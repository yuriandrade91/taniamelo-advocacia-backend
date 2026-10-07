package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.model.ClientPayment;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.FinanceSpecifications;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * A busca de parcelas, uma vez só, para as duas rotas que listam a mesma tabela.
 *
 * <p>{@code GET /payments} (o escritório inteiro) e {@code GET /clients/{id}/payments} (a aba da
 * ficha) leem {@code client_payments} e respondem à mesma pergunta com recortes diferentes. Cada
 * uma tinha a sua consulta: a aninhada só paginava, sem nenhum filtro. Não é que faltasse recurso —
 * é que o próximo filtro entraria num lado só, e a aba do cliente ficaria respondendo diferente da
 * lista geral sobre a mesma parcela.
 *
 * <p>A <b>ordem</b> também é uma só, e viaja dentro da especificação em vez de num {@code Sort}
 * entregue ao {@code Pageable}: ela depende de um {@code CASE} sobre o status, que {@code Sort} não
 * expressa — ver {@code FinanceSpecifications.orderByStatusThenDueDate}. Quem monta o {@code
 * Pageable} destas consultas não passa ordenação; se passar, o Spring Data troca a da especificação
 * pela dele e a lista volta a ignorar o status.
 *
 * <p>O que continua separado é a <b>projeção</b>: a lista geral devolve o nome do cliente em cada
 * linha ({@code PaymentListItemDTO}) porque junta clientes; a aninhada devolve a parcela inteira
 * com autoria ({@code ClientPaymentResponseDTO}) porque o cliente já é a URL. Consulta igual,
 * resposta diferente — e é essa a divisão certa.
 */
public final class PaymentSpecs {

    private PaymentSpecs() {}

    /**
     * Monta a especificação e, de quebra, recusa competência e caixa na mesma chamada. A validação
     * mora aqui de propósito: quem esquecer de chamar {@code PeriodBasis} direto no serviço ainda
     * assim não consegue montar a consulta ambígua.
     */
    public static Specification<ClientPayment> of(PaymentSearchParams params) {
        PeriodBasis.requireSingleRange(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        return Specification.allOf(
                activeOnly(),
                FinanceSpecifications.<ClientPayment>dueBetween(
                        params.getDueFrom(), params.getDueTo()),
                FinanceSpecifications.<ClientPayment>paidBetween(
                        params.getPaidFrom(), params.getPaidTo()),
                FinanceSpecifications.<ClientPayment>statusIn(
                        RequestEnums.list("status", params.getStatus(), PaymentStatus::fromLabel)),
                FinanceSpecifications.<ClientPayment>methodIn(
                        RequestEnums.list(
                                "paymentMethod",
                                params.getPaymentMethod(),
                                PaymentMethod::fromLabel)),
                forClient(params.getClientId()),
                FinanceSpecifications.<ClientPayment>textoEm(
                        params.getSearchTerm(), "description", "client.fullName"),
                FinanceSpecifications.<ClientPayment>orderByStatusThenDueDate());
    }

    /**
     * {@code ClientPayment} não tem {@code @SQLRestriction} — o filtro de excluídos é explícito em
     * cada consulta. Sem ele, a lista mostraria parcela apagada, e só ela.
     */
    private static Specification<ClientPayment> activeOnly() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static Specification<ClientPayment> forClient(UUID clientId) {
        return (root, query, cb) ->
                clientId == null ? null : cb.equal(root.get("client").get("id"), clientId);
    }
}
