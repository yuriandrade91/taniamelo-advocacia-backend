package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.util.OfficeClock;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Desenha o {@link ClientRecord} em PDF a partir de HTML e CSS (openhtmltopdf). Só layout: não
 * consulta nada nem decide permissão.
 *
 * <p>O desenho mora no CSS ({@link #STYLE}); o Java só monta a marcação. Paginação, cabeçalho de
 * tabela repetido e "Página N de T" são do CSS de mídia paginada.
 */
final class ClientRecordHtmlRenderer {

    private static final String EMPTY_VALUE = "—";
    private static final String NO_RECORDS = "Nenhum registro.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(OfficeClock.ZONE);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static final String STYLE =
            """
            @page {
              size: A4;
              margin: 44pt 48pt 64pt 48pt;
              @bottom-center { content: element(footer); vertical-align: top; }
            }
            * { box-sizing: border-box; }
            body {
              font-family: Helvetica, sans-serif;
              font-size: 9.5pt;
              line-height: 1.45;
              color: #20242c;
              margin: 0;
            }
            #footer {
              position: running(footer);
              width: 100%;
              border-top: 0.6pt solid #e1e4e9;
              padding-top: 6pt;
              font-size: 7.5pt;
              color: #6c727c;
            }
            #footer .right { float: right; }
            #footer .page::after { content: counter(page); }
            #footer .pages::after { content: counter(pages); }

            .top { font-size: 8.5pt; color: #6c727c; }
            .top .right { float: right; }
            h1 { font-size: 20pt; color: #1f3864; margin: 12pt 0 2pt 0; line-height: 1.2; }
            .meta { font-size: 8.5pt; color: #6c727c; margin: 0; }

            .summary { width: 100%; margin-top: 14pt; border-collapse: collapse; }
            .summary td { background: #f4f6f9; padding: 9pt 10pt; vertical-align: top; }
            .summary .value { font-weight: bold; font-size: 9.5pt; line-height: 1.3; }

            h2 {
              font-size: 11pt;
              color: #1f3864;
              margin: 20pt 0 2pt 0;
              page-break-after: avoid;
            }
            .label { font-size: 7.5pt; color: #6c727c; line-height: 1.2; margin-bottom: 2pt; }
            .value { font-size: 10pt; line-height: 1.3; }
            .empty { color: #aaafb7; }

            .fields { width: 100%; border-collapse: collapse; }
            .fields td {
              width: 50%;
              vertical-align: top;
              padding: 6pt 16pt 7pt 0;
              border-bottom: 0.6pt solid #e1e4e9;
            }
            .fields td + td { padding-right: 0; }
            .fields tr, .records tr { page-break-inside: avoid; }

            .records { width: 100%; border-collapse: collapse; margin-top: 8pt; -fs-table-paginate: paginate; }
            .records th {
              background: #f4f6f9;
              color: #6c727c;
              font-size: 7.5pt;
              font-weight: bold;
              text-align: left;
              padding: 6pt;
              line-height: 1.3;
              vertical-align: bottom;
            }
            .records td {
              font-size: 9pt;
              line-height: 1.35;
              padding: 6pt;
              vertical-align: top;
              border-bottom: 0.6pt solid #e1e4e9;
            }
            .none { font-size: 9pt; font-style: italic; color: #6c727c; margin: 6pt 0 0 0; }

            .block-title { font-size: 8.5pt; font-weight: bold; margin: 10pt 0 2pt 0; }
            .interview + .interview { border-top: 0.6pt solid #e1e4e9; margin-top: 8pt; }
            .rich p { margin: 3pt 0 0 0; }
            .rich ul, .rich ol { margin: 3pt 0 0 0; padding-left: 16pt; }
            /* Marcador próprio: o disco padrão vem da fonte Symbol, que o PDFBox troca por
               outra e ainda varre as fontes do sistema para achar. */
            .rich ul { list-style: none; }
            .rich ul > li::before { content: "•"; display: inline-block; width: 10pt; margin-left: -10pt; }
            .rich li { margin: 2pt 0 0 0; }
            .rich li p { margin: 0; }
            .rich blockquote { margin: 3pt 0 0 0; padding-left: 10pt; color: #6c727c; font-style: italic; }
            """;

    private final StringBuilder html = new StringBuilder();

    private ClientRecordHtmlRenderer() {}

    static byte[] render(ClientRecord clientRecord) {
        ClientRecordHtmlRenderer renderer = new ClientRecordHtmlRenderer();
        renderer.addDocument(clientRecord);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(renderer.html.toString(), null);
            builder.toStream(output);
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output.toByteArray();
    }

    private void addDocument(ClientRecord clientRecord) {
        ClientDetailsDTO client = clientRecord.client();
        html.append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"/>")
                .append("<title>Ficha do cliente</title><style>")
                .append(STYLE)
                .append("</style></head><body>");
        // O rodapé corrente vem antes de tudo: o CSS o leva para a margem de cada página.
        html.append("<div id=\"footer\"><span class=\"right\">Página <span class=\"page\"></span>")
                .append(" de <span class=\"pages\"></span></span>")
                .append(escape(client.getFullName()))
                .append(" · Ficha do cliente · Documento sigiloso</div>");

        addHeader(clientRecord);
        addPersonalData(client);
        addAddresses(clientRecord);
        addCase(clientRecord);
        addDisabilityPeriods(clientRecord);
        addInterviews(clientRecord);
        addFiles(clientRecord);
        addAppointments(clientRecord);
        addHistory(clientRecord);
        if (clientRecord.paymentsVisible()) {
            addPayments(clientRecord);
        }
        html.append("</body></html>");
    }

    private void addHeader(ClientRecord clientRecord) {
        ClientDetailsDTO client = clientRecord.client();
        html.append("<div class=\"top\"><span class=\"right\">Ficha do cliente · gerada em ")
                .append(dateTime(clientRecord.generatedAt()))
                .append("</span>")
                .append(escape(clientRecord.officeName()))
                .append("</div><h1>")
                .append(escape(client.getFullName()))
                .append(
                        "</h1><p class=\"meta\">Documento sigiloso. Uso interno do escritório.</p>");
        html.append("<table class=\"summary\"><tr>");
        summaryCell("CPF", text(client.getCpf()), "21%");
        summaryCell("Benefício", text(client.getBenefit()), "31%");
        summaryCell("Situação no escritório", text(client.getSituation()), "27%");
        summaryCell("Etapa no INSS", stageOf(client), "21%");
        html.append("</tr></table>");
    }

    private void summaryCell(String label, String value, String width) {
        html.append("<td style=\"width:")
                .append(width)
                .append("\"><div class=\"label\">")
                .append(escape(label))
                .append("</div><div class=\"value\">")
                .append(escape(value))
                .append("</div></td>");
    }

    private void addPersonalData(ClientDetailsDTO client) {
        addSection("Dados pessoais");
        String birthDate = date(client.getBirthDate());
        if (client.getAge() != null) {
            birthDate += " (" + client.getAge() + " anos)";
        }
        addFields(
                field("Nome completo", client.getFullName()),
                field("Data de nascimento", birthDate),
                field("CPF", client.getCpf()),
                field("RG", client.getRg()),
                field("Órgão emissor do RG", client.getRgIssuer()),
                field("Emissão do RG", date(client.getRgIssueDate())),
                field("Nome da mãe", client.getMotherName()),
                field("Gênero", client.getGender()),
                field("Estado civil", client.getMaritalStatus()),
                field("Nacionalidade", client.getNationality()),
                field("Pessoa com deficiência", yesNo(client.getHasDisability())),
                field("E-mail", client.getEmail()),
                field("Celular", client.getMobilePhone()),
                field("Celular é WhatsApp", yesNo(client.getIsWhatsapp())),
                field("Telefone de referência", client.getReferencePhone()),
                field("Responsável pela referência", client.getReferenceResponsible()));
    }

    private void addAddresses(ClientRecord clientRecord) {
        addSection("Endereços");
        addTable(
                new float[] {2, 6, 3, 1.5f},
                List.of("Tipo", "Endereço", "Cidade/UF", "Principal"),
                clientRecord.addresses().stream()
                        .map(
                                address ->
                                        List.of(
                                                text(address.getAddressType()),
                                                addressLine(address),
                                                join("/", address.getCity(), address.getState()),
                                                yesNo(address.getIsPrimary())))
                        .toList());
    }

    private void addCase(ClientRecord clientRecord) {
        ClientDetailsDTO client = clientRecord.client();
        addSection("Dados profissionais e previdenciários");
        addFields(
                field("Tipo de cliente", client.getClientType()),
                field("Benefício", client.getBenefit()),
                field("Situação no escritório", client.getSituation()),
                field("Etapa no INSS", stageOf(client)),
                field("Número do benefício", client.getBeneficiaryNumber()),
                field("NIT/PIS", client.getNitPis()),
                field("Profissão", client.getProfession()),
                field("CTPS", join(" série ", client.getCtps(), client.getCtpsSeries())),
                field("Tempo de contribuição", client.getContributionTime()),
                field("Sem arrecadação", yesNo(client.getNotBillable())),
                field("Responsável", userName(clientRecord, client.getResponsibleUserId())),
                field("Cadastrado em", dateTime(client.getCreatedAt())));
        if (hasText(client.getNotes())) {
            html.append("<p class=\"block-title\">Observações</p><div class=\"rich\"><p>")
                    .append(escape(client.getNotes()))
                    .append("</p></div>");
        }
    }

    private void addDisabilityPeriods(ClientRecord clientRecord) {
        addSection("Períodos de deficiência");
        addTable(
                new float[] {3, 2, 2, 1.5f},
                List.of("Grau", "Início", "Fim", "Dias"),
                clientRecord.disabilityPeriods().stream()
                        .map(
                                period ->
                                        List.of(
                                                text(period.getGrade()),
                                                date(period.getStartedOn()),
                                                period.isOngoing()
                                                        ? "Até a presente data"
                                                        : date(period.getEndedOn()),
                                                String.valueOf(period.getDays())))
                        .toList());
    }

    /** Texto corrido, sem tabela: entrevista costuma ser longa e numa coluna ficaria ilegível. */
    private void addInterviews(ClientRecord clientRecord) {
        addSection("Entrevistas");
        if (clientRecord.interviews().isEmpty()) {
            addEmpty();
        }
        for (ClientInterviewResponseDTO interview : clientRecord.interviews()) {
            String duration =
                    interview.getDurationMinutes() == null
                            ? ""
                            : " - " + interview.getDurationMinutes() + " min";
            html.append("<div class=\"interview\"><p class=\"block-title\">")
                    .append(dateTime(interview.getOccurredAt()))
                    .append(duration)
                    .append(" - por ")
                    .append(escape(userName(clientRecord, interview.getCreatedBy())))
                    .append("</p><div class=\"rich\">")
                    // Já limpo e serializado como XHTML: só as marcas do editor, sem atributo.
                    .append(RichText.body(interview.getContent()).html())
                    .append("</div></div>");
        }
    }

    private void addFiles(ClientRecord clientRecord) {
        addSection("Documentos anexados");
        addTable(
                new float[] {3, 4, 2.6f, 4},
                List.of("Tipo", "Arquivo", "Enviado em", "Observação"),
                clientRecord.documents().stream()
                        .map(
                                file ->
                                        List.of(
                                                text(file.getDocumentType()),
                                                text(file.getOriginalFilename()),
                                                dateTime(file.getUploadedAt()),
                                                text(file.getNotes())))
                        .toList());

        addSection("Simulações");
        addTable(
                new float[] {4, 2, 1.5f, 1.5f, 1.5f, 3},
                List.of("Arquivo", "Data", "Versão", "Vínculos", "Principal", "Observação"),
                clientRecord.simulations().stream()
                        .map(
                                file ->
                                        List.of(
                                                text(file.getOriginalFilename()),
                                                date(file.getSimulationDate()),
                                                text(file.getVersion()),
                                                text(file.getVinculos()),
                                                yesNo(file.getIsPrincipal()),
                                                text(file.getNotes())))
                        .toList());
    }

    private void addAppointments(ClientRecord clientRecord) {
        addSection("Compromissos");
        addTable(
                new float[] {2.8f, 3, 2, 2, 2, 3.4f},
                List.of("Início", "Título", "Tipo", "Local", "Status", "Detalhes"),
                clientRecord.appointments().stream()
                        .map(
                                appointment ->
                                        List.of(
                                                dateTime(appointment.getStartAt()),
                                                text(appointment.getTitle()),
                                                text(appointment.getType()),
                                                join(
                                                        " - ",
                                                        appointment.getModality(),
                                                        appointment.getLocation()),
                                                text(appointment.getStatus()),
                                                join(
                                                        ". Motivo do cancelamento: ",
                                                        appointment.getDescription(),
                                                        appointment.getCancellationReason())))
                        .toList());
    }

    private void addHistory(ClientRecord clientRecord) {
        addSection("Histórico de situação no escritório");
        addTable(
                new float[] {2.4f, 4, 4, 3},
                List.of("Data", "De", "Para", "Alterado por"),
                clientRecord.situationHistory().stream()
                        .map(
                                entry ->
                                        List.of(
                                                dateTime(entry.getChangedAt()),
                                                text(entry.getPreviousSituation()),
                                                text(entry.getCurrentSituation()),
                                                userName(clientRecord, entry.getChangedByUserId())))
                        .toList());

        addSection("Histórico de etapas no INSS");
        addTable(
                new float[] {2.8f, 2.3f, 2.3f, 1.3f, 3.8f, 2.3f},
                List.of("Data", "De", "Para", "Dias na anterior", "Justificativa", "Alterado por"),
                clientRecord.requestStageHistory().stream()
                        .map(
                                entry ->
                                        List.of(
                                                dateTime(entry.getChangedAt()),
                                                text(entry.getPreviousStage()),
                                                text(entry.getCurrentStage()),
                                                text(entry.getDiasNaEtapaAnterior()),
                                                text(entry.getJustification()),
                                                userName(clientRecord, entry.getChangedByUserId())))
                        .toList());
    }

    private void addPayments(ClientRecord clientRecord) {
        addSection("Pagamentos a receber do INSS");
        addTable(
                new float[] {4, 1.2f, 2, 2, 2, 2.2f, 2},
                List.of(
                        "Descrição",
                        "Parcela",
                        "Vencimento",
                        "Pago em",
                        "Forma",
                        "Valor",
                        "Status"),
                clientRecord.payments().stream()
                        .map(
                                payment ->
                                        List.of(
                                                text(payment.getDescription()),
                                                join(
                                                        "/",
                                                        text(payment.getInstallmentNumber()),
                                                        text(payment.getInstallmentTotal())),
                                                date(payment.getDueDate()),
                                                date(payment.getPaidDate()),
                                                text(payment.getPaymentMethod()),
                                                money(payment.getAmount()),
                                                payment.isOverdue()
                                                        ? "Vencido"
                                                        : text(payment.getStatus())))
                        .toList());
    }

    private void addSection(String title) {
        html.append("<h2>").append(escape(title)).append("</h2>");
    }

    private void addEmpty() {
        html.append("<p class=\"none\">").append(NO_RECORDS).append("</p>");
    }

    private void addFields(Field... fields) {
        html.append("<table class=\"fields\">");
        for (int index = 0; index < fields.length; index += 2) {
            html.append("<tr>");
            fieldCell(fields[index]);
            if (index + 1 < fields.length) {
                fieldCell(fields[index + 1]);
            } else {
                html.append("<td></td>");
            }
            html.append("</tr>");
        }
        html.append("</table>");
    }

    private void fieldCell(Field item) {
        html.append("<td><div class=\"label\">")
                .append(escape(item.label()))
                .append("</div><div class=\"value")
                .append(EMPTY_VALUE.equals(item.value()) ? " empty" : "")
                .append("\">")
                .append(escape(item.value()))
                .append("</div></td>");
    }

    private void addTable(float[] widths, List<String> headers, List<List<String>> rows) {
        if (rows.isEmpty()) {
            addEmpty();
            return;
        }
        float total = 0;
        for (float width : widths) {
            total += width;
        }
        html.append("<table class=\"records\"><thead><tr>");
        for (int index = 0; index < headers.size(); index++) {
            html.append("<th style=\"width:")
                    .append(Math.round(widths[index] / total * 1000) / 10f)
                    .append("%\">")
                    .append(escape(headers.get(index)))
                    .append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (List<String> row : rows) {
            html.append("<tr>");
            for (String value : row) {
                html.append(EMPTY_VALUE.equals(value) ? "<td class=\"empty\">" : "<td>")
                        .append(escape(value))
                        .append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
    }

    /** O mínimo do XML: o openhtmltopdf lê XHTML, e entidade nomeada do HTML não existe lá. */
    private static String escape(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }

    private static String stageOf(ClientDetailsDTO client) {
        return client.getRequestStage() == null
                ? "Não protocolado"
                : client.getRequestStage().toString();
    }

    private static Field field(String label, Object value) {
        return new Field(label, text(value));
    }

    private static String addressLine(ClientAddressResponseDTO address) {
        String line =
                join(
                        ", ",
                        address.getStreet(),
                        join(" - ", address.getAddressNumber(), address.getComplement()));
        return join(" - ", join(" - ", line, address.getNeighborhood()), address.getZipCode());
    }

    private static String userName(ClientRecord clientRecord, UUID userId) {
        return text(userId == null ? null : clientRecord.userNames().get(userId));
    }

    private static String date(LocalDate value) {
        return value == null ? EMPTY_VALUE : DATE.format(value);
    }

    private static String dateTime(Instant value) {
        return value == null ? EMPTY_VALUE : DATE_TIME.format(value);
    }

    private static String money(BigDecimal value) {
        return value == null ? EMPTY_VALUE : NumberFormat.getCurrencyInstance(PT_BR).format(value);
    }

    private static String yesNo(Boolean value) {
        if (value == null) {
            return EMPTY_VALUE;
        }
        return Boolean.TRUE.equals(value) ? "Sim" : "Não";
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? EMPTY_VALUE : value.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Junta só as partes preenchidas, para não imprimir "Rua A, - - ". */
    private static String join(String separator, String... parts) {
        List<String> present =
                Stream.of(parts)
                        .filter(ClientRecordHtmlRenderer::hasText)
                        .filter(part -> !EMPTY_VALUE.equals(part))
                        .toList();
        return present.isEmpty() ? EMPTY_VALUE : String.join(separator, present);
    }

    private record Field(String label, String value) {}
}
