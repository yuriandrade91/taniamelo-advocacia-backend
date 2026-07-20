package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.PaymentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {
    private static final Logger log = LoggerFactory.getLogger(PaymentStatusConverter.class);

    @Override
    public String convertToDatabaseColumn(PaymentStatus attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public PaymentStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return PaymentStatus.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown PaymentStatus value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
