package com.lawfirm.law.firm.dto;

import java.util.List;

public class ClientHistoryResponseDTO {
    private ClientDetailsDTO client;
    private List<ClientSituationHistoryDTO> history;

    public ClientHistoryResponseDTO() {}

    public ClientHistoryResponseDTO(ClientDetailsDTO client, List<ClientSituationHistoryDTO> history) {
        this.client = client;
        this.history = history;
    }

    public ClientDetailsDTO getClient() { return client; }
    public void setClient(ClientDetailsDTO client) { this.client = client; }

    public List<ClientSituationHistoryDTO> getHistory() { return history; }
    public void setHistory(List<ClientSituationHistoryDTO> history) { this.history = history; }
}
