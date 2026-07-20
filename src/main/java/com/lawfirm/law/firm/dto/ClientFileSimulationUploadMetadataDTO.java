package com.lawfirm.law.firm.dto;

import java.time.LocalDate;

/** Metadados de uma simulação no upload em lote (uma entrada por arquivo, na mesma ordem). */
public class ClientFileSimulationUploadMetadataDTO {

    private LocalDate simulationDate;

    /** Texto livre no formato definido pelo usuário, ex.: "v1.0.2". */
    private String version;

    /** Quantidade de vínculos considerados na simulação. */
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
