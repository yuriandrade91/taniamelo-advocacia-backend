package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Filtros da listagem de clientes. Lista vazia e lista nula significam a mesma coisa - "não filtra
 * por isto" - e o construtor guarda sempre a vazia, para quem consome não ter de tratar as duas.
 */
public record ClientListFilter(
        String searchTerm,
        List<BenefitType> benefitTypes,
        List<Situation> situations,
        List<RequestStage> requestStages,
        List<ClientType> clientTypes,
        Boolean notBillable,
        Instant createdFrom,
        Instant createdTo) {

    public ClientListFilter {
        benefitTypes = semNulos(benefitTypes);
        situations = semNulos(situations);
        requestStages = semNulos(requestStages);
        clientTypes = semNulos(clientTypes);
    }

    // "?benefitType=" vazio chega como [null]: o nulo é "nada informado", não um valor a filtrar.
    private static <T> List<T> semNulos(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    /** Sem filtro nenhum. */
    public static ClientListFilter none() {
        return new ClientListFilter(null, null, null, null, null, null, null, null);
    }
}
