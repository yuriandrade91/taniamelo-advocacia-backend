package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.dto.ClientRequestStageHistoryDTO;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.support.TestFixtures;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

@DisplayName("Ficha do cliente em PDF: conteúdo e paginação")
class ClientRecordPdfRendererTest {

    private static ClientDetailsDTO client() {
        ClientDetailsDTO client = new ClientDetailsDTO();
        client.setClientId(TestFixtures.CLIENT_ID);
        client.setFullName("Conceição Araújo");
        client.setCpf("529.982.247-25");
        client.setBirthDate(LocalDate.of(1960, 3, 9));
        client.setAge(66);
        client.setGender(Gender.FEMININO);
        client.setResponsibleUserId(TestFixtures.USER_ID);
        return client;
    }

    private static ClientRecord recordWith(
            List<ClientInterviewResponseDTO> interviews,
            List<ClientRequestStageHistoryDTO> stages,
            boolean paymentsVisible,
            List<PaymentListItemDTO> payments) {
        ClientAddressResponseDTO address = new ClientAddressResponseDTO();
        address.setStreet("Rua das Acácias");
        address.setAddressNumber("10");
        address.setCity("São Paulo");
        address.setState("SP");
        address.setIsPrimary(true);
        return new ClientRecord(
                "Escritório Teste",
                Instant.parse("2026-10-30T12:00:00Z"),
                client(),
                List.of(address),
                List.of(),
                interviews,
                List.of(),
                List.of(),
                List.of(),
                stages,
                List.of(),
                paymentsVisible,
                payments,
                Map.of(TestFixtures.USER_ID, "Dra. Responsável"));
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }

    private static int pagesOf(byte[] pdf) throws IOException {
        try (PdfReader reader = new PdfReader(pdf)) {
            return reader.getNumberOfPages();
        }
    }

    private static ClientInterviewResponseDTO interview(String content) {
        ClientInterviewResponseDTO interview = new ClientInterviewResponseDTO();
        interview.setOccurredAt(Instant.parse("2026-01-15T17:30:00Z"));
        interview.setDurationMinutes(30);
        interview.setContent(content);
        interview.setCreatedBy(TestFixtures.USER_ID);
        return interview;
    }

    @Test
    @DisplayName(
            "imprime os dados com acento, rótulo de enum, nome do usuário e hora do escritório")
    void printsReadableData() throws IOException {
        String text =
                textOf(
                        ClientRecordPdfRenderer.render(
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
                        ClientRecordPdfRenderer.render(
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
                                ClientRecordPdfRenderer.render(
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
                ClientRecordPdfRenderer.render(
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
                ClientRecordPdfRenderer.render(recordWith(List.of(), stages, false, List.of()));

        // A tabela começa depois da primeira página, e dali em diante aparece em todas.
        int pages = pagesOf(pdf);
        assertTrue(pages > 2);
        assertEquals(pages - 1, textOf(pdf).split("Justificativa", -1).length - 1);
    }

    @Test
    @DisplayName("caractere fora do Latin-1 não derruba a geração")
    void survivesCharactersOutsideTheBuiltInFont() throws IOException {
        byte[] pdf =
                ClientRecordPdfRenderer.render(
                        recordWith(
                                List.of(interview("antes → 😀 日本語 depois")),
                                List.of(),
                                false,
                                List.of()));

        String text = textOf(pdf);
        assertTrue(text.contains("antes"), text);
        assertTrue(text.contains("depois"), text);
    }
}
