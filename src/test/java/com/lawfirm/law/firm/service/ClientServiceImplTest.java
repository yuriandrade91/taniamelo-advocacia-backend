package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.audit.AuditLog;
import com.lawfirm.law.firm.audit.AuditLogRepository;
import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientInssPasswordDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientMapper;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientSituationHistory;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.ClientSituationHistoryRepository;
import com.lawfirm.law.firm.security.UserPrincipal;
import com.lawfirm.law.firm.support.TestFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClientServiceImpl: regras do cadastro de clientes")
class ClientServiceImplTest {

    @Mock private ClientRepository repository;
    @Mock private ClientMapper mapper;
    @Mock private ClientSituationHistoryRepository historyRepository;
    @Mock private AuditLogRepository auditRepository;

    private ClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImpl(repository, mapper, historyRepository, auditRepository);
        when(repository.save(any(Client.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(TestFixtures.user());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private ClientCreateRequestDTO createDto() {
        ClientCreateRequestDTO dto = new ClientCreateRequestDTO();
        dto.setFullName("Maria da Silva");
        dto.setBirthDate(LocalDate.of(1980, 5, 20));
        dto.setCpf("529.982.247-25");
        dto.setMotherName("Joana");
        dto.setMobilePhone("+5511999999999");
        dto.setInssPassword("senha");
        dto.setGender(Gender.FEMININO);
        dto.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        dto.setSituation(Situation.FORMULARIO_PREENCHIDO);
        return dto;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("deriva o total de meses, marca o autor e registra o histórico inicial")
        void createsAndRecordsInitialHistory() {
            ClientCreateRequestDTO dto = createDto();
            Client entity = TestFixtures.client();
            entity.setContributionYears(3);
            entity.setContributionMonths(10);
            entity.setContributionDays(22);
            when(mapper.toEntity(dto)).thenReturn(entity);
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());
            authenticate();

            assertNotNull(service.create(dto));

            assertEquals(47, entity.getContributionInMonths());
            assertEquals(TestFixtures.USER_ID, entity.getCreatedBy());

            ArgumentCaptor<ClientSituationHistory> history =
                    ArgumentCaptor.forClass(ClientSituationHistory.class);
            verify(historyRepository).save(history.capture());
            assertNull(
                    history.getValue().getPreviousSituation(),
                    "primeira situação não tem anterior");
            assertEquals("Formulário preenchido", history.getValue().getNewSituation());
            assertEquals(TestFixtures.USER_ID, history.getValue().getChangedBy());
            assertNotNull(history.getValue().getChangedAt());
        }

        @Test
        @DisplayName("sem tempo de contribuição, o total em meses fica nulo")
        void nullContributionTimeLeavesMonthsNull() {
            // Os três nulos significam "não informado", que é diferente de informar zero - era
            // essa distinção que o parser de texto apagava ao devolver 0 para "nao informado".
            Client entity = TestFixtures.client();
            entity.setContributionYears(null);
            entity.setContributionMonths(null);
            entity.setContributionDays(null);
            when(mapper.toEntity(any())).thenReturn(entity);
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.create(createDto());

            assertNull(entity.getContributionInMonths());
        }

        /* ────────────────────────────────────────────────────────────────
         * Identidade única.
         *
         * A validação passou a acontecer sobre a ENTIDADE, depois de mapear e
         * normalizar - antes olhava o DTO cru, e era por isso que
         * "39053344705" e "390.533.447-05" passavam os dois: para a checagem
         * eram textos diferentes.
         * ──────────────────────────────────────────────────────────────── */

        @Test
        @DisplayName("CPF já cadastrado é bloqueado antes de gravar")
        void rejectsDuplicateCpf() {
            Client entity = TestFixtures.client();
            entity.setCpf("529.982.247-25");
            when(mapper.toEntity(any())).thenReturn(entity);
            when(repository.existsByCpf("52998224725")).thenReturn(true);

            ValidationException ex =
                    assertThrows(ValidationException.class, () -> service.create(createDto()));
            assertEquals("cpf", ex.getField());
            assertEquals(ValidationErrorCode.DUPLICATE_VALUE, ex.getValidationErrorCode());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("o CPF é comparado por DÍGITOS: com e sem máscara são o mesmo cadastro")
        void cpfMascaradoEhOMesmoCadastro() {
            // O defeito que originou tudo isto: os dois passavam, e viravam duas
            // fichas da mesma pessoa, com históricos separados.
            Client entity = TestFixtures.client();
            entity.setCpf("390.533.447-05");
            when(mapper.toEntity(any())).thenReturn(entity);
            when(repository.existsByCpf("39053344705")).thenReturn(true);

            assertEquals(
                    "cpf",
                    assertThrows(ValidationException.class, () -> service.create(createDto()))
                            .getField());
        }

        @Test
        @DisplayName("o que é gravado já vai normalizado")
        void gravaNormalizado() {
            Client entity = TestFixtures.client();
            entity.setCpf("390.533.447-05");
            entity.setRg("mg-12.345.678");
            entity.setCtps("12.345/0001");
            entity.setNitPis("123.45678.90-1");
            entity.setBeneficiaryNumber("123.456.789-0");
            when(mapper.toEntity(any())).thenReturn(entity);
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.create(createDto());

            assertEquals("39053344705", entity.getCpf());
            assertEquals("MG12345678", entity.getRg());
            assertEquals("123450001", entity.getCtps());
            assertEquals("123456789 01".replace(" ", ""), entity.getNitPis());
            assertEquals("1234567890", entity.getBeneficiaryNumber());
        }

        @Test
        @DisplayName("RG, CTPS, NIT/PIS e nº do benefício duplicados também são bloqueados")
        void rejectsOutrosDocumentos() {
            record Caso(
                    String campo, java.util.function.Consumer<Client> preenche, Runnable stub) {}

            Client entity = TestFixtures.client();
            List<Caso> casos =
                    List.of(
                            new Caso(
                                    "rg",
                                    c -> c.setRg("MG12345678"),
                                    () ->
                                            when(repository.existsByRg("MG12345678"))
                                                    .thenReturn(true)),
                            new Caso(
                                    "ctps",
                                    c -> c.setCtps("1234567"),
                                    () ->
                                            when(repository.existsByCtps("1234567"))
                                                    .thenReturn(true)),
                            new Caso(
                                    "nitPis",
                                    c -> c.setNitPis("12345678901"),
                                    () ->
                                            when(repository.existsByNitPis("12345678901"))
                                                    .thenReturn(true)),
                            new Caso(
                                    "beneficiaryNumber",
                                    c -> c.setBeneficiaryNumber("1234567890"),
                                    () ->
                                            when(repository.existsByBeneficiaryNumber("1234567890"))
                                                    .thenReturn(true)));

            for (Caso caso : casos) {
                reset(repository, mapper);
                Client alvo = TestFixtures.client();
                alvo.setRg(null);
                alvo.setCtps(null);
                alvo.setNitPis(null);
                alvo.setBeneficiaryNumber(null);
                caso.preenche().accept(alvo);
                when(mapper.toEntity(any())).thenReturn(alvo);
                caso.stub().run();

                assertEquals(
                        caso.campo(),
                        assertThrows(ValidationException.class, () -> service.create(createDto()))
                                .getField(),
                        "esperava bloqueio em " + caso.campo());
            }
            assertNotNull(entity);
        }

        @Test
        @DisplayName("contato NÃO é único: mãe e filho podem ter o mesmo telefone e e-mail")
        void contatoNaoEhUnico() {
            // Decisão de produto: contato se compartilha. Tornar único recusaria o
            // segundo cadastro de uma família.
            Client entity = TestFixtures.client();
            when(mapper.toEntity(any())).thenReturn(entity);
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.create(createDto());

            verify(repository).save(entity);
        }

        @Test
        @DisplayName("campos únicos em branco não são checados contra duplicidade")
        void blankUniqueFieldsAreSkipped() {
            Client entity = TestFixtures.client();
            entity.setNitPis("   ");
            entity.setBeneficiaryNumber(null);
            entity.setRg(null);
            entity.setCtps(null);
            when(mapper.toEntity(any())).thenReturn(entity);
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.create(createDto());

            verify(repository, never()).existsByNitPis(anyString());
            verify(repository, never()).existsByBeneficiaryNumber(anyString());
            verify(repository, never()).existsByRg(anyString());
            verify(repository, never()).existsByCtps(anyString());
        }
    }

    @Nested
    @DisplayName("listSummary")
    class ListSummary {

        @SuppressWarnings("unchecked")
        private Pageable capturePageable() {
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(repository).findAll(any(Specification.class), pageable.capture());
            return pageable.getValue();
        }

        @SuppressWarnings("unchecked")
        @BeforeEach
        void stubFindAll() {
            when(repository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(TestFixtures.client())));
            when(mapper.toListDTO(any(Client.class))).thenReturn(new ClientListResponseDTO());
        }

        @Test
        @DisplayName("página 1-based vira índice 0-based e ordena por atividade recente")
        void translatesOneBasedPageAndSortsByUpdatedAt() {
            Page<ClientListResponseDTO> page =
                    service.listSummary(1, 25, null, null, null, null, null, null);

            assertEquals(1, page.getContent().size());
            Pageable pageable = capturePageable();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(25, pageable.getPageSize());
            assertEquals(
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"),
                    pageable.getSort());
        }

        @Test
        @DisplayName("página 0 ou negativa é normalizada para a primeira")
        void nonPositivePageFallsBackToFirst() {
            service.listSummary(0, 10, null, null, null, null, null, null);
            assertEquals(0, capturePageable().getPageNumber());
        }

        @Test
        @DisplayName("tamanho de página inválido cai no default de 10")
        void invalidPageSizeFallsBackToTen() {
            service.listSummary(1, 0, null, null, null, null, null, null);
            assertEquals(10, capturePageable().getPageSize());
        }

        @Test
        @DisplayName("com todos os filtros preenchidos a consulta é montada sem erro")
        void combinesEveryFilter() {
            Page<ClientListResponseDTO> page =
                    service.listSummary(
                            2,
                            5,
                            "maria",
                            List.of(BenefitType.APOSENTADORIA_RURAL),
                            List.of(Situation.ANALISE_DOCUMENTAL),
                            List.of(ClientType.POTENCIAL),
                            Instant.parse("2026-01-01T00:00:00Z"),
                            Instant.parse("2026-12-31T00:00:00Z"));

            assertNotNull(page);
            assertEquals(1, capturePageable().getPageNumber());
        }
    }

    @Nested
    @DisplayName("findById / delete")
    class FindAndDelete {

        @Test
        @DisplayName("findById devolve o DTO mapeado")
        void findByIdMapsToDto() {
            ClientDetailsDTO dto = new ClientDetailsDTO();
            when(repository.findById(TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(TestFixtures.client()));
            when(mapper.toDTO(any(Client.class))).thenReturn(dto);

            assertEquals(Optional.of(dto), service.findById(TestFixtures.CLIENT_ID));
        }

        @Test
        @DisplayName("findById de id inexistente devolve Optional vazio")
        void findByIdOfMissingClientIsEmpty() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            assertTrue(service.findById(UUID.randomUUID()).isEmpty());
        }

        @Test
        @DisplayName("delete marca deletedAt em vez de apagar a linha")
        void deleteIsLogicalNotPhysical() {
            Client existing = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(existing));

            service.delete(TestFixtures.CLIENT_ID);

            // O delete físico levaria junto endereços, entrevistas, arquivos, pagamentos e o
            // histórico (FKs ON DELETE CASCADE). Nada disso pode acontecer aqui.
            verify(repository, never()).deleteById(any());
            verify(repository).saveAndFlush(existing);
            assertNotNull(existing.getDeletedAt());
        }

        @Test
        @DisplayName("delete de id inexistente estoura 404 sem gravar nada")
        void deleteOfMissingClientThrows() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            NotFoundException ex = assertThrows(NotFoundException.class, () -> service.delete(id));
            assertTrue(ex.getMessage().contains(id.toString()));
            verify(repository, never()).save(any());
            verify(repository, never()).deleteById(any());
        }

        @Test
        @DisplayName("restore limpa deletedAt de um cliente excluído")
        void restoreClearsDeletedAt() {
            Client deleted = TestFixtures.client();
            deleted.setDeletedAt(Instant.parse("2026-01-10T12:00:00Z"));
            when(repository.findByIdIncludingDeleted(TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(deleted));

            service.restore(TestFixtures.CLIENT_ID);

            assertNull(deleted.getDeletedAt());
            verify(repository).saveAndFlush(deleted);
        }

        @Test
        @DisplayName("restore de cliente ativo não grava nada (idempotente)")
        void restoreOfActiveClientIsNoOp() {
            Client active = TestFixtures.client();
            when(repository.findByIdIncludingDeleted(TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(active));

            service.restore(TestFixtures.CLIENT_ID);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("restore de id inexistente estoura 404")
        void restoreOfMissingClientThrows() {
            UUID id = UUID.randomUUID();
            when(repository.findByIdIncludingDeleted(id)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.restore(id));
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("senha do INSS")
    class SenhaDoInss {

        @Test
        @DisplayName("revela a senha E registra quem leu na auditoria")
        void revealsAndAudits() {
            authenticate();
            Client client = TestFixtures.client();
            client.setInssPassword("senha-do-portal");
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            ClientInssPasswordDTO dto = service.revealInssPassword(TestFixtures.CLIENT_ID);

            assertEquals("senha-do-portal", dto.inssPassword());

            // O registro é o ponto do endpoint, não um detalhe: devolver a senha sem rastro
            // tornaria a criptografia em repouso meia medida — protegeria contra quem lê o
            // banco e não contra quem tem login.
            ArgumentCaptor<AuditLog> auditoria = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditRepository).save(auditoria.capture());
            AuditLog registro = auditoria.getValue();
            assertEquals(AuditAction.READ, registro.getAction());
            assertEquals("Client", registro.getEntityName());
            assertEquals(TestFixtures.CLIENT_ID, registro.getEntityId());
            assertEquals(TestFixtures.USER_ID, registro.getPerformedBy());
        }

        @Test
        @DisplayName("cliente inexistente estoura 404 sem registrar leitura")
        void missingClientThrowsAndDoesNotAudit() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.revealInssPassword(id));
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("editar a aba profissional sem enviar a senha NÃO a apaga")
        void updateWithoutPasswordKeepsIt() {
            // inss_password é NOT NULL, e desde que a senha saiu do GET quem edita a aba não a
            // tem em mãos. Apagá-la aqui quebraria o acesso do escritório ao portal do INSS.
            Client client = TestFixtures.client();
            client.setInssPassword("senha-antiga");
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            ClientProfessionalDataRequestDTO dto = new ClientProfessionalDataRequestDTO();
            dto.setProfession("Pedreiro");
            service.updateProfessionalData(TestFixtures.CLIENT_ID, dto);

            assertEquals("senha-antiga", client.getInssPassword());
        }

        @Test
        @DisplayName("senha em branco também é tratada como ausente")
        void blankPasswordIsTreatedAsAbsent() {
            Client client = TestFixtures.client();
            client.setInssPassword("senha-antiga");
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            ClientProfessionalDataRequestDTO dto = new ClientProfessionalDataRequestDTO();
            dto.setInssPassword("   ");
            service.updateProfessionalData(TestFixtures.CLIENT_ID, dto);

            assertEquals("senha-antiga", client.getInssPassword());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("mudança de situação gera registro no histórico com a situação anterior")
        void situationChangeRecordsHistory() {
            Client existing = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(existing));
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());
            org.mockito.Mockito.doAnswer(
                            invocation -> {
                                existing.setSituation(Situation.ANALISE_DOCUMENTAL);
                                return null;
                            })
                    .when(mapper)
                    .updateEntityFromDto(any(), any());
            authenticate();

            service.update(TestFixtures.CLIENT_ID, new ClientUpdateRequestDTO());

            ArgumentCaptor<ClientSituationHistory> history =
                    ArgumentCaptor.forClass(ClientSituationHistory.class);
            verify(historyRepository).save(history.capture());
            assertEquals("Formulário preenchido", history.getValue().getPreviousSituation());
            assertEquals("Análise documental", history.getValue().getNewSituation());
            assertEquals(TestFixtures.USER_ID, existing.getUpdatedBy());
        }

        @Test
        @DisplayName("sem mudança de situação, nenhum histórico é gerado")
        void noHistoryWhenSituationIsUnchanged() {
            Client existing = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(existing));
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.update(TestFixtures.CLIENT_ID, new ClientUpdateRequestDTO());

            verify(historyRepository, never()).save(any());
        }

        @Test
        @DisplayName("o total de meses é recalculado a partir dos números atualizados")
        void recomputesContributionMonths() {
            Client existing = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(existing));
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());
            org.mockito.Mockito.doAnswer(
                            invocation -> {
                                existing.setContributionYears(2);
                                existing.setContributionMonths(0);
                                existing.setContributionDays(0);
                                return null;
                            })
                    .when(mapper)
                    .updateEntityFromDto(any(), any());

            service.update(TestFixtures.CLIENT_ID, new ClientUpdateRequestDTO());

            assertEquals(24, existing.getContributionInMonths());
        }

