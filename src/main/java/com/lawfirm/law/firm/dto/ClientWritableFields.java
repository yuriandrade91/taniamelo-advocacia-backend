package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrato comum dos campos que a API pode escrever em um Client, compartilhado entre
 * ClientCreateRequestDTO (POST) e ClientUpdateRequestDTO (PUT). Mantém os dois DTOs em sincronia
 * sem herança e permite validação de unicidade única.
 */
public interface ClientWritableFields {
    String getFullName();

    LocalDate getBirthDate();

    String getCpf();

    String getMotherName();

    String getMobilePhone();

    String getInssPassword();

    Gender getGender();

    String getRg();

    String getEmail();

    String getReferencePhone();

    String getReferenceResponsible();

    MaritalStatus getMaritalStatus();

    BenefitType getBenefit();

    Situation getSituation();

    String getBeneficiaryNumber();

    String getNitPis();

    String getProfession();

    String getCtps();

    String getCtpsSeries();

    String getContributionTime();

    Boolean getNotBillable();

    String getRgIssuer();

    LocalDate getRgIssueDate();

    String getNationality();

    Boolean getIsWhatsapp();

    Boolean getHasDisability();

    String getNotes();

    UUID getResponsibleUserId();

    ClientType getClientType();
}
