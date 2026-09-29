package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/**
 * Categoria de uma despesa do escritório.
 *
 * <p>O vocabulário veio do frontend, que o declarou primeiro ({@code
 * src/enums/expenseCategory/ExpenseCategory.ts}) enquanto o Java não existia. As chaves e os
 * rótulos são os mesmos, letra por letra: divergir aqui faria a tela mostrar a categoria crua em
 * vez do nome, e só numa das pontas.
 *
 * <p>Não há categoria para custa adiantada por um cliente específico, de propósito. Aquilo é valor
 * a reembolsar, e reembolso tem lugar próprio ({@code client_payments}); registrar como despesa o
 * faria sair do resultado do escritório duas vezes — uma como gasto, outra como receita que nunca
 * entra.
 */
public enum ExpenseCategory {
    ALUGUEL("Aluguel e condomínio"),
    PESSOAL("Salários e encargos"),
    CUSTAS_PROCESSUAIS("Custas processuais"),
    SOFTWARE("Software e assinaturas"),
    MARKETING("Marketing e captação"),
    CONTABILIDADE("Contabilidade e jurídico"),
    IMPOSTOS("Impostos e taxas"),
    MATERIAL("Material de escritório"),
    DESLOCAMENTO("Deslocamento e viagens"),
    OUTROS("Outros");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ExpenseCategory fromLabel(String label) {
        return EnumLabelSupport.fromLabel(ExpenseCategory.class, ExpenseCategory::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
