package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.validation.ValidCPF;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Aba "Dados pessoais": identidade e contato. Dados profissionais têm endpoint próprio
 * (/professional-data); endereços idem (/addresses).
 */
@Getter
@Setter
public class ClientPersonalDataRequestDTO {

    @Size(max = 255)
    @NotBlank
    private String fullName;

    /**
     * No passado, sempre. Sem isto, {@code 2090-01-01} era aceito com 201 e a ficha passava a
     * mostrar {@code age: -63} - verificado rodando.
     */
    @Past(message = "Data de nascimento precisa estar no passado")
    @NotNull
    private LocalDate birthDate;

    @Size(max = 14)
    @NotBlank
    @ValidCPF
    private String cpf;

    @Size(max = 20)
    private String rg;

    @Size(max = 20)
    private String rgIssuer;

    @PastOrPresent(message = "Data de emissão do RG não pode estar no futuro")
    private LocalDate rgIssueDate;

    @Size(max = 255)
    @NotBlank
    private String motherName;

    @NotNull private Gender gender;

    private MaritalStatus maritalStatus;

    @Size(max = 50)
    private String nationality;

    @Size(max = 20)
    @NotBlank
    private String mobilePhone;

    private Boolean isWhatsapp;

    @Size(max = 20)
    private String referencePhone;

    @Size(max = 255)
    private String referenceResponsible;

    @Size(max = 255)
    @Email
    private String email;

    private Boolean hasDisability;

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }
}
