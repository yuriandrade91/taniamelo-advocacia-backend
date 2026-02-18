package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.Situation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SituationConverter implements AttributeConverter<Situation, String> {
    @Override
    public String convertToDatabaseColumn(Situation attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public Situation convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Situation.fromLabel(dbData);
    }
}
