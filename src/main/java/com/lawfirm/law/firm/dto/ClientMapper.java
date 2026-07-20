package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Client;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Period;
import java.time.ZoneId;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ClientMapper {

    // ── Create: request DTO -> new entity ──
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "contributionInMonths", ignore = true)
    @Mapping(target = "notBillable", expression = "java(dto.getNotBillable() != null ? dto.getNotBillable() : false)")
    @Mapping(target = "clientType", expression = "java(dto.getClientType() != null ? dto.getClientType() : com.lawfirm.law.firm.model.ClientType.POTENCIAL)")
    Client toEntity(ClientCreateRequestDTO dto);

    // ── Update: request DTO -> existing entity (in place) ──
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "contributionInMonths", ignore = true)
    @Mapping(target = "notBillable", expression = "java(dto.getNotBillable() != null ? dto.getNotBillable() : entity.getNotBillable())")
    @Mapping(target = "clientType", expression = "java(dto.getClientType() != null ? dto.getClientType() : entity.getClientType())")
    void updateEntityFromDto(ClientUpdateRequestDTO dto, @MappingTarget Client entity);

    // ── Response mapping ──
    ClientDetailsDTO toDTO(Client entity);

    // map entity -> list item DTO for grid/listing
    ClientListResponseDTO toListDTO(Client entity);

    // history mapping
    @Mapping(target = "currentSituation", source = "newSituation")
    @Mapping(target = "changedByUserId", source = "changedBy")
    ClientSituationHistoryDTO toHistoryDTO(com.lawfirm.law.firm.model.ClientSituationHistory h);

    @AfterMapping
    default void computeAge(Client entity, @MappingTarget ClientDetailsDTO dto) {
        if (entity != null && entity.getBirthDate() != null) {
            dto.setAge(Period.between(entity.getBirthDate(), java.time.LocalDate.now(ZoneId.systemDefault())).getYears());
        }
    }
}
