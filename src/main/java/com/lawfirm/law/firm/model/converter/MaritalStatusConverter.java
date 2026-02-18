package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.MaritalStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MaritalStatusConverter implements AttributeConverter<MaritalStatus, String> {
    @Override
    public String convertToDatabaseColumn(MaritalStatus attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public MaritalStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MaritalStatus.fromLabel(dbData);
    }
}
