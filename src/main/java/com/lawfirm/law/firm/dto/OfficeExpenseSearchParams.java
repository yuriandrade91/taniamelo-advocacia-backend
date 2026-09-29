package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * Filtros da lista de despesas do escritório.
 *
 * <p><b>Competência e caixa não se misturam numa chamada.</b> {@code dueFrom}/{@code dueTo}
 * recortam por vencimento ("o que vence no mês"); {@code paidFrom}/{@code paidTo}, por data de
 * pagamento ("o que entrou e saiu no mês"). Mandar os dois pares responde 400, em vez de o servidor
 * escolher um: somar recebimento por vencimento e apresentar como "entrou" conta dinheiro que não
 * chegou, e o erro é silencioso — os números parecem plausíveis e só aparecem quando alguém confere
 * com o extrato.
 *
 * <p>Listas aceitam o parâmetro repetido, sem colchetes ({@code ?status=Pendente&status=Pago}).
 */
public class OfficeExpenseSearchParams {

    @Schema(description = "Número da página (1-based)", defaultValue = "1")
    private int pageNumber = 1;

    @Schema(description = "Tamanho da página (máximo 100)", defaultValue = "10")
    private int pageSize = 10;

    @Schema(description = "Início do intervalo de VENCIMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate dueFrom;

    @Schema(description = "Fim do intervalo de VENCIMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate dueTo;

    @Schema(description = "Início do intervalo de PAGAMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate paidFrom;

    @Schema(description = "Fim do intervalo de PAGAMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate paidTo;

    @Schema(description = "Situação(ões): nome da constante ou rótulo")
    private List<String> status;

    @Schema(description = "Forma(s) de pagamento: nome da constante ou rótulo")
    private List<String> paymentMethod;

    @Schema(description = "Busca na descrição ou no fornecedor")
    private String searchTerm;

    @Schema(description = "Categoria(s): nome da constante ou rótulo")
    private List<String> category;

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public LocalDate getDueFrom() {
        return dueFrom;
    }

    public void setDueFrom(LocalDate dueFrom) {
        this.dueFrom = dueFrom;
    }

    public LocalDate getDueTo() {
        return dueTo;
    }

    public void setDueTo(LocalDate dueTo) {
        this.dueTo = dueTo;
    }

    public LocalDate getPaidFrom() {
        return paidFrom;
    }

    public void setPaidFrom(LocalDate paidFrom) {
        this.paidFrom = paidFrom;
    }

    public LocalDate getPaidTo() {
        return paidTo;
    }

    public void setPaidTo(LocalDate paidTo) {
        this.paidTo = paidTo;
    }

    public List<String> getStatus() {
        return status;
    }

    public void setStatus(List<String> status) {
        this.status = status;
    }

    public List<String> getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(List<String> paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public List<String> getCategory() {
        return category;
    }

    public void setCategory(List<String> category) {
        this.category = category;
    }
}
