package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma entrada da linha do tempo de situação do cliente.
 *
 * <p>Traz a situação anterior junto da nova porque a linha do tempo precisa dizer "de X para Y".
 * Com só a nova, a tela consegue afirmar "passou para X" e nada mais - e reconstruir o "de" a
 * partir da entrada anterior da lista é errado na primeira página de uma listagem paginada, onde a
 * entrada anterior pode nem estar carregada.
 *
 * <p>{@code previousSituation} é nulo na primeira entrada (situação inicial, no cadastro).
 */
@JsonPropertyOrder({"id"})
public class ClientSituationHistoryDTO {
    private UUID id;
    private String previousSituation;
    private String currentSituation;
    private Instant changedAt;
    private UUID changedByUserId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPreviousSituation() {
        return previousSituation;
    }

    public void setPreviousSituation(String previousSituation) {
        this.previousSituation = previousSituation;
    }

    public String getCurrentSituation() {
        return currentSituation;
    }

    public void setCurrentSituation(String currentSituation) {
        this.currentSituation = currentSituation;
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
}
