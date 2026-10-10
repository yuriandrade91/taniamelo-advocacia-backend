package com.lawfirm.law.firm.dto;

/**
 * Atualização parcial do cliente (PATCH /clients/{clientId}): situação, benefício, tipo de cliente
 * e/ou arrecadação. Envie só os campos que quer mudar - null significa "não mexer nisso".
 * situation/benefit/clientType aceitam o nome do enum ou o label PT-BR (validado no service para
 * produzir erro amigável).
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
    private String benefit;
    private String clientType;
    private Boolean notBillable;

    public String getSituation() {
        return situation;
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
