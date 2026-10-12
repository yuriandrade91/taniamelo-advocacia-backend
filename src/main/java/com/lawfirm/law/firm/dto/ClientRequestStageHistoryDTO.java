package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma entrada da linha do tempo do requerimento no INSS.
 *
 * <p>Traz a etapa anterior junto da nova pelo mesmo motivo de {@link ClientSituationHistoryDTO}: a
 * linha do tempo precisa dizer "de X para Y", e reconstruir o "de" a partir da entrada anterior da
 * lista erra na primeira página de uma listagem paginada.
 *
 * <p>{@code previousStage} é nulo na primeira entrada - o momento em que o requerimento foi
 * protocolado e não havia etapa anterior.
 *
 * <p><b>Não há {@code retrocesso} aqui</b>, diferente da esteira do escritório. Voltar de "Recurso"
 * para "Exigência" é caminho real no INSS, não erro de digitação a sinalizar - marcar isso como
 * anomalia treinaria quem lê a ignorar o aviso.
 *
 * <p>No lugar dele vem {@code diasNaEtapaAnterior}, que é a pergunta que esta tela existe para
 * responder: num domínio onde prazo decide direito, "está em exigência há 47 dias" é a informação
 * que faz alguém agir.
 */
@JsonPropertyOrder({"id"})
public class ClientRequestStageHistoryDTO {
    private UUID id;
    private String previousStage;
    private String currentStage;
    private Instant changedAt;
    private UUID changedByUserId;

    /** Nulo na primeira entrada: não havia etapa anterior a cronometrar. */
    private Long diasNaEtapaAnterior;

    /**
     * O motivo da transição - o que o INSS exigiu, por que indeferiu, contra o quê se recorre.
     * Sempre presente ao entrar em exigência, indeferimento ou recurso; nas demais, só se quem
     * mudou escreveu.
     */
    private String justification;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPreviousStage() {
        return previousStage;
    }

    public void setPreviousStage(String previousStage) {
        this.previousStage = previousStage;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public UUID getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(UUID changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public Long getDiasNaEtapaAnterior() {
        return diasNaEtapaAnterior;
    }

    public void setDiasNaEtapaAnterior(Long diasNaEtapaAnterior) {
        this.diasNaEtapaAnterior = diasNaEtapaAnterior;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }
}
