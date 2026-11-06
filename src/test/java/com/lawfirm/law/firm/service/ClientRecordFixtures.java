package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.AppointmentResponseDTO;
import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientDisabilityPeriodResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationResponseDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.dto.ClientRequestStageHistoryDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.DisabilityGrade;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.support.TestFixtures;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Fichas de exemplo para os testes do desenho da ficha em PDF: uma vazia, uma completa (todas as
 * seções e campos, inclusive os que saem como travessão) e uma enxuta, montada à vontade.
 */
final class ClientRecordFixtures {

    private static final Instant WHEN = Instant.parse("2026-01-15T17:30:00Z");

    private ClientRecordFixtures() {}

    /**
     * O texto de todas as páginas, com o espaço não separável (que o PDF usa) como espaço comum.
     */
    static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            // Pela posição na página: o "Página N de T" do rodapé é desenhado em pedaços.
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document).replace('\u00a0', ' ');
        }
    }

    static int pagesOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    static ClientDetailsDTO client() {
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

    static ClientRecord recordWith(
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

    static ClientInterviewResponseDTO interview(String content) {
        ClientInterviewResponseDTO interview = new ClientInterviewResponseDTO();
        interview.setOccurredAt(WHEN);
        interview.setDurationMinutes(30);
        interview.setContent(content);
        interview.setCreatedBy(TestFixtures.USER_ID);
        return interview;
    }

    /** Ficha sem nada: toda seção cai no texto de "nenhum registro" e cada campo no travessão. */
    static ClientRecord emptyRecord(boolean paymentsVisible) {
        return new ClientRecord(
                "Escritório Teste",
                Instant.parse("2026-10-30T12:00:00Z"),
                new ClientDetailsDTO(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                paymentsVisible,
                List.of(),
                Map.of());
    }

    /** Ficha com todos os campos e todas as seções preenchidas, e uma linha "vazia" em cada. */
    static ClientRecord fullRecord(boolean paymentsVisible) {
        ClientDetailsDTO client = client();
        client.setRg("12.345.678-9");
        client.setRgIssuer("SSP/SP");
        client.setRgIssueDate(LocalDate.of(1990, 5, 20));
        client.setMotherName("Maria Araújo");
        client.setMaritalStatus(MaritalStatus.CASADO);
        client.setNationality("Brasileira");
        client.setHasDisability(true);
        client.setEmail("conceicao@example.com");
        client.setMobilePhone("(11) 91234-5678");
        client.setIsWhatsapp(true);
        client.setReferencePhone("(11) 3456-7890");
        client.setReferenceResponsible("Filha, Ana");
        client.setClientType(ClientType.VERIFICADO);
        client.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        client.setSituation(Situation.PLANEJAMENTO_EM_EXECUCAO);
        client.setRequestStage(RequestStage.EXIGENCIA);
        client.setBeneficiaryNumber("123.456.789-0");
        client.setNitPis("123.45678.90-1");
        client.setProfession("Costureira");
        client.setCtps("123456");
        client.setCtpsSeries("0012");
        client.setContributionTime("25 anos, 3 meses e 10 dias");
        client.setNotBillable(false);
        client.setNotes("Prefere contato à tarde & por WhatsApp <sempre>");
        client.setCreatedAt(WHEN);

        ClientAddressResponseDTO home = new ClientAddressResponseDTO();
        home.setAddressType("Residencial");
        home.setStreet("Rua das Acácias");
        home.setAddressNumber("10");
        home.setComplement("Apto 4");
        home.setNeighborhood("Centro");
        home.setCity("São Paulo");
        home.setState("SP");
        home.setZipCode("01001-000");
        home.setIsPrimary(true);
        ClientAddressResponseDTO bare = new ClientAddressResponseDTO();
        bare.setIsPrimary(false);

        ClientDisabilityPeriodResponseDTO ended = new ClientDisabilityPeriodResponseDTO();
        ended.setGrade(DisabilityGrade.GRAVE);
        ended.setStartedOn(LocalDate.of(2010, 1, 1));
        ended.setEndedOn(LocalDate.of(2012, 12, 31));
        ended.setDays(1096);
        ClientDisabilityPeriodResponseDTO ongoing = new ClientDisabilityPeriodResponseDTO();
        ongoing.setGrade(DisabilityGrade.LEVE);
        ongoing.setStartedOn(LocalDate.of(2020, 1, 1));
        ongoing.setOngoing(true);
        ongoing.setDays(2000);

        ClientInterviewResponseDTO formatted =
                interview(
                        "<p>Relato <strong>importante</strong> e <em>urgente</em></p>"
                                + "<ul><li>primeiro item</li><li>segundo item</li></ul>"
                                + "<ol><li>passo um</li></ol><blockquote>citação</blockquote>");
        ClientInterviewResponseDTO plain = interview("Texto simples\nem duas linhas");
        plain.setDurationMinutes(null);
        plain.setCreatedBy(null);

        ClientFileDocumentResponseDTO document = new ClientFileDocumentResponseDTO();
        document.setDocumentType("RG");
        document.setOriginalFilename("rg.pdf");
        document.setUploadedAt(WHEN);
        document.setNotes("frente e verso");

        ClientFileSimulationResponseDTO simulation = new ClientFileSimulationResponseDTO();
        simulation.setOriginalFilename("simulacao.pdf");
        simulation.setSimulationDate(LocalDate.of(2026, 2, 1));
        simulation.setVersion("v2");
        simulation.setVinculos(4);
        simulation.setIsPrincipal(true);
        simulation.setNotes("melhor cenário");

        ClientSituationHistoryDTO situation = new ClientSituationHistoryDTO();
        situation.setChangedAt(WHEN);
        situation.setPreviousSituation("Formulário preenchido");
        situation.setCurrentSituation("Análise documental");
        situation.setChangedByUserId(TestFixtures.USER_ID);

        ClientRequestStageHistoryDTO stage = new ClientRequestStageHistoryDTO();
        stage.setChangedAt(WHEN);
        stage.setPreviousStage("Requerimento");
        stage.setCurrentStage("Exigência");
        stage.setDiasNaEtapaAnterior(12L);
        stage.setJustification("INSS pediu comprovante de residência");
        stage.setChangedByUserId(TestFixtures.USER_ID);

        AppointmentResponseDTO scheduled = new AppointmentResponseDTO();
        scheduled.setStartAt(WHEN);
        scheduled.setTitle("Perícia médica");
        scheduled.setType("Perícia");
        scheduled.setModality("Presencial");
        scheduled.setLocation("Agência Sé");
        scheduled.setStatus("Agendado");
        scheduled.setDescription("Levar laudos");
        AppointmentResponseDTO cancelled = new AppointmentResponseDTO();
        cancelled.setStartAt(WHEN);
        cancelled.setTitle("Reunião");
        cancelled.setStatus("Cancelado");
        cancelled.setDescription("Revisar documentos");
        cancelled.setCancellationReason("Cliente doente");

        PaymentListItemDTO paid = new PaymentListItemDTO();
        paid.setDescription("Retroativo");
        paid.setInstallmentNumber(1);
        paid.setInstallmentTotal(6);
        paid.setDueDate(LocalDate.of(2026, 11, 1));
        paid.setPaidDate(LocalDate.of(2026, 11, 3));
        paid.setPaymentMethod("PIX");
        paid.setAmount(new BigDecimal("1234.50"));
        paid.setStatus("Pago");
        PaymentListItemDTO overdue = new PaymentListItemDTO();
        overdue.setDescription("Honorários");
        overdue.setInstallmentNumber(2);
        overdue.setInstallmentTotal(6);
        overdue.setDueDate(LocalDate.of(2026, 12, 1));
        overdue.setAmount(new BigDecimal("500"));
        overdue.setStatus("Pendente");
        overdue.setOverdue(true);

        return new ClientRecord(
                "Escritório Teste",
                Instant.parse("2026-10-30T12:00:00Z"),
                client,
                List.of(home, bare),
                List.of(ended, ongoing),
                List.of(formatted, plain),
                List.of(document, new ClientFileDocumentResponseDTO()),
                List.of(simulation, new ClientFileSimulationResponseDTO()),
                List.of(situation),
                List.of(stage),
                List.of(scheduled, cancelled),
                paymentsVisible,
                List.of(paid, overdue, new PaymentListItemDTO()),
                Map.of(TestFixtures.USER_ID, "Dra. Responsável"));
    }
}
