package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.repository.ClientRepository;
import java.util.UUID;

/**
 * Único ponto que resolve "cliente existe?" para os services de sub-recurso
 * (endereço/entrevista/pagamento/arquivo), que precisam da entidade {@link Client} completa (para
 * ligar a FK) e não só do DTO que {@code ClientService} expõe.
 */
final class ClientLookup {

    private ClientLookup() {}

    static Client orThrow(ClientRepository repository, UUID clientId) {
        return repository
                .findById(clientId)
                .orElseThrow(() -> NotFoundException.of("Cliente", clientId));
    }
}
