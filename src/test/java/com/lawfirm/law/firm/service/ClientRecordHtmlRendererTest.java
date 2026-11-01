package com.lawfirm.law.firm.service;

import static com.lawfirm.law.firm.service.ClientRecordFixtures.emptyRecord;
import static com.lawfirm.law.firm.service.ClientRecordFixtures.fullRecord;
import static com.lawfirm.law.firm.service.ClientRecordFixtures.interview;
import static com.lawfirm.law.firm.service.ClientRecordFixtures.pagesOf;
import static com.lawfirm.law.firm.service.ClientRecordFixtures.recordWith;
import static com.lawfirm.law.firm.service.ClientRecordFixtures.textOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.dto.ClientRequestStageHistoryDTO;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ficha do cliente em PDF: conteúdo e paginação")
class ClientRecordHtmlRendererTest {

    @Test
    @DisplayName(
            "imprime os dados com acento, rótulo de enum, nome do usuário e hora do escritório")
    void printsReadableData() throws IOException {
        String text =
                textOf(
                        ClientRecordHtmlRenderer.render(
                                recordWith(
                                        List.of(interview("Relato da entrevista")),
                                        List.of(),
                                        false,
                                        List.of())));

        assertTrue(text.contains("Conceição Araújo"), text);
        assertTrue(text.contains("529.982.247-25"), text);
        assertTrue(text.contains("09/03/1960 (66 anos)"), text);
        assertTrue(text.contains("Feminino"), text);
        assertTrue(text.contains("Rua das Acácias, 10"), text);
        assertTrue(text.contains("São Paulo/SP"), text);
        assertTrue(text.contains("Dra. Responsável"), text);
        // 17:30 UTC é 14:30 em São Paulo.
        assertTrue(text.contains("15/01/2026 14:30 - 30 min"), text);
        assertTrue(text.contains("Relato da entrevista"), text);
        assertTrue(text.contains("Página 1 de 2"), text);
        assertFalse(text.contains("de 3"), text);
    }

    @Test
    @DisplayName("sem permissão, a seção de pagamentos não aparece nem como vazia")
    void omitsPaymentsWhenNotVisible() throws IOException {
        String text =
                textOf(
                        ClientRecordHtmlRenderer.render(
                                recordWith(List.of(), List.of(), false, List.of())));

        assertFalse(text.contains("Pagamentos"), text);
    }

    @Test
    @DisplayName("com permissão, as parcelas saem com valor em reais")
    void printsPaymentsWhenVisible() throws IOException {
        PaymentListItemDTO payment = new PaymentListItemDTO();
        payment.setDescription("Retroativo");
        payment.setInstallmentNumber(2);
        payment.setInstallmentTotal(6);
        payment.setAmount(new BigDecimal("1234.50"));
        payment.setDueDate(LocalDate.of(2026, 12, 1));
        payment.setStatus("Pendente");

        String text =
                textOf(
                                ClientRecordHtmlRenderer.render(
                                        recordWith(List.of(), List.of(), true, List.of(payment))))
                        .replace(' ', ' ');

        assertTrue(text.contains("Pagamentos a receber do INSS"), text);
        assertTrue(text.contains("2/6"), text);
        assertTrue(text.contains("1.234,50"), text);
    }

    @Test
    @DisplayName("texto longo quebra página sem perder o fim, nem em parágrafo nem em tabela")
    void longContentFlowsAcrossPages() throws IOException {
        String longText = "palavra ".repeat(6000) + "FIM-ENTREVISTA";
        ClientRequestStageHistoryDTO stage = new ClientRequestStageHistoryDTO();
        stage.setChangedAt(Instant.parse("2026-01-15T17:30:00Z"));
        stage.setCurrentStage("Exigência");
        stage.setJustification("motivo ".repeat(2000) + "FIM-JUSTIFICATIVA");

        byte[] pdf =
                ClientRecordHtmlRenderer.render(
                        recordWith(List.of(interview(longText)), List.of(stage), false, List.of()));

        assertTrue(pagesOf(pdf) > 2);
        String text = textOf(pdf);
        assertTrue(text.contains("FIM-ENTREVISTA"));
        assertTrue(text.contains("FIM-JUSTIFICATIVA"));
    }

    @Test
    @DisplayName("muitas linhas repetem o cabeçalho da tabela em cada página")
    void repeatsTableHeader() throws IOException {
        List<ClientRequestStageHistoryDTO> stages = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            ClientRequestStageHistoryDTO stage = new ClientRequestStageHistoryDTO();
            stage.setCurrentStage("Recurso");
            stages.add(stage);
        }

        byte[] pdf =
                ClientRecordHtmlRenderer.render(recordWith(List.of(), stages, false, List.of()));

        // A tabela começa depois da primeira página, e dali em diante aparece em todas.
        int pages = pagesOf(pdf);
        assertTrue(pages > 2);
        assertEquals(pages - 1, textOf(pdf).split("Justificativa", -1).length - 1);
    }

    @Test
    @DisplayName("caractere fora do Latin-1 não derruba a geração")
    void survivesCharactersOutsideTheBuiltInFont() throws IOException {
        byte[] pdf =
                ClientRecordHtmlRenderer.render(
                        recordWith(
                                List.of(interview("antes → 😀 日本語 depois")),
                                List.of(),
                                false,
                                List.of()));

        String text = textOf(pdf);
        assertTrue(text.contains("antes"), text);
        assertTrue(text.contains("depois"), text);
    }

    @Test
    @DisplayName("ficha completa imprime todas as seções, com e sem as parcelas")
    void printsEverySectionOfAFullRecord() throws IOException {
        // Célula estreita quebra "Motivo do / cancelamento" em duas linhas: compara sem a quebra.
        String text =
                textOf(ClientRecordHtmlRenderer.render(fullRecord(true))).replaceAll("\\s+", " ");

        for (String expected :
                List.of(
                        "Dados pessoais",
                        "Endereços",
                        "Dados profissionais e previdenciários",
                        "Períodos de deficiência",
                        "Até a presente data",
                        "Entrevistas",
                        "primeiro item",
                        "citação",
                        "Documentos anexados",
                        "Simulações",
                        "Compromissos",
                        "Motivo do cancelamento",
                        "Histórico de situação no escritório",
                        "Histórico de etapas no INSS",
                        "Pagamentos a receber do INSS",
                        "Vencido",
                        "1.234,50")) {
            assertTrue(text.contains(expected), expected + " não saiu em:\n" + text);
        }
        assertFalse(
                textOf(ClientRecordHtmlRenderer.render(fullRecord(false)))
                        .contains("Pagamentos a receber"));
    }

    @Test
    @DisplayName("ficha sem nenhum dado ainda sai, com os textos de registro vazio")
    void printsAnEmptyRecord() throws IOException {
        String text = textOf(ClientRecordHtmlRenderer.render(emptyRecord(true)));

        assertTrue(text.contains("Nenhum registro"), text);
        assertTrue(text.contains("Pagamentos a receber do INSS"), text);
    }
}
