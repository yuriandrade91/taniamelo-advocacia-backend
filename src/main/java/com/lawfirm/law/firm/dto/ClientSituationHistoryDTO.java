package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@JsonPropertyOrder({"id"})
@Getter
@Setter
public class ClientSituationHistoryDTO {
    private UUID id;
    private String previousSituation;
    private String currentSituation;
    private Instant changedAt;
    private UUID changedByUserId;
    private boolean retrocesso;
}
