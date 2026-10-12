package com.lawfirm.law.firm.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Uma transição da etapa do requerimento no INSS.
 *
 * <p>Espelha {@link ClientSituationHistory}, que faz o mesmo para a esteira do escritório. São
 * tabelas separadas porque são esteiras separadas: misturar as duas numa só exigiria uma coluna
 * discriminadora e faria toda consulta de uma filtrar a outra.
 *
 * <p>{@code previousStageEnteredAt} guarda quando a etapa anterior começou, para cada linha
 * responder sozinha "quanto tempo ficou nela". Ver V26 para o motivo de não derivar isso da linha
 * vizinha.
 */
@Entity
@Table(name = "client_request_stage_history")
public class ClientRequestStageHistory {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "previous_stage", length = 100)
    private String previousStage;

    @Column(name = "new_stage", length = 100)
    private String newStage;

    /** Nulo na primeira entrada: não havia etapa anterior a cronometrar. */
    @Column(name = "previous_stage_entered_at")
    private Instant previousStageEnteredAt;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by")
    private UUID changedBy;

    /**
     * Por que a etapa mudou. Obrigatória ao entrar em exigência, indeferimento ou recurso ({@link
     * RequestStage#exigeJustificativa()}); opcional nas demais. Nula nas entradas anteriores à V27.
     */
    @Column(name = "justification")
    private String justification;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getPreviousStage() {
        return previousStage;
    }

    public void setPreviousStage(String previousStage) {
        this.previousStage = previousStage;
    }

    public String getNewStage() {
        return newStage;
    }

    public void setNewStage(String newStage) {
        this.newStage = newStage;
    }

    public Instant getPreviousStageEnteredAt() {
        return previousStageEnteredAt;
    }

    public void setPreviousStageEnteredAt(Instant previousStageEnteredAt) {
        this.previousStageEnteredAt = previousStageEnteredAt;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(UUID changedBy) {
        this.changedBy = changedBy;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }
}
