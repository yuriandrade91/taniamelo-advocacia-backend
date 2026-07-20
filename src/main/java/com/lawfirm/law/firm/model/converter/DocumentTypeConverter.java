package com.lawfirm.law.firm.model.converter;

import com.lawfirm.law.firm.model.DocumentType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class DocumentTypeConverter implements AttributeConverter<DocumentType, String> {
    private static final Logger log = LoggerFactory.getLogger(DocumentTypeConverter.class);

    @Override
    public String convertToDatabaseColumn(DocumentType attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public DocumentType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return DocumentType.fromLabel(dbData);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown DocumentType value in DB: {} - returning null", dbData);
            return null;
        }
    }
}
