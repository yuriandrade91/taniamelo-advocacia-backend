package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/**
 * Os 11 tipos de documento aceitos na aba "Documentos" da collection de
 * arquivos do cliente. Decisão de modelagem: enum no código (padrão adotado em
 * todo o projeto para listas fixas), não tabela de domínio no banco - a lista
 * só muda com deploy e não há tela administrativa para editá-la.
 */
public enum DocumentType {
    IDENTIFICACAO_SEGURADO("Documentos de identificação do segurado"),
    CADASTRAIS_DADOS_PESSOAIS("Documentos cadastrais / dados pessoais"),
    VINCULO_TEMPO_CONTRIBUICAO("Documentos de vínculo e tempo de contribuição"),
    CONTRIBUINTE_INDIVIDUAL_FACULTATIVO("Contribuinte individual / facultativo"),
    SEGURADO_ESPECIAL("Segurado especial"),
    ATIVIDADE_ESPECIAL("Atividade especial"),
    DOCUMENTOS_MEDICOS("Documentos médicos"),
    DEPENDENTES_RELACAO_FAMILIAR("Dependentes e relação familiar"),
    JUDICIAIS_ADMINISTRATIVOS("Documentos judiciais e administrativos"),
    DECLARACOES_AUTODECLARACOES("Declarações e autodeclarações"),
    OUTROS("Outros");

    private final String label;

    DocumentType(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static DocumentType fromLabel(String label) {
        return EnumLabelSupport.fromLabel(DocumentType.class, DocumentType::getLabel, label);
    }

    @Override
    public String toString() { return label; }
}
