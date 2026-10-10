package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.RequestStage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Grava {@link RequestStage} pelo label PT-BR, como o {@link SituationConverter} faz. */
@Converter(autoApply = true)
public class RequestStageConverter implements AttributeConverter<RequestStage, String> {
    private static final Logger log = LoggerFactory.getLogger(RequestStageConverter.class);

    @Override
    public String convertToDatabaseColumn(RequestStage attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public RequestStage convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return RequestStage.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown RequestStage value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
