package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.model.ClientPayment;

/**
 * A linha da lista de parcelas, uma vez só, para as duas rotas que listam a mesma tabela.
 *
 * <p>{@code GET /payments} e {@code GET /clients/{clientId}/payments} já compartilham a consulta
 * ({@link PaymentSpecs}) e a ordem. Faltava a projeção: a aba do cliente devolvia o DTO do detalhe,
 * então as duas respondiam formatos diferentes sobre a mesma parcela e a aba não tinha o que o
 * detalhe dela detalhasse.
 *
 * <p>Mora numa classe própria, e não como {@code static} dentro de {@code PaymentService}, pelo
 * mesmo motivo que {@link DueDateRules} saiu de {@code OfficeExpenseService}: um service hospedando
 * o método que o outro chama sugere que a regra é particularidade de quem hospeda e que o outro a
 * toma emprestada. Ela não é de nenhum dos dois — é da parcela.
 */
final class PaymentProjections {

    private PaymentProjections() {}

    static PaymentListItemDTO toListItem(ClientPayment entity) {
        PaymentListItemDTO dto = new PaymentListItemDTO();
        dto.setId(entity.getId());
        if (entity.getClient() != null) {
            dto.setClientId(entity.getClient().getId());
            dto.setClientName(entity.getClient().getFullName());
        }
        dto.setDescription(entity.getDescription());
        dto.setInstallmentNumber(entity.getInstallmentNumber());
        dto.setInstallmentTotal(entity.getInstallmentTotal());
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setPaymentMethod(
                entity.getPaymentMethod() != null ? entity.getPaymentMethod().getLabel() : null);
        dto.setAmount(entity.getAmount());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setOverdue(DueDateRules.isOverdue(entity.getStatus(), entity.getDueDate()));
        return dto;
    }
}
