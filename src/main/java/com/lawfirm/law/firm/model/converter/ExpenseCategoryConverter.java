package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.ExpenseCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class ExpenseCategoryConverter implements AttributeConverter<ExpenseCategory, String> {
    private static final Logger log = LoggerFactory.getLogger(ExpenseCategoryConverter.class);

    @Override
    public String convertToDatabaseColumn(ExpenseCategory attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public ExpenseCategory convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return ExpenseCategory.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown ExpenseCategory value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
