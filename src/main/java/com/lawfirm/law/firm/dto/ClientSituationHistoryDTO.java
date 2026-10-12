package com.lawfirm.law.firm.dto;

import java.time.Instant;
import java.util.UUID;

public class ClientSituationHistoryDTO {
    private UUID id;
    private String previousSituation;
    private String currentSituation;
    private Instant changedAt;
    private UUID changedByUserId;
    private boolean retrocesso;

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

    public boolean isRetrocesso() {
        return retrocesso;
    }

    public boolean getRetrocesso() {
        return retrocesso;
    }

    public void setRetrocesso(boolean retrocesso) {
        this.retrocesso = retrocesso;
    }
}
