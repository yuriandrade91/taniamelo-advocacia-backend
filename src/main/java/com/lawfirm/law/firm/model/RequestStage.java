package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/**
 * Etapa do requerimento junto ao INSS. Persistida pelo label via converter, como {@link Situation}.
 *
 * <p><b>Não confundir com {@link Situation}.</b> Aquela é a esteira do <i>escritório</i> - o que a
 * equipe já fez pelo cliente (formulário, análise documental, planejamento). Esta é a esteira do
 * <i>INSS</i> - onde o requerimento está no órgão. As duas andam em paralelo e por isso são campos
 * separados: um cliente pode estar com "Planejamento concluído" no escritório e "Exigência" no INSS
 * ao mesmo tempo, e colapsar as duas perderia justamente a informação que diz o que fazer a seguir.
 *
 * <p><b>Esta esteira se ramifica, e por isso não é um funil.</b> {@code DEFERIMENTO} e {@code
 * INDEFERIMENTO} são desfechos alternativos do mesmo ponto - nunca os dois -, e {@code RECURSO} só
 * faz sentido depois de um indeferimento. Daí os dois desfechos compartilharem a mesma {@link
 * #getOrdem() ordem}: a ordem aqui serve para exibir e agrupar, não para medir avanço. É também o
 * motivo de não existir aqui o conceito de retrocesso que {@link Situation} tem - voltar de
 * "Recurso" para "Exigência" é um caminho real no INSS, não um erro de digitação a sinalizar.
 *
 * <p><b>Nula é um estado legítimo</b>, não dado faltando: cliente em prospecção ou em planejamento
 * ainda não protocolou requerimento nenhum. A coluna é anulável de propósito, e marcar todo mundo
 * como "Requerimento" por padrão afirmaria um protocolo que não existe.
 */
public enum RequestStage {
    REQUERIMENTO("Requerimento", 1),
    EXIGENCIA("Exigência", 2),
    CUMPRIMENTO_DE_EXIGENCIA("Cumprimento de exigência", 3),
    DEFERIMENTO("Deferimento", 4),
    INDEFERIMENTO("Indeferimento", 4),
    RECURSO("Recurso", 5);

    private final String label;
    private final int ordem;

    RequestStage(String label, int ordem) {
        this.label = label;
        this.ordem = ordem;
    }

    /**
     * Posição na esteira, para exibição e agrupamento.
     *
     * <p>Não é {@code ordinal()} pelo mesmo motivo de {@link Situation#getOrdem()}: reordenar as
     * constantes mudaria o número em silêncio, e ele é regra de negócio. Diferente de lá, aqui o
     * valor se repete - deferimento e indeferimento ocupam a mesma posição porque são desfechos
     * mutuamente exclusivos do mesmo ponto do processo.
     */
    public int getOrdem() {
        return ordem;
    }

    /**
     * Desfecho que encerra o requerimento, sem etapa seguinte prevista.
     *
     * <p>Só o deferimento. O indeferimento não encerra: abre a possibilidade de recurso, e tratá-lo
     * como fim esconderia exatamente o caso em que o escritório ainda tem trabalho a fazer.
     */
    public boolean ehDesfechoFinal() {
        return this == DEFERIMENTO;
    }

    /**
     * Mudar para esta etapa exige justificativa.
     *
     * <p>São as três etapas que existem por causa de um <i>motivo</i>: a exigência (o que o INSS
     * pediu), o indeferimento (por que negou) e o recurso (contra o quê se recorre). É esse motivo
     * que orienta o próximo passo do escritório - juntar o documento, preparar o recurso -, e sem
     * ele o histórico diria "foi para exigência" deixando "exigência de quê?" na cabeça de quem
     * atendeu. As demais transições (protocolo, cumprimento, deferimento) se explicam sozinhas.
     */
    public boolean exigeJustificativa() {
        return this == EXIGENCIA || this == INDEFERIMENTO || this == RECURSO;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static RequestStage fromLabel(String label) {
        return EnumLabelSupport.fromLabel(RequestStage.class, RequestStage::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
