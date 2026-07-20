package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.AddressType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class AddressTypeConverter implements AttributeConverter<AddressType, String> {
    private static final Logger log = LoggerFactory.getLogger(AddressTypeConverter.class);

    @Override
    public String convertToDatabaseColumn(AddressType attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public AddressType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return AddressType.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown AddressType value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
