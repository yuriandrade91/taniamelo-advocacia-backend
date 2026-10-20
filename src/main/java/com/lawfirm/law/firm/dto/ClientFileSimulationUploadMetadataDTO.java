package com.lawfirm.law.firm.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Metadados de uma simulação no upload em lote (uma entrada por arquivo, na mesma ordem). */
@Getter
@Setter
public class ClientFileSimulationUploadMetadataDTO {

    private LocalDate simulationDate;

    /** Texto livre no formato definido pelo usuário, ex.: "v1.0.2". */
    private String version;

    /** Quantidade de vínculos considerados na simulação. */
    private Integer vinculos;

    private String notes;
}
