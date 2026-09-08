package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.AppointmentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class AppointmentStatusConverter implements AttributeConverter<AppointmentStatus, String> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentStatusConverter.class);

    @Override
    public String convertToDatabaseColumn(AppointmentStatus attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public AppointmentStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return AppointmentStatus.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown AppointmentStatus value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