        @Test
        @DisplayName("cliente inexistente estoura 404")
        void updateOfMissingClientThrows() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            ClientUpdateRequestDTO dto = new ClientUpdateRequestDTO();
            UUID id = UUID.randomUUID();

            assertThrows(NotFoundException.class, () -> service.update(id, dto));
        }
    }

    @Nested
    @DisplayName("patch")
    class Patch {

        private Client existing;

        @BeforeEach
        void stubClient() {
            existing = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(existing));
        }

        @Test
        @DisplayName("patch vazio não muda nada e não grava")
        void emptyPatchChangesNothing() {
            ClientPatchOutcome outcome =
                    service.patch(TestFixtures.CLIENT_ID, new ClientPatchRequestDTO());

            assertFalse(outcome.situationChanged());
            assertFalse(outcome.benefitChanged());
            assertFalse(outcome.clientTypeChanged());
            assertFalse(outcome.notBillableChanged());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("mudança de situação grava histórico; os demais campos não")
        void situationChangeAlsoRecordsHistory() {
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            patch.setSituation("Análise documental");
            patch.setBenefit("Aposentadoria rural");
            patch.setClientType("Verificado");
            patch.setNotBillable(true);
            authenticate();

            ClientPatchOutcome outcome = service.patch(TestFixtures.CLIENT_ID, patch);

            assertTrue(outcome.situationChanged());
            assertTrue(outcome.benefitChanged());
            assertTrue(outcome.clientTypeChanged());
            assertTrue(outcome.notBillableChanged());
            assertEquals(Situation.ANALISE_DOCUMENTAL, existing.getSituation());
            assertEquals(BenefitType.APOSENTADORIA_RURAL, existing.getBenefit());
            assertEquals(ClientType.VERIFICADO, existing.getClientType());
            assertTrue(existing.getNotBillable());
            verify(historyRepository, times(1)).save(any());
            assertEquals(TestFixtures.USER_ID, existing.getUpdatedBy());
        }

        @Test
        @DisplayName("enviar o mesmo valor que já está gravado não conta como mudança")
        void samValuesAreNotChanges() {
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            patch.setSituation("Formulário preenchido");
            patch.setBenefit("Aposentadoria por idade");
            patch.setClientType("Potencial");
            patch.setNotBillable(false);

            ClientPatchOutcome outcome = service.patch(TestFixtures.CLIENT_ID, patch);

            assertFalse(outcome.situationChanged());
            assertFalse(outcome.benefitChanged());
            assertFalse(outcome.clientTypeChanged());
            assertFalse(outcome.notBillableChanged());
            verify(repository, never()).save(any());
            verify(historyRepository, never()).save(any());
        }

        @Test
        @DisplayName("aceita o nome da constante além do label")
        void acceptsEnumConstantNames() {
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            patch.setSituation("BENEFICIO_CONCLUIDO");

            assertTrue(service.patch(TestFixtures.CLIENT_ID, patch).situationChanged());
            assertEquals(Situation.BENEFICIO_CONCLUIDO, existing.getSituation());
        }

        @Test
        @DisplayName("situação inválida vira 400 com o valor recebido na mensagem")
        void invalidSituationThrows() {
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            patch.setSituation("nao-existe");

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.patch(TestFixtures.CLIENT_ID, patch));
            assertEquals("situation", ex.getField());
            assertEquals(ValidationErrorCode.INVALID_SITUATION, ex.getValidationErrorCode());
            assertTrue(ex.getMessage().contains("nao-existe"));
        }

        @Test
        @DisplayName("benefício inválido vira 400")
        void invalidBenefitThrows() {
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            patch.setBenefit("nao-existe");

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.patch(TestFixtures.CLIENT_ID, patch));
            assertEquals("benefit", ex.getField());
            assertEquals(ValidationErrorCode.INVALID_BENEFIT, ex.getValidationErrorCode());
        }

        @Test
        @DisplayName("tipo de cliente inválido vira 400")
        void invalidClientTypeThrows() {
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            patch.setClientType("nao-existe");

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.patch(TestFixtures.CLIENT_ID, patch));
            assertEquals("clientType", ex.getField());
            assertEquals(ValidationErrorCode.INVALID_CLIENT_TYPE, ex.getValidationErrorCode());
        }

        @Test
        @DisplayName("string vazia nos enums é tratada como valor inválido")
        void blankEnumValueThrows() {
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            patch.setSituation("   ");

            assertThrows(
                    ValidationException.class, () -> service.patch(TestFixtures.CLIENT_ID, patch));
        }

        @Test
        @DisplayName("cliente inexistente estoura 404")
        void patchOfMissingClientThrows() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
            UUID id = UUID.randomUUID();

            assertThrows(NotFoundException.class, () -> service.patch(id, patch));
        }
    }

    @Nested
    @DisplayName("histórico de situação")
    class History {

        @Test
        @DisplayName("valida o cliente e ordena do mais recente para o mais antigo")
        void listsHistoryNewestFirst() {
            when(repository.findById(TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(TestFixtures.client()));
            when(historyRepository.findByClient_Id(eq(TestFixtures.CLIENT_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(new ClientSituationHistory())));
            when(mapper.toHistoryDTO(any())).thenReturn(new ClientSituationHistoryDTO());

            Page<ClientSituationHistoryDTO> page =
                    service.historyByClientId(TestFixtures.CLIENT_ID, 1, 10);

            assertEquals(1, page.getContent().size());
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(historyRepository).findByClient_Id(any(), pageable.capture());
            assertEquals(
                    org.springframework.data.domain.Sort.by("changedAt").descending(),
                    pageable.getValue().getSort());
        }

        @Test
        @DisplayName("página e tamanho inválidos são normalizados")
        void normalizesPaging() {
            when(repository.findById(TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(TestFixtures.client()));
            when(historyRepository.findByClient_Id(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            service.historyByClientId(TestFixtures.CLIENT_ID, -5, -1);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(historyRepository).findByClient_Id(any(), pageable.capture());
            assertEquals(0, pageable.getValue().getPageNumber());
            assertEquals(10, pageable.getValue().getPageSize());
        }

        @Test
        @DisplayName("cliente inexistente estoura 404 antes de consultar o histórico")
        void historyOfMissingClientThrows() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            UUID id = UUID.randomUUID();

            assertThrows(NotFoundException.class, () -> service.historyByClientId(id, 1, 10));
            verify(historyRepository, never()).findByClient_Id(any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("aba Dados pessoais")
    class PersonalData {

        @Test
        @DisplayName("get calcula a idade a partir da data de nascimento")
        void getComputesAge() {
            Client client = TestFixtures.client();
            client.setBirthDate(LocalDate.now(ZoneOffset.UTC).minusYears(45).minusDays(1));
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            ClientPersonalDataResponseDTO dto = service.getPersonalData(TestFixtures.CLIENT_ID);

            assertEquals(45, dto.getAge());
            assertEquals("Maria da Silva", dto.getFullName());
            assertEquals("529.982.247-25", dto.getCpf());
        }

        @Test
        @DisplayName("sem data de nascimento a idade fica nula")
        void nullBirthDateMeansNullAge() {
            Client client = TestFixtures.client();
            client.setBirthDate(null);
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            assertNull(service.getPersonalData(TestFixtures.CLIENT_ID).getAge());
        }

        @Test
        @DisplayName("update substitui o subconjunto pessoal e marca o autor")
        void updateReplacesPersonalFields() {
            Client client = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));
            authenticate();

            ClientPersonalDataRequestDTO dto = new ClientPersonalDataRequestDTO();
            dto.setFullName("Maria Souza");
            dto.setBirthDate(LocalDate.of(1975, 3, 1));
            dto.setCpf("111.444.777-35");
            dto.setRg("MG-1");
            dto.setRgIssuer("SSP");
            dto.setRgIssueDate(LocalDate.of(2000, 1, 1));
            dto.setMotherName("Ana");
            dto.setGender(Gender.FEMININO);
            dto.setMaritalStatus(MaritalStatus.DIVORCIADO);
            dto.setNationality("Brasileira");
            dto.setMobilePhone("+5511911111111");
            dto.setIsWhatsapp(true);
            dto.setReferencePhone("+5511922222222");
            dto.setReferenceResponsible("João");
            dto.setEmail("maria@x.com");
            dto.setHasDisability(true);

            ClientPersonalDataResponseDTO response =
                    service.updatePersonalData(TestFixtures.CLIENT_ID, dto);

            assertEquals("Maria Souza", response.getFullName());
            // A aba escreve direto na entidade, mas passa pela mesma normalização do
            // cadastro: o CPF é gravado em dígitos, venha mascarado ou não.
            assertEquals("11144477735", client.getCpf());
            assertEquals(MaritalStatus.DIVORCIADO, client.getMaritalStatus());
            assertEquals("Brasileira", client.getNationality());
            assertTrue(client.getIsWhatsapp());
            assertTrue(client.getHasDisability());
            assertEquals(TestFixtures.USER_ID, client.getUpdatedBy());
        }

        @Test
        @DisplayName("campos booleanos e nacionalidade nulos preservam o valor atual")
        void nullOptionalFieldsPreserveCurrentValues() {
            Client client = TestFixtures.client();
            client.setNationality("Brasileira");
            client.setIsWhatsapp(true);
            client.setHasDisability(true);
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            service.updatePersonalData(TestFixtures.CLIENT_ID, new ClientPersonalDataRequestDTO());

            assertEquals("Brasileira", client.getNationality());
            assertTrue(client.getIsWhatsapp());
            assertTrue(client.getHasDisability());
        }

        @Test
        @DisplayName("cliente inexistente estoura 404 em get e update")
        void missingClientThrows() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            UUID id = UUID.randomUUID();
            ClientPersonalDataRequestDTO dto = new ClientPersonalDataRequestDTO();

            assertThrows(NotFoundException.class, () -> service.getPersonalData(id));
            assertThrows(NotFoundException.class, () -> service.updatePersonalData(id, dto));
        }
    }

    @Nested
    @DisplayName("aba Dados profissionais")
    class ProfessionalData {

        @Test
        @DisplayName("get devolve os campos profissionais gravados")
        void getReturnsProfessionalFields() {
            Client client = TestFixtures.client();
            client.setProfession("Costureira");
            client.setContributionYears(10);
            client.setContributionMonths(0);
            client.setContributionDays(0);
            client.setContributionInMonths(120);
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            ClientProfessionalDataResponseDTO dto =
                    service.getProfessionalData(TestFixtures.CLIENT_ID);

            assertEquals("Costureira", dto.getProfession());
            assertEquals(10, dto.getContributionYears());
            assertEquals("10 anos", dto.getContributionTime(), "frase derivada, não gravada");
            assertEquals(120, dto.getContributionInMonths());
        }

        @Test
        @DisplayName("update recalcula o total de meses a partir dos números enviados")
        void updateRecomputesMonths() {
            Client client = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));
            authenticate();

            ClientProfessionalDataRequestDTO dto = new ClientProfessionalDataRequestDTO();
            dto.setProfession("Pedreiro");
            dto.setNitPis("123");
            dto.setCtps("CTPS1");
            dto.setCtpsSeries("S1");
            dto.setBeneficiaryNumber("BN1");
            dto.setContributionYears(5);
            dto.setContributionMonths(6);
            dto.setContributionDays(20);
            dto.setInssPassword("nova-senha");

            ClientProfessionalDataResponseDTO response =
                    service.updateProfessionalData(TestFixtures.CLIENT_ID, dto);

            assertEquals(67, response.getContributionInMonths());
            assertEquals("5 anos, 6 meses e 20 dias", response.getContributionTime());
            assertEquals(67, client.getContributionInMonths());
            assertEquals("Pedreiro", client.getProfession());
            assertEquals("nova-senha", client.getInssPassword());
            assertEquals(TestFixtures.USER_ID, client.getUpdatedBy());
        }

        @Test
        @DisplayName("tempo de contribuição nulo zera o total em meses")
        void nullContributionTimeClearsMonths() {
            Client client = TestFixtures.client();
            client.setContributionInMonths(120);
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            service.updateProfessionalData(
                    TestFixtures.CLIENT_ID, new ClientProfessionalDataRequestDTO());

            assertNull(client.getContributionInMonths());
        }

        @Test
        @DisplayName("cliente inexistente estoura 404 em get e update")
        void missingClientThrows() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            UUID id = UUID.randomUUID();
            ClientProfessionalDataRequestDTO dto = new ClientProfessionalDataRequestDTO();

            assertThrows(NotFoundException.class, () -> service.getProfessionalData(id));
            assertThrows(NotFoundException.class, () -> service.updateProfessionalData(id, dto));
        }
    }
}
