package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.Size;

/**
 * Atualização parcial do cliente (PATCH /clients/{clientId}): situação, etapa no INSS, benefício,
 * tipo de cliente e/ou arrecadação. Envie só os campos que quer mudar - null significa "não mexer
 * nisso". situation/benefit/clientType aceitam o nome do enum ou o label PT-BR (validado no service
 * para produzir erro amigável).
 *
 * <p><b>Recusa campo que não aplica, de propósito.</b> O default do Spring Boot é {@code
 * FAIL_ON_UNKNOWN_PROPERTIES=false}: sem a anotação abaixo, um PATCH com {@code mobilePhone} era
 * aceito, o campo descartado em silêncio e a resposta vinha 200. Quem chamou acreditava ter editado
 * o telefone, o banco seguia com o antigo, e a releitura - correta - devolvia o valor velho. O
 * sintoma aparecia como "a Home está em cache", e a investigação começava no lugar errado.
 *
 * <p>Com {@code ignoreUnknown = false} o mesmo PATCH passa a receber 400 nomeando o campo não
 * suportado. Não faz a edição funcionar - para telefone, nome e os outros campos o recurso é o
 * {@code PUT /clients/{clientId}} - mas para de mentir que funcionou.
 */
public class ClientPatchRequestDTO {

    /**
     * Campos que o corpo trouxe e este recurso não aplica.
     *
     * <p>{@code @JsonIgnoreProperties(ignoreUnknown = false)} NÃO resolve: esse é o valor default
     * da anotação e ela não reativa a falha quando {@code FAIL_ON_UNKNOWN_PROPERTIES} está
     * desligado globalmente - que é o default do Spring Boot. Tentado e medido: o PATCH continuava
     * respondendo 200.
     *
     * <p>Por isso a captura é explícita. O service recusa a requisição nomeando o que veio, em vez
     * de descartar em silêncio.
     */
    private final java.util.Map<String, Object> camposNaoSuportados =
            new java.util.LinkedHashMap<>();

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void capturarCampoNaoSuportado(String nome, Object valor) {
        camposNaoSuportados.put(nome, valor);
    }

    /** Nomes dos campos não suportados, na ordem em que apareceram no corpo. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.Set<String> camposNaoSuportados() {
        return camposNaoSuportados.keySet();
    }

    private String situation;

    /**
     * Etapa do requerimento no INSS. Entra aqui, e não só no PUT, porque é o campo que mais muda ao
     * longo de um caso: exigência vira cumprimento, cumprimento vira deferimento ou indeferimento.
     * Obrigar um PUT completo a cada andamento faria a tela reenviar o cadastro inteiro para mexer
     * numa palavra.
     */
    private String requestStage;

    /**
     * Por que a etapa no INSS mudou. Obrigatória quando {@code requestStage} leva a exigência,
     * indeferimento ou recurso; opcional nas demais etapas. Só vale junto de {@code requestStage}:
     * sozinha não teria onde ser gravada, e o PATCH não descarta campo em silêncio.
     */
    @Size(max = 1000)
    private String requestStageJustification;

    private String benefit;
    private String clientType;
    private Boolean notBillable;

    public String getSituation() {
        return situation;
    }

    public String getRequestStage() {
        return requestStage;
    }

    public void setRequestStage(String requestStage) {
        this.requestStage = requestStage;
    }

    public String getRequestStageJustification() {
        return requestStageJustification;
    }

    public void setRequestStageJustification(String requestStageJustification) {
        this.requestStageJustification = requestStageJustification;
    }

    public void setSituation(String situation) {
        this.situation = situation;
    }

    public String getBenefit() {
        return benefit;
    }

    public void setBenefit(String benefit) {
        this.benefit = benefit;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public Boolean getNotBillable() {
        return notBillable;
    }

    public void setNotBillable(Boolean notBillable) {
        this.notBillable = notBillable;
    }
}
