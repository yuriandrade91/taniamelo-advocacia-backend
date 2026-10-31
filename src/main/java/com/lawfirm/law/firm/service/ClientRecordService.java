package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.audit.AuditLog;
import com.lawfirm.law.firm.audit.AuditLogRepository;
import com.lawfirm.law.firm.dto.AppointmentResponseDTO;
import com.lawfirm.law.firm.dto.AppointmentSearchParams;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.dto.ClientRequestStageHistoryDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.tenant.TenantContext;
import com.lawfirm.law.firm.util.PageRequests;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta a ficha completa do cliente para impressão.
 *
 * <p>Lê pelos mesmos services das abas, e não por consulta própria, para a ficha nunca discordar da
 * tela: exclusão lógica, ordem e rótulos são os que o usuário já vê.
 *
 * <p>A senha do INSS não entra, para papel nenhum. Um PDF é copiado, encaminhado e impresso sem
 * deixar rastro, e a leitura da senha só faz sentido na rota dedicada, onde cada acesso é um ato
 * registrado. As parcelas só saem para ADMIN, o mesmo corte de {@code ClientPaymentController}: sem
 * isso, baixar o PDF seria o caminho de STAFF e SUPPORT até o financeiro.
 */
@Service
public class ClientRecordService {

    private final ClientService clientService;
    private final ClientAddressService addressService;
    private final ClientDisabilityPeriodService disabilityPeriodService;
    private final ClientInterviewService interviewService;
    private final ClientFileService fileService;
    private final ClientPaymentService paymentService;
    private final AppointmentService appointmentService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AuditLogRepository auditRepository;

    @SuppressWarnings("java:S107") // uma dependência por seção da ficha
    public ClientRecordService(
            ClientService clientService,
            ClientAddressService addressService,
            ClientDisabilityPeriodService disabilityPeriodService,
            ClientInterviewService interviewService,
            ClientFileService fileService,
            ClientPaymentService paymentService,
            AppointmentService appointmentService,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            AuditLogRepository auditRepository) {
        this.clientService = clientService;
        this.addressService = addressService;
        this.disabilityPeriodService = disabilityPeriodService;
        this.interviewService = interviewService;
        this.fileService = fileService;
        this.paymentService = paymentService;
        this.appointmentService = appointmentService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Gera o PDF e registra quem baixou. Abrir uma ficha é rotina e não vale registro; levar a
     * ficha inteira para fora do sistema, com CPF e entrevistas, vale.
     *
     * <p>Transação de escrita, mesmo sendo quase toda leitura: em {@code readOnly} o Hibernate não
     * faz flush, e o registro de auditoria nunca chegaria ao banco.
     */
    @Transactional
    public ClientRecordPdf export(UUID clientId) {
        ClientDetailsDTO client =
                clientService
                        .findById(clientId)
                        .orElseThrow(() -> NotFoundException.of("Cliente", clientId));

        boolean paymentsVisible = CurrentUser.hasRole("ADMIN");
        ClientRecord clientRecord = collect(client, paymentsVisible);
        byte[] content = ClientRecordPdfRenderer.render(clientRecord);

        auditRepository.save(
                new AuditLog(
                        "Client",
                        clientId,
                        AuditAction.READ,
                        CurrentUser.id(),
                        CurrentUser.actingSupportId(),
                        "Client record PDF exported"));
        return new ClientRecordPdf("Ficha - " + client.getFullName() + ".pdf", content);
    }

    private ClientRecord collect(ClientDetailsDTO client, boolean paymentsVisible) {
        UUID clientId = client.getClientId();
        List<ClientInterviewResponseDTO> interviews =
                allPages(page -> interviewService.list(clientId, page, PageRequests.MAX_PAGE_SIZE));
        List<ClientSituationHistoryDTO> situationHistory =
                allPages(
                        page ->
                                clientService.historyByClientId(
                                        clientId, page, PageRequests.MAX_PAGE_SIZE));
        List<ClientRequestStageHistoryDTO> requestStageHistory =
                allPages(
                        page ->
                                clientService.requestStageHistoryByClientId(
                                        clientId, page, PageRequests.MAX_PAGE_SIZE));

        Set<UUID> authors = new HashSet<>();
        authors.add(client.getResponsibleUserId());
        interviews.forEach(interview -> authors.add(interview.getCreatedBy()));
        situationHistory.forEach(entry -> authors.add(entry.getChangedByUserId()));
        requestStageHistory.forEach(entry -> authors.add(entry.getChangedByUserId()));

        return new ClientRecord(
                officeName(),
                Instant.now(),
                client,
                allPages(page -> addressService.list(clientId, page, PageRequests.MAX_PAGE_SIZE)),
                disabilityPeriodService.list(clientId),
                interviews,
                allPages(
                        page ->
                                fileService.listDocuments(
                                        clientId, null, page, PageRequests.MAX_PAGE_SIZE)),
                allPages(
                        page ->
                                fileService.listSimulations(
                                        clientId, page, PageRequests.MAX_PAGE_SIZE)),
                situationHistory,
                requestStageHistory,
                appointments(clientId),
                paymentsVisible,
                paymentsVisible ? payments(clientId) : List.of(),
                userNames(authors));
    }

    private List<AppointmentResponseDTO> appointments(UUID clientId) {
        return allPages(
                page -> {
                    AppointmentSearchParams params = new AppointmentSearchParams();
                    params.setClientId(clientId);
                    params.setPageNumber(page);
                    params.setPageSize(PageRequests.MAX_PAGE_SIZE);
                    return appointmentService.list(params);
                });
    }

    private List<PaymentListItemDTO> payments(UUID clientId) {
        return allPages(
                page -> {
                    PaymentSearchParams params = new PaymentSearchParams();
                    params.setPageNumber(page);
                    params.setPageSize(PageRequests.MAX_PAGE_SIZE);
                    return paymentService.list(clientId, params);
                });
    }

    private String officeName() {
        String schema = TenantContext.get();
        return tenantRepository
                .findBySchemaName(schema)
                .map(Tenant::getRazaoSocial)
                .orElseThrow(() -> NotFoundException.of("Tenant", schema));
    }

    private Map<UUID, String> userNames(Set<UUID> ids) {
        ids.removeIf(Objects::isNull);
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    /** As listagens têm teto de página; a ficha impressa não pode sair cortada por causa dele. */
    private static <T> List<T> allPages(IntFunction<Page<T>> fetchPage) {
        List<T> items = new ArrayList<>();
        Page<T> page;
        int pageNumber = 1;
        do {
            page = fetchPage.apply(pageNumber++);
            items.addAll(page.getContent());
        } while (page.hasNext());
        return items;
    }
}
