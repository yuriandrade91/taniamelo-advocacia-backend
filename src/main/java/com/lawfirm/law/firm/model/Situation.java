package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/**
 * Situação (etapa) do cliente no fluxo do escritório. Persistida pelo label via converter.
 *
 * <p><b>O funil tem ordem</b>, e ela está declarada aqui. Antes não estava em lugar nenhum: o
 * código parecia um funil - seis etapas com nomes de sequência - e era uma lista de rótulos soltos.
 * Dava para cadastrar um cliente já em "Benefício concluído" e ninguém sabia dizer se aquilo era um
 * erro de digitação ou um cliente que chegou com o caso pronto.
 *
 * <p><b>Voltar é permitido</b>, e isso é deliberado. Situação é preenchida por gente, gente erra a
 * linha da lista, e um funil que recusa a correção obriga a corrigir por fora - editando o banco,
 * ou criando um cliente novo e perdendo a trilha. O que o sistema faz é marcar: cada entrada do
 * histórico sabe dizer se aquela mudança avançou ou voltou ({@code retrocesso} em {@code
 * ClientSituationHistoryDTO}), e quem lê a linha do tempo vê a diferença.
 *
 * <p>Pular etapa para a frente também é permitido, e nem é anormal: cliente que chega com a
 * documentação toda pronta salta a análise documental.
 */
public enum Situation {
    FORMULARIO_PREENCHIDO("Formulário preenchido", 1),
    ANALISE_DOCUMENTAL("Análise documental", 2),
    PLANEJAMENTO_EM_EXECUCAO("Planejamento em execução", 3),
    PLANEJAMENTO_CONCLUIDO("Planejamento concluído", 4),
    BENEFICIO_FUTURO("Benefício futuro", 5),
    BENEFICIO_CONCLUIDO("Benefício concluído", 6);

    private final String label;
    private final int ordem;

    Situation(String label, int ordem) {
        this.label = label;
        this.ordem = ordem;
    }

    /**
     * Posição no funil, de 1 a 6. Não é o {@code ordinal()} de propósito: reordenar as constantes
     * mudaria o ordinal em silêncio, e este número é regra de negócio, não posição no arquivo.
     */
    public int getOrdem() {
        return ordem;
    }

    /**
     * A mudança de {@code anterior} para {@code nova} anda para trás no funil.
     *
     * <p>Sem anterior (situação inicial, no cadastro) não há retrocesso: o cliente começou ali, não
     * voltou para ali.
     */
    public static boolean ehRetrocesso(Situation anterior, Situation nova) {
        return anterior != null && nova != null && nova.ordem < anterior.ordem;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static Situation fromLabel(String label) {
        return EnumLabelSupport.fromLabel(Situation.class, Situation::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
