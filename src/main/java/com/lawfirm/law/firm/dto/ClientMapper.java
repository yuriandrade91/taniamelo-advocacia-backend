package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Client;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ClientMapper {

    // ── Create: request DTO -> new entity ──
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "contributionInMonths", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Client toEntity(ClientCreateRequestDTO dto);

    // ── Update: request DTO -> existing entity (in place) ──
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "contributionInMonths", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntityFromDto(ClientUpdateRequestDTO dto, @MappingTarget Client entity);

    /**
     * Preenche os campos que o banco exige e o contrato deixa opcional.
     *
     * <p>A entidade já nasce com esses valores nos inicializadores de campo, mas o MapStruct copia
     * o DTO por cima e um campo ausente vira {@code null} — que a coluna {@code NOT NULL} recusa. O
     * resultado era um cadastro que seguia o Swagger à risca e recebia 409 {@code
     * DATABASE_INTEGRITY_ERROR}, sem dizer qual campo faltava.
     *
     * <p>Uma lista só, e não uma expressão por campo no {@code @Mapping}: o problema já tinha sido
     * remendado assim duas vezes ({@code notBillable} e {@code clientType}) e as três colunas
     * seguintes ficaram de fora. Coluna nova com {@code NOT NULL DEFAULT} entra aqui.
     */
    @AfterMapping
    default void aplicarPadroesDeColunaObrigatoria(@MappingTarget Client entity) {
        if (entity.getNationality() == null) {
            entity.setNationality("Brasileira");
        }
        if (entity.getIsWhatsapp() == null) {
            entity.setIsWhatsapp(true);
        }
        if (entity.getHasDisability() == null) {
            entity.setHasDisability(false);
        }
        if (entity.getNotBillable() == null) {
            entity.setNotBillable(false);
        }
        if (entity.getClientType() == null) {
            entity.setClientType(com.lawfirm.law.firm.model.ClientType.POTENCIAL);
        }
    }

    /**
     * No PUT, campo ausente nesses cinco significa "mantém o que está gravado", não "volta ao
     * padrão": {@code notBillable} e {@code clientType} são decisões de gestão do caso, e
     * resetá-las numa edição de endereço seria perda silenciosa.
     *
     * <p>Copiar o valor atual para o DTO antes do mapeamento resolve isso num lugar só. O DTO é
     * objeto de requisição, vive uma chamada e não é reaproveitado — por isso escrever nele aqui é
     * seguro, e é o que evita cinco expressões inline que a próxima coluna esqueceria de ganhar.
     */
    @BeforeMapping
    default void preservarObrigatoriosNaEdicao(
            ClientUpdateRequestDTO dto, @MappingTarget Client entity) {
        if (dto.getNationality() == null) {
            dto.setNationality(entity.getNationality());
        }
        if (dto.getIsWhatsapp() == null) {
            dto.setIsWhatsapp(entity.getIsWhatsapp());
        }
        if (dto.getHasDisability() == null) {
            dto.setHasDisability(entity.getHasDisability());
        }
        if (dto.getNotBillable() == null) {
            dto.setNotBillable(entity.getNotBillable());
        }
        if (dto.getClientType() == null) {
            dto.setClientType(entity.getClientType());
        }
        // inss_password também é NOT NULL, e desde que a senha saiu do GET o cliente da API não
        // tem como devolvê-la: ausente aqui só pode significar "mantém".
        if (dto.getInssPassword() == null || dto.getInssPassword().isBlank()) {
            dto.setInssPassword(entity.getInssPassword());
        }
    }

    // ── Response mapping ──
    @Mapping(target = "clientId", source = "id")
    ClientDetailsDTO toDTO(Client entity);

    // map entity -> list item DTO for grid/listing
    @Mapping(target = "clientId", source = "id")
    ClientListResponseDTO toListDTO(Client entity);

    // history mapping
    @Mapping(target = "previousSituation", source = "previousSituation")
    @Mapping(target = "currentSituation", source = "newSituation")
    @Mapping(target = "changedByUserId", source = "changedBy")
    ClientSituationHistoryDTO toHistoryDTO(com.lawfirm.law.firm.model.ClientSituationHistory h);

    @AfterMapping
    default void computeAge(Client entity, @MappingTarget ClientDetailsDTO dto) {
        if (entity != null && entity.getBirthDate() != null) {
            // Idade calculada em UTC para bater com ClientServiceImpl.ageOf e com o
            // time_zone=UTC fixado na camada JDBC - evita divergência de 1 dia no aniversário.
            dto.setAge(
                    Period.between(entity.getBirthDate(), LocalDate.now(ZoneOffset.UTC))
                            .getYears());
        }
    }
}
