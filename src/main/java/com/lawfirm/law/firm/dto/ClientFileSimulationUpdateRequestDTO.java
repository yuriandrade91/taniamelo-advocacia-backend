package com.lawfirm.law.firm.dto;

import java.time.LocalDate;

/** Atualização parcial dos metadados de uma simulação (o arquivo em si não muda). */
public class ClientFileSimulationUpdateRequestDTO {

    private LocalDate simulationDate;
    private String version;
    private Integer vinculos;
    private String notes;

    public LocalDate getSimulationDate() {
        return simulationDate;
    }

    public void setSimulationDate(LocalDate simulationDate) {
        this.simulationDate = simulationDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getVinculos() {
        return vinculos;
    }

    public void setVinculos(Integer vinculos) {
        this.vinculos = vinculos;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
