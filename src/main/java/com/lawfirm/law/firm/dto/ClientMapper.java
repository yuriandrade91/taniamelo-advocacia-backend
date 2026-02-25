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
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nonBillable", expression = "java(dto.getNonBillable() != null ? dto.getNonBillable() : false)")
    Client toEntity(ClientDetailsDTO dto);
    ClientDetailsDTO toDTO(Client entity);

    // map entity -> list item DTO for grid/listing
    ClientListResponseDTO toListDTO(Client entity);

    // map create DTO to DTO (used in controller)
    ClientDetailsDTO fromCreate(ClientCreateRequestDTO createDto);

    // history mapping
    @org.mapstruct.Mapping(target = "currentSituation", source = "newSituation")
    ClientSituationHistoryDTO toHistoryDTO(com.lawfirm.law.firm.model.ClientSituationHistory h);

    @AfterMapping
    default void computeAge(Client entity, @MappingTarget ClientDetailsDTO dto) {
        if (entity != null && entity.getBirthDate() != null) {
            dto.setAge(Period.between(entity.getBirthDate(), java.time.LocalDate.now(ZoneId.systemDefault())).getYears());
        }
    }
}
