package com.lawfirm.law.firm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Vários endereços de um cliente em uma requisição só.
 *
 * <p>Existe por causa da atomicidade: o cadastro permite mais de um endereço e, enviando um POST
 * por endereço, uma falha no segundo deixava o cliente com a ficha pela metade e ninguém sabendo
 * quais tinham entrado. Aqui ou entram todos ou não entra nenhum.
 *
 * <p>Limite de 10 é folga sobre o uso real (o cadastro trabalha com três): existe para que um corpo
 * absurdo seja recusado na validação, não na transação.
 */
public class ClientAddressBatchRequestDTO {

    @Valid
    @NotEmpty(message = "Informe ao menos um endereço.")
    @Size(max = 10, message = "No máximo 10 endereços por requisição.")
    private List<ClientAddressRequestDTO> addresses;

    public List<ClientAddressRequestDTO> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<ClientAddressRequestDTO> addresses) {
        this.addresses = addresses;
    }
}
