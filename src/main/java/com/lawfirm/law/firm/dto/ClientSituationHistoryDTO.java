package com.lawfirm.law.firm.dto;

import java.time.Instant;
import java.util.UUID;

public class ClientSituationHistoryDTO {
    private UUID id;
    private String currentSituation;
    private Instant changedAt;
    private Integer changedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCurrentSituation() { return currentSituation; }
    public void setCurrentSituation(String currentSituation) { this.currentSituation = currentSituation; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public Integer getChangedBy() { return changedBy; }
    public void setChangedBy(Integer changedBy) { this.changedBy = changedBy; }
}
