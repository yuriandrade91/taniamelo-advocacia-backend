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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
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

    private ClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImpl(repository, mapper, historyRepository);
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
            entity.setContributionTime("3 anos, 10 meses, 22 dias");
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
            Client entity = TestFixtures.client();
            entity.setContributionTime(null);
            when(mapper.toEntity(any())).thenReturn(entity);
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.create(createDto());

            assertNull(entity.getContributionInMonths());
        }

        @Test
        @DisplayName("CPF já cadastrado é bloqueado antes de gravar")
        void rejectsDuplicateCpf() {
            ClientCreateRequestDTO dto = createDto();
            when(repository.existsByCpf("529.982.247-25")).thenReturn(true);

            ValidationException ex =
                    assertThrows(ValidationException.class, () -> service.create(dto));
            assertEquals("cpf", ex.getField());
            assertEquals(ValidationErrorCode.DUPLICATE_VALUE, ex.getValidationErrorCode());
            assertEquals("CPF já cadastrado", ex.getMessage());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("NIT/PIS duplicado é bloqueado")
        void rejectsDuplicateNitPis() {
            ClientCreateRequestDTO dto = createDto();
            dto.setNitPis("12345");
            when(repository.existsByNitPis("12345")).thenReturn(true);

            assertEquals(
                    "nitPis",
                    assertThrows(ValidationException.class, () -> service.create(dto)).getField());
        }

        @Test
        @DisplayName("número do benefício duplicado é bloqueado (sem diferenciar caixa)")
        void rejectsDuplicateBeneficiaryNumber() {
            ClientCreateRequestDTO dto = createDto();
            dto.setBeneficiaryNumber("bn-1");
            when(repository.existsByBeneficiaryNumberIgnoreCase("bn-1")).thenReturn(true);

            assertEquals(
                    "beneficiaryNumber",
                    assertThrows(ValidationException.class, () -> service.create(dto)).getField());
        }

        @Test
        @DisplayName("campos únicos em branco não são checados contra duplicidade")
        void blankUniqueFieldsAreSkipped() {
            ClientCreateRequestDTO dto = createDto();
            dto.setNitPis("   ");
            dto.setBeneficiaryNumber(null);
            when(mapper.toEntity(any())).thenReturn(TestFixtures.client());
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.create(dto);

            verify(repository, never()).existsByNitPis(anyString());
            verify(repository, never()).existsByBeneficiaryNumberIgnoreCase(anyString());
        }

        @Test
        @DisplayName("o valor checado é o texto sem espaços nas pontas")
        void trimsBeforeCheckingDuplicates() {
            ClientCreateRequestDTO dto = createDto();
            dto.setCpf("  529.982.247-25  ");
            when(mapper.toEntity(any())).thenReturn(TestFixtures.client());
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());

            service.create(dto);

            verify(repository).existsByCpf("529.982.247-25");
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
        @DisplayName("delete remove quando o cliente existe")
        void deleteRemovesExistingClient() {
            when(repository.existsById(TestFixtures.CLIENT_ID)).thenReturn(true);

            service.delete(TestFixtures.CLIENT_ID);

            verify(repository).deleteById(TestFixtures.CLIENT_ID);
        }

        @Test
        @DisplayName("delete de id inexistente estoura 404 sem chamar o banco")
        void deleteOfMissingClientThrows() {
            UUID id = UUID.randomUUID();
            when(repository.existsById(id)).thenReturn(false);

            NotFoundException ex = assertThrows(NotFoundException.class, () -> service.delete(id));
            assertTrue(ex.getMessage().contains(id.toString()));
            verify(repository, never()).deleteById(any());
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
        @DisplayName("o total de meses é recalculado a partir do texto atualizado")
        void recomputesContributionMonths() {
            Client existing = TestFixtures.client();
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(existing));
            when(mapper.toDTO(any(Client.class))).thenReturn(new ClientDetailsDTO());
            org.mockito.Mockito.doAnswer(
                            invocation -> {
                                existing.setContributionTime("2 anos");
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
            assertEquals("111.444.777-35", client.getCpf());
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
            client.setContributionTime("10 anos");
            client.setContributionInMonths(120);
            when(repository.findById(TestFixtures.CLIENT_ID)).thenReturn(Optional.of(client));

            ClientProfessionalDataResponseDTO dto =
                    service.getProfessionalData(TestFixtures.CLIENT_ID);

            assertEquals("Costureira", dto.getProfession());
            assertEquals("10 anos", dto.getContributionTime());
            assertEquals(120, dto.getContributionInMonths());
            assertEquals("senha-inss", dto.getInssPassword());
        }

        @Test
        @DisplayName("update recalcula o total de meses a partir do texto enviado")
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
            dto.setContributionTime("5 anos, 6 meses, 20 dias");
            dto.setInssPassword("nova-senha");

            ClientProfessionalDataResponseDTO response =
                    service.updateProfessionalData(TestFixtures.CLIENT_ID, dto);

            assertEquals(67, response.getContributionInMonths());
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
