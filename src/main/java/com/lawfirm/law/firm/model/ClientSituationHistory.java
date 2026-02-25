package com.lawfirm.law.firm.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_situation_history")
public class ClientSituationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "previous_situation", length = 100)
    private String previousSituation;

    @Column(name = "new_situation", length = 100)
    private String newSituation;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by")
    private Integer changedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public String getPreviousSituation() { return previousSituation; }
    public void setPreviousSituation(String previousSituation) { this.previousSituation = previousSituation; }

    public String getNewSituation() { return newSituation; }
    public void setNewSituation(String newSituation) { this.newSituation = newSituation; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public Integer getChangedBy() { return changedBy; }
    public void setChangedBy(Integer changedBy) { this.changedBy = changedBy; }
}
