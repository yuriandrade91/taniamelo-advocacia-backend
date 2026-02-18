package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.BenefitType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class BenefitTypeConverter implements AttributeConverter<BenefitType, String> {
    private static final Logger log = LoggerFactory.getLogger(BenefitTypeConverter.class);
    @Override
    public String convertToDatabaseColumn(BenefitType attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public BenefitType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return BenefitType.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown BenefitType value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
