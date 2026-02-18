package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.Gender;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class GenderConverter implements AttributeConverter<Gender, String> {
    private static final Logger log = LoggerFactory.getLogger(GenderConverter.class);
    @Override
    public String convertToDatabaseColumn(Gender attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public Gender convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return Gender.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown Gender value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
