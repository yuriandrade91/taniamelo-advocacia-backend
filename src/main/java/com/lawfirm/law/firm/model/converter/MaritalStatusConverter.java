package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.MaritalStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class MaritalStatusConverter implements AttributeConverter<MaritalStatus, String> {
    private static final Logger log = LoggerFactory.getLogger(MaritalStatusConverter.class);

    @Override
    public String convertToDatabaseColumn(MaritalStatus attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public MaritalStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return MaritalStatus.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown MaritalStatus value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
