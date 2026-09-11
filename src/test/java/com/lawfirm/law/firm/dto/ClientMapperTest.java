package com.lawfirm.law.firm.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientSituationHistory;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.support.TestFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ClientMapper (MapStruct): DTO <-> entidade e a idade derivada")
class ClientMapperTest {

    private ClientMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ClientMapperImpl();
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
        dto.setMaritalStatus(MaritalStatus.CASADO);
        dto.setBenefit(BenefitType.APOSENTADORIA_RURAL);
        dto.setSituation(Situation.ANALISE_DOCUMENTAL);
        dto.setEmail("maria@x.com");
        dto.setProfession("Costureira");
        dto.setContributionTime("10 anos");
        return dto;
    }

    @Test
    @DisplayName("toEntity copia os campos e ignora id, autores e o total em meses")
    void toEntityCopiesFieldsAndIgnoresDerived() {
        Client entity = mapper.toEntity(createDto());

        assertEquals("Maria da Silva", entity.getFullName());
        assertEquals("529.982.247-25", entity.getCpf());
        assertEquals(Gender.FEMININO, entity.getGender());
        assertEquals(BenefitType.APOSENTADORIA_RURAL, entity.getBenefit());
        assertEquals("10 anos", entity.getContributionTime());
        assertNull(entity.getId());
        assertNull(entity.getCreatedBy());
        assertNull(entity.getUpdatedBy());
        assertNull(entity.getContributionInMonths(), "derivado no service, nunca no mapper");
    }

    @Test
    @DisplayName("notBillable ausente vira false e clientType ausente vira Potencial")
    void appliesDefaultsOnCreate() {
        Client entity = mapper.toEntity(createDto());

        assertFalse(entity.getNotBillable());
        assertEquals(ClientType.POTENCIAL, entity.getClientType());
    }

    @Test
    @DisplayName("notBillable e clientType informados são respeitados")
    void explicitValuesWinOnCreate() {
        ClientCreateRequestDTO dto = createDto();
        dto.setNotBillable(true);
        dto.setClientType(ClientType.VERIFICADO);

        Client entity = mapper.toEntity(dto);

        assertTrue(entity.getNotBillable());
        assertEquals(ClientType.VERIFICADO, entity.getClientType());
    }

    @Test
    @DisplayName("campos omitidos no DTO recebem o padrão da coluna, não null")
    void omittedFieldsGetColumnDefaults() {
        // nationality, is_whatsapp e has_disability são NOT NULL DEFAULT no banco
        // (V2__clients.sql) e opcionais no contrato. O mapper copiava o campo do DTO
        // mesmo quando null, apagando o default da entidade - e o INSERT falhava com
        // violação de NOT NULL, que a API devolvia como 409 DATABASE_INTEGRITY_ERROR.
        // Quem seguia o Swagger à risca não conseguia cadastrar cliente.
        //
        // A correção é o @AfterMapping do ClientMapper. A sugestão antiga
        // (nullValuePropertyMappingStrategy = IGNORE) não serve: ela vale para métodos
        // de atualização com @MappingTarget, e toEntity cria a entidade.
        ClientCreateRequestDTO dto = createDto();
        assertNull(dto.getNationality());
        assertNull(dto.getIsWhatsapp());
        assertNull(dto.getHasDisability());

        Client mapped = mapper.toEntity(dto);

        assertEquals("Brasileira", mapped.getNationality());
        assertTrue(mapped.getIsWhatsapp());
        assertFalse(mapped.getHasDisability());
        assertFalse(mapped.getNotBillable());
        assertEquals(ClientType.POTENCIAL, mapped.getClientType());
    }

    @Test
    @DisplayName("valores informados nesses campos são preservados")
    void explicitDefaultableValuesAreKept() {
        ClientCreateRequestDTO dto = createDto();
        dto.setNationality("Portuguesa");
        dto.setIsWhatsapp(false);
        dto.setHasDisability(true);

        Client mapped = mapper.toEntity(dto);

        assertEquals("Portuguesa", mapped.getNationality());
        assertFalse(mapped.getIsWhatsapp());
        assertTrue(mapped.getHasDisability());
    }

    @Test
    @DisplayName("toEntity de null devolve null")
    void toEntityOfNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    @DisplayName("updateEntityFromDto altera a entidade no lugar, preservando id e criação")
    void updateKeepsIdentityFields() {
        Client existing = TestFixtures.client();
        UUID originalId = existing.getId();
        Instant createdAt = existing.getCreatedAt();
        existing.setCreatedBy(TestFixtures.USER_ID);

        ClientUpdateRequestDTO dto = new ClientUpdateRequestDTO();
        dto.setFullName("Maria Souza");
        dto.setBirthDate(LocalDate.of(1975, 1, 1));
        dto.setCpf("111.444.777-35");
        dto.setMotherName("Ana");
        dto.setMobilePhone("+5511911111111");
        dto.setInssPassword("nova");
        dto.setGender(Gender.FEMININO);
        dto.setBenefit(BenefitType.APOSENTADORIA_ESPECIAL);
        dto.setSituation(Situation.BENEFICIO_CONCLUIDO);

        mapper.updateEntityFromDto(dto, existing);

        assertEquals("Maria Souza", existing.getFullName());
        assertEquals(BenefitType.APOSENTADORIA_ESPECIAL, existing.getBenefit());
        assertEquals(Situation.BENEFICIO_CONCLUIDO, existing.getSituation());
        assertEquals(originalId, existing.getId(), "id nunca muda no update");
        assertEquals(createdAt, existing.getCreatedAt());
        assertEquals(TestFixtures.USER_ID, existing.getCreatedBy());
    }

    @Test
    @DisplayName("no update, notBillable e clientType nulos preservam o valor atual")
    void updateKeepsCurrentValuesWhenOmitted() {
        Client existing = TestFixtures.client();
        existing.setNotBillable(true);
        existing.setClientType(ClientType.VERIFICADO);

        mapper.updateEntityFromDto(new ClientUpdateRequestDTO(), existing);

        assertTrue(existing.getNotBillable());
        assertEquals(ClientType.VERIFICADO, existing.getClientType());
    }

    @Test
    @DisplayName("no update, valores explícitos sobrescrevem")
    void updateAppliesExplicitValues() {
        Client existing = TestFixtures.client();
        existing.setNotBillable(true);

        ClientUpdateRequestDTO dto = new ClientUpdateRequestDTO();
        dto.setNotBillable(false);
        dto.setClientType(ClientType.VERIFICADO);

        mapper.updateEntityFromDto(dto, existing);

        assertFalse(existing.getNotBillable());
        assertEquals(ClientType.VERIFICADO, existing.getClientType());
    }

    @Test
    @DisplayName("updateEntityFromDto com dto null não altera a entidade")
    void updateWithNullDtoIsANoOp() {
        Client existing = TestFixtures.client();
        mapper.updateEntityFromDto(null, existing);
        assertEquals("Maria da Silva", existing.getFullName());
    }

    @Test
    @DisplayName("toDTO calcula a idade em UTC a partir da data de nascimento")
    void toDtoComputesAge() {
        Client entity = TestFixtures.client();
        entity.setBirthDate(LocalDate.now(ZoneOffset.UTC).minusYears(30).minusDays(1));

        ClientDetailsDTO dto = mapper.toDTO(entity);

        assertEquals(30, dto.getAge());
        assertEquals("Maria da Silva", dto.getFullName());
        assertEquals(entity.getId(), dto.getClientId());
    }

    @Test
    @DisplayName("aniversário exatamente hoje conta o ano completo")
    void ageOnBirthday() {
        Client entity = TestFixtures.client();
        entity.setBirthDate(LocalDate.now(ZoneOffset.UTC).minusYears(40));

        assertEquals(40, mapper.toDTO(entity).getAge());
    }

    @Test
    @DisplayName("sem data de nascimento a idade fica nula")
    void nullBirthDateLeavesAgeNull() {
        Client entity = TestFixtures.client();
        entity.setBirthDate(null);

        assertNull(mapper.toDTO(entity).getAge());
    }

    @Test
    @DisplayName("toDTO e toListDTO de null devolvem null")
    void nullMappingsAreNull() {
        assertNull(mapper.toDTO(null));
        assertNull(mapper.toListDTO(null));
        assertNull(mapper.toHistoryDTO(null));
    }

    @Test
    @DisplayName("toListDTO traz os campos do grid")
    void toListDtoMapsGridFields() {
        ClientListResponseDTO dto = mapper.toListDTO(TestFixtures.client());

        assertEquals(TestFixtures.CLIENT_ID, dto.getClientId());
        assertEquals("Maria da Silva", dto.getFullName());
        assertEquals("529.982.247-25", dto.getCpf());
    }

    @Nested
    @DisplayName("edição preserva as colunas NOT NULL que o contrato deixa opcionais")
    class PadroesObrigatoriosNaEdicao {

        @Test
        @DisplayName("update sem os opcionais MANTÉM o que está gravado, não volta ao padrão")
        void updateKeepsExistingValues() {
            Client existente = TestFixtures.client();
            existente.setNationality("Portuguesa");
            existente.setIsWhatsapp(false);
            existente.setHasDisability(true);
            existente.setNotBillable(true);
            existente.setClientType(ClientType.VERIFICADO);

            ClientUpdateRequestDTO dto = new ClientUpdateRequestDTO();
            dto.setFullName("Maria da Silva");
            dto.setBirthDate(LocalDate.of(1970, 5, 20));
            dto.setCpf("529.982.247-25");
            dto.setMotherName("Mãe");
            dto.setMobilePhone("+5531999990000");
            dto.setInssPassword("senha");
            dto.setGender(Gender.FEMININO);
            dto.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
            dto.setSituation(Situation.FORMULARIO_PREENCHIDO);

            mapper.updateEntityFromDto(dto, existente);

            // notBillable e clientType são decisões de gestão do caso: resetá-las numa
            // edição de endereço seria perda silenciosa.
            assertEquals("Portuguesa", existente.getNationality());
            assertEquals(Boolean.FALSE, existente.getIsWhatsapp());
            assertEquals(Boolean.TRUE, existente.getHasDisability());
            assertEquals(Boolean.TRUE, existente.getNotBillable());
            assertEquals(ClientType.VERIFICADO, existente.getClientType());
        }

        @Test
        @DisplayName("update com valor explícito sobrescreve")
        void updateOverwritesWhenSent() {
            Client existente = TestFixtures.client();
            existente.setClientType(ClientType.POTENCIAL);

            ClientUpdateRequestDTO dto = new ClientUpdateRequestDTO();
            dto.setFullName("Maria da Silva");
            dto.setBirthDate(LocalDate.of(1970, 5, 20));
            dto.setCpf("529.982.247-25");
            dto.setMotherName("Mãe");
            dto.setMobilePhone("+5531999990000");
            dto.setInssPassword("senha");
            dto.setGender(Gender.FEMININO);
            dto.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
            dto.setSituation(Situation.FORMULARIO_PREENCHIDO);
            dto.setClientType(ClientType.VERIFICADO);
            dto.setNationality("Italiana");

            mapper.updateEntityFromDto(dto, existente);

            assertEquals(ClientType.VERIFICADO, existente.getClientType());
            assertEquals("Italiana", existente.getNationality());
        }
    }

    @Test
    @DisplayName("toHistoryDTO renomeia newSituation e changedBy para o contrato da API")
    void toHistoryDtoRenamesFields() {
        ClientSituationHistory history = new ClientSituationHistory();
        history.setId(UUID.randomUUID());
        history.setPreviousSituation("Formulário preenchido");
        history.setNewSituation("Análise documental");
        history.setChangedAt(Instant.parse("2026-04-01T12:00:00Z"));
        history.setChangedBy(TestFixtures.USER_ID);

        ClientSituationHistoryDTO dto = mapper.toHistoryDTO(history);

        assertEquals("Análise documental", dto.getCurrentSituation());
        // "de X para Y": sem previousSituation a linha do tempo só consegue dizer "passou para Y".
        assertEquals("Formulário preenchido", dto.getPreviousSituation());
        assertEquals(history.getId(), dto.getId());
        assertEquals(TestFixtures.USER_ID, dto.getChangedByUserId());
        assertEquals(Instant.parse("2026-04-01T12:00:00Z"), dto.getChangedAt());
    }

    @Test
    @DisplayName("toHistoryDTO da primeira entrada vem sem situação anterior")
    void toHistoryDtoOfFirstEntryHasNoPrevious() {
        ClientSituationHistory first = new ClientSituationHistory();
        first.setId(UUID.randomUUID());
        first.setNewSituation("Formulário preenchido");
        first.setChangedAt(Instant.parse("2026-04-01T12:00:00Z"));

        assertNull(mapper.toHistoryDTO(first).getPreviousSituation());
    }

    @Test
    @DisplayName("computeAge com entidade null não quebra")
    void computeAgeWithNullEntity() {
        ClientDetailsDTO dto = new ClientDetailsDTO();
        mapper.computeAge(null, dto);
        assertNull(dto.getAge());
    }
}
