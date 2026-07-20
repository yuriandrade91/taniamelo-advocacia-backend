package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.ClientType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class ClientTypeConverter implements AttributeConverter<ClientType, String> {
    private static final Logger log = LoggerFactory.getLogger(ClientTypeConverter.class);

    @Override
    public String convertToDatabaseColumn(ClientType attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public ClientType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return ClientType.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown ClientType value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
