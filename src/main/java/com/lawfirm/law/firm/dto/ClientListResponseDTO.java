package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Situation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonPropertyOrder({
    "clientId",
    "fullName",
    "birthDate",
    "cpf",
    "mobilePhone",
    "benefit",
    "situation",
    "clientType",
    "notBillable",
    "beneficiaryNumber",
    "createdAt",
    "updatedAt"
})
public class ClientListResponseDTO {
    private UUID clientId;
    private String fullName;

    /**
     * Data de nascimento.
     *
     * <p>Está aqui porque a Home responde "quem faz aniversário agora" sobre a base inteira, e essa
     * pergunta não tem endpoint próprio: sem o campo na linha, o front teria que buscar uma ficha
     * por cliente para descobrir a data de cada um. É um {@code LocalDate} - dia sem hora e sem
     * fuso -, então o cliente nascido em 01/10 não vira 30/09 em quem lê de outro fuso.
     */
    private LocalDate birthDate;

    private String cpf;
    private String mobilePhone;
    private BenefitType benefit;
    private Situation situation;
    private ClientType clientType;

    /**
     * Marca de "sem arrecadação", o mesmo campo filtrável por {@code ?notBillable=} na listagem.
     */
    private Boolean notBillable;

    private Instant createdAt;
    private String beneficiaryNumber;
    private Instant updatedAt;

    /**
     * Quando foi excluído. Nulo na listagem normal - só a lixeira ({@code GET /clients/deleted})
     * devolve registros com isto preenchido.
     */
    private Instant deletedAt;

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public BenefitType getBenefit() {
        return benefit;
    }

    public void setBenefit(BenefitType benefit) {
        this.benefit = benefit;
    }

    public Situation getSituation() {
        return situation;
    }

    public void setSituation(Situation situation) {
        this.situation = situation;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

    public Boolean getNotBillable() {
        return notBillable;
    }

    public void setNotBillable(Boolean notBillable) {
        this.notBillable = notBillable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getBeneficiaryNumber() {
        return beneficiaryNumber;
    }

    public void setBeneficiaryNumber(String beneficiaryNumber) {
        this.beneficiaryNumber = beneficiaryNumber;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
