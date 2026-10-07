package com.lawfirm.law.firm.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * As listas de despesa e receita carregam só as colunas da grade.
 *
 * <p>Este teste existe porque a redundância que ele barra já aconteceu: as duas rotas de lista
 * devolviam o DTO do detalhe, e cada linha vinha com observação, forma de pagamento e as quatro
 * colunas de autoria que nenhuma coluna da tela usa. O caminho de volta é fácil — alguém precisa de
 * um campo numa tela, acrescenta no DTO de lista porque é onde está mexendo, e em duas semanas a
 * lista é o detalhe outra vez.
 *
 * <p>Então a asserção é sobre o conjunto <b>exato</b> de campos, não sobre "contém". Campo novo na
 * grade quebra aqui de propósito: é para a decisão de alargar a lista ser tomada, e não acontecer.
 */
@DisplayName("Projeção das listas de despesa, receita e parcelas")
class FinanceListProjectionTest {

    @Test
    @DisplayName("despesa na grade: descrição, fornecedor, categoria, datas, valor e status")
    void despesaSoAsColunasDaGrade() {
        assertEquals(
                List.of(
                        "id",
                        "description",
                        "supplier",
                        "category",
                        "dueDate",
                        "paidDate",
                        "amount",
                        "status",
                        "overdue"),
                camposDe(OfficeExpenseListItemDTO.class));
    }

    @Test
    @DisplayName("receita na grade: descrição, cliente, datas, valor e status")
    void receitaSoAsColunasDaGrade() {
        assertEquals(
                List.of(
                        "id",
                        "description",
                        "clientName",
                        "dueDate",
                        "paidDate",
                        "amount",
                        "status",
                        "overdue"),
                camposDe(OfficeRevenueListItemDTO.class));
    }

    @Test
    @DisplayName("parcela na grade: cliente, descrição, parcela, datas, forma, valor e status")
    void parcelaSoAsColunasDaGrade() {
        assertEquals(
                List.of(
                        "id",
                        "clientId",
                        "clientName",
                        "description",
                        "installmentNumber",
                        "installmentTotal",
                        "dueDate",
                        "paidDate",
                        "paymentMethod",
                        "amount",
                        "status",
                        "overdue"),
                camposDe(PaymentListItemDTO.class));
    }

    /**
     * As duas rotas que listam parcelas usam a MESMA projeção. Se um dia divergirem, a aba do
     * cliente volta a responder diferente da lista geral sobre a mesma parcela — que é o que {@code
     * PaymentProjections} existe para impedir.
     */
    @Test
    @DisplayName("a aba do cliente não devolve mais o DTO do detalhe")
    void abaDoClienteNaoUsaODtoDoDetalhe() {
        // O detalhe tem autoria; a grade, não. Se os dois conjuntos ficarem iguais, a distinção
        // entre listar e detalhar sumiu de novo.
        assertEquals(true, camposDe(ClientPaymentResponseDTO.class).contains("createdBy"));
        assertEquals(false, camposDe(PaymentListItemDTO.class).contains("createdBy"));
        assertEquals(false, camposDe(PaymentListItemDTO.class).contains("notes"));
    }

    /**
     * O detalhe continua sendo o lugar do resto. Se um destes campos aparecer na lista, as duas
     * rotas voltaram a responder a mesma coisa.
     */
    @Test
    @DisplayName("o que é do detalhe não vaza para a lista")
    void detalheNaoVazaParaALista() {
        for (String doDetalhe :
                List.of(
                        "notes",
                        "createdBy",
                        "createdAt",
                        "updatedBy",
                        "updatedAt",
                        "paymentMethod")) {
            assertEquals(
                    false,
                    camposDe(OfficeExpenseListItemDTO.class).contains(doDetalhe),
                    doDetalhe + " é do detalhe da despesa, não da grade");
            assertEquals(
                    false,
                    camposDe(OfficeRevenueListItemDTO.class).contains(doDetalhe),
                    doDetalhe + " é do detalhe da receita, não da grade");
        }
        // sourcePaymentId e clientId são só da receita, e são só do detalhe dela.
        assertEquals(false, camposDe(OfficeRevenueListItemDTO.class).contains("sourcePaymentId"));
        assertEquals(false, camposDe(OfficeRevenueListItemDTO.class).contains("clientId"));
    }

    /** Na ordem de declaração, que é a ordem em que o Jackson serializa. */
    private static List<String> camposDe(Class<?> dto) {
        return java.util.Arrays.stream(dto.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .toList();
    }
}
