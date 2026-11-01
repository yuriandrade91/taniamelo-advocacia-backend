package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Dados de catálogo de um tenant (escritório) expostos pela API. */
@JsonPropertyOrder({"id"})
@Getter
@Setter
public class TenantResponseDTO {

    private UUID id;
    private String slug;
    private String razaoSocial;
    private String cnpj;
    private String responsavel;
    private String email;
    private String telefone;
    private String plano;
    private String status;
    private Instant criadoEm;
    private Instant atualizadoEm;
}
