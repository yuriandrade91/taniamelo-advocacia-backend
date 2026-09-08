package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.AppointmentType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class AppointmentTypeConverter implements AttributeConverter<AppointmentType, String> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentTypeConverter.class);

    @Override
    public String convertToDatabaseColumn(AppointmentType attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public AppointmentType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return AppointmentType.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown AppointmentType value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
