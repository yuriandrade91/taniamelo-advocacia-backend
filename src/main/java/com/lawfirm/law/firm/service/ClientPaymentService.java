package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.audit.IntencaoDeAuditoria;
import com.lawfirm.law.firm.dto.ClientPaymentRequestDTO;
import com.lawfirm.law.firm.dto.ClientPaymentResponseDTO;
import com.lawfirm.law.firm.dto.ClientPaymentUpdateRequestDTO;
import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientPayment;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.ClientPaymentRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.PaymentQueryRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.util.FusoDoEscritorio;
import com.lawfirm.law.firm.util.PageRequests;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que um cliente tem a receber - atrasados, benefício, parcela de acordo. Cada linha é uma
 * parcela individual (não um contrato inteiro) - ver comentário em {@link ClientPayment}.
 *
 * <p>Não confundir com o faturamento do escritório, que é {@code OfficeRevenueService}: este
 * dinheiro é do cliente e não entra na Carteira.
 *
 * <p>"Atrasado" nunca é persistido: é calculado na leitura a partir de status=PENDENTE + vencimento
 * no passado.
 */
@Service
public class ClientPaymentService {

    private final ClientPaymentRepository repository;
    private final ClientRepository clientRepository;
    private final PaymentQueryRepository consultaRepository;

