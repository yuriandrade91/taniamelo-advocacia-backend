package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.BenefitType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BenefitTypeConverter implements AttributeConverter<BenefitType, String> {
    @Override
    public String convertToDatabaseColumn(BenefitType attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public BenefitType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BenefitType.fromLabel(dbData);
    }
}
