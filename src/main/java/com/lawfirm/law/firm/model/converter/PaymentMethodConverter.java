package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.PaymentMethod;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class PaymentMethodConverter implements AttributeConverter<PaymentMethod, String> {
    private static final Logger log = LoggerFactory.getLogger(PaymentMethodConverter.class);

    @Override
    public String convertToDatabaseColumn(PaymentMethod attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public PaymentMethod convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return PaymentMethod.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown PaymentMethod value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
