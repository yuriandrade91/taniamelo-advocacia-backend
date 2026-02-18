package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.Situation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class SituationConverter implements AttributeConverter<Situation, String> {
    private static final Logger log = LoggerFactory.getLogger(SituationConverter.class);
    @Override
    public String convertToDatabaseColumn(Situation attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public Situation convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return Situation.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown Situation value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
