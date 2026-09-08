package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.AppointmentModality;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class AppointmentModalityConverter
        implements AttributeConverter<AppointmentModality, String> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentModalityConverter.class);

    @Override
    public String convertToDatabaseColumn(AppointmentModality attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public AppointmentModality convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return AppointmentModality.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown AppointmentModality value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