    public ClientPaymentService(
            ClientPaymentRepository repository,
            ClientRepository clientRepository,
            PaymentQueryRepository consultaRepository) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.consultaRepository = consultaRepository;
    }

    @Transactional
    public ClientPaymentResponseDTO create(UUID clientId, ClientPaymentRequestDTO dto) {
        Client client = findClientOrThrow(clientId);

        ClientPayment entity = new ClientPayment();
        entity.setClient(client);
        entity.setDescription(dto.getDescription());
        entity.setAmount(dto.getAmount());
        entity.setInstallmentNumber(dto.getInstallmentNumber());
        entity.setInstallmentTotal(dto.getInstallmentTotal());
        entity.setDueDate(dto.getDueDate());
        entity.setPaymentMethod(parsePaymentMethod(dto.getPaymentMethod()));
        entity.setNotes(dto.getNotes());
        entity.setStatus(PaymentStatus.PENDENTE);
        entity.setCreatedBy(CurrentUser.id());

        return toDTO(repository.save(entity));
    }

    /**
     * A aba financeira da ficha: as mesmas parcelas de {@code GET /payments}, recortadas neste
     * cliente. Os filtros vêm do mesmo {@link FiltrosDePagamento} que a lista geral usa — a aba não
     * responde diferente da lista sobre a mesma parcela, e um filtro novo nasce nas duas.
     *
     * <p>O 404 de cliente inexistente continua vindo antes da consulta: sem ele, um id errado
     * devolveria página vazia com 200, que é indistinguível de "cliente sem parcelas".
     */
    public Page<ClientPaymentResponseDTO> list(UUID clientId, PaymentSearchParams params) {
        findClientOrThrow(clientId);
        params.setClientId(exigirMesmoCliente(clientId, params.getClientId()));

        var pageable =
                PageRequests.of(
                        params.getPageNumber(), params.getPageSize(), FiltrosDePagamento.ORDEM);
        return consultaRepository.findAll(FiltrosDePagamento.de(params), pageable).map(this::toDTO);
    }

    /**
     * O cliente da URL manda. Mandar {@code ?clientId=} de outro cliente é 400, e não silêncio: as
     * duas leituras possíveis ("a URL ganha" ou "a query ganha") devolvem listas diferentes, e quem
     * escreveu a chamada não vai saber qual recebeu.
     */
    private static UUID exigirMesmoCliente(UUID daUrl, UUID daQuery) {
        if (daQuery != null && !daQuery.equals(daUrl)) {
            throw new ValidationException(
                    "clientId",
                    ValidationErrorCode.CONFLICTING_PARAMETERS,
                    "O cliente da URL e o do parâmetro clientId são diferentes. "
                            + "Para listar outro cliente, use GET /api/v1/payments?clientId=.");
        }
        return daUrl;
    }

    public ClientPaymentResponseDTO get(UUID clientId, UUID paymentId) {
        return toDTO(findPaymentOrThrow(clientId, paymentId));
    }

    @Transactional
    public ClientPaymentResponseDTO update(
            UUID clientId, UUID paymentId, ClientPaymentUpdateRequestDTO dto) {
        ClientPayment entity = findPaymentOrThrow(clientId, paymentId);

        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getAmount() != null) {
            entity.setAmount(dto.getAmount());
        }
        if (dto.getInstallmentNumber() != null) {
            entity.setInstallmentNumber(dto.getInstallmentNumber());
        }
        if (dto.getInstallmentTotal() != null) {
            entity.setInstallmentTotal(dto.getInstallmentTotal());
        }
        if (dto.getDueDate() != null) {
            entity.setDueDate(dto.getDueDate());
        }
        if (dto.getPaymentMethod() != null) {
            entity.setPaymentMethod(parsePaymentMethod(dto.getPaymentMethod()));
        }
        if (dto.getNotes() != null) {
            entity.setNotes(dto.getNotes());
        }

        if (dto.getStatus() != null) {
            PaymentStatus newStatus = parsePaymentStatus(dto.getStatus());
            entity.setStatus(newStatus);
            if (newStatus == PaymentStatus.PAGO) {
                // Marcar como pago sem informar a data assume "hoje" - o caso comum
                // (usuário confirmando o pagamento no momento em que ele chegou).
                entity.setPaidDate(
                        dto.getPaidDate() != null ? dto.getPaidDate() : FusoDoEscritorio.hoje());
            } else {
                // PENDENTE/CANCELADO com paidDate preenchida é um estado contraditório
                // (não dá para estar "pago" e "pendente" ao mesmo tempo) - limpa, a
                // menos que o próprio patch tenha mandado uma data (correção manual).
                entity.setPaidDate(dto.getPaidDate());
            }
        } else if (dto.getPaidDate() != null) {
            entity.setPaidDate(dto.getPaidDate());
        }

        entity.setUpdatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID clientId, UUID paymentId) {
        ClientPayment entity = findPaymentOrThrow(clientId, paymentId);
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(CurrentUser.id());
        // Exclusão lógica: sem declarar, a trilha registraria UPDATE.
        IntencaoDeAuditoria.declarando(AuditAction.DELETE, () -> repository.saveAndFlush(entity));
    }

    // ── Private helpers ──

    private Client findClientOrThrow(UUID clientId) {
        return ClientLookup.orThrow(clientRepository, clientId);
    }

    private ClientPayment findPaymentOrThrow(UUID clientId, UUID paymentId) {
        findClientOrThrow(clientId);
        return repository
                .findByIdAndClient_IdAndDeletedAtIsNull(paymentId, clientId)
                .orElseThrow(() -> NotFoundException.of("Parcela", paymentId));
    }

    private PaymentStatus parsePaymentStatus(String raw) {
        try {
            return PaymentStatus.fromLabel(raw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "status",
                    ValidationErrorCode.INVALID_ENUM_VALUE,
                    "Status de pagamento inválido: " + raw);
        }
    }

    private PaymentMethod parsePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PaymentMethod.fromLabel(raw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "paymentMethod",
                    ValidationErrorCode.INVALID_ENUM_VALUE,
                    "Forma de pagamento inválida: " + raw);
        }
    }

    private ClientPaymentResponseDTO toDTO(ClientPayment entity) {
        ClientPaymentResponseDTO dto = new ClientPaymentResponseDTO();
        dto.setId(entity.getId());
        dto.setDescription(entity.getDescription());
        dto.setAmount(entity.getAmount());
        dto.setInstallmentNumber(entity.getInstallmentNumber());
        dto.setInstallmentTotal(entity.getInstallmentTotal());
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setPaymentMethod(
                entity.getPaymentMethod() != null ? entity.getPaymentMethod().getLabel() : null);
        dto.setNotes(entity.getNotes());
        dto.setOverdue(RegrasDeVencimento.estaAtrasado(entity.getStatus(), entity.getDueDate()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
