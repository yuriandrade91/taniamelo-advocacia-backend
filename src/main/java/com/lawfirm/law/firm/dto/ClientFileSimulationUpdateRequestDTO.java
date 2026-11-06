package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Atualização parcial dos metadados de uma simulação (o arquivo em si não muda). */
@Getter
@Setter
public class ClientFileSimulationUpdateRequestDTO {

    private LocalDate simulationDate;

    @Size(max = 30)
    private String version;

    private Integer vinculos;
    private String notes;
}
