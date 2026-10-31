package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.util.OfficeClock;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.draw.LineSeparator;

/** Desenha o {@link ClientRecord} em PDF. Só layout: não consulta nada nem decide permissão. */
final class ClientRecordPdfRenderer {

    private static final String EMPTY_VALUE = "—";
    private static final String NO_RECORDS = "Nenhum registro.";

    // Medidas em pontos, numa escala de 4: o ritmo vertical é o que faz a página ler como uma
    // coisa só, em vez de blocos colados uns nos outros.
    private static final float MARGIN_X = 48;
    private static final float MARGIN_TOP = 44;
    private static final float MARGIN_BOTTOM = 60;
    private static final float FOOTER_Y = 30;
    private static final float SECTION_GAP = 20;
    private static final float MIN_SPACE_FOR_SECTION = 80;

    private static final Color INK = new Color(32, 36, 44);
    private static final Color ACCENT = new Color(31, 56, 100);
    private static final Color MUTED = new Color(108, 114, 124);
    private static final Color FAINT = new Color(170, 175, 183);
    private static final Color RULE = new Color(225, 228, 233);
    private static final Color SURFACE = new Color(244, 246, 249);

    private static final Font OFFICE = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    private static final Font NAME = new Font(Font.HELVETICA, 20, Font.BOLD, ACCENT);
    private static final Font META = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    private static final Font SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, ACCENT);
    private static final Font LABEL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, MUTED);
    private static final Font VALUE = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);
    private static final Font VALUE_EMPTY = new Font(Font.HELVETICA, 10, Font.NORMAL, FAINT);
    private static final Font SUMMARY_VALUE = new Font(Font.HELVETICA, 9.5f, Font.BOLD, INK);
    private static final Font HEAD_CELL = new Font(Font.HELVETICA, 7.5f, Font.BOLD, MUTED);
    private static final Font CELL = new Font(Font.HELVETICA, 9, Font.NORMAL, INK);
    private static final Font CELL_EMPTY = new Font(Font.HELVETICA, 9, Font.NORMAL, FAINT);
    private static final Font NOTE = new Font(Font.HELVETICA, 9, Font.ITALIC, MUTED);
    private static final Font BLOCK_TITLE = new Font(Font.HELVETICA, 8.5f, Font.BOLD, INK);
    private static final float BODY_SIZE = 9.5f;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(OfficeClock.ZONE);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final Document document;
    private final PdfWriter writer;

    private ClientRecordPdfRenderer(Document document, PdfWriter writer) {
        this.document = document;
        this.writer = writer;
    }

    static byte[] render(ClientRecord clientRecord) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Document document =
                new Document(PageSize.A4, MARGIN_X, MARGIN_X, MARGIN_TOP, MARGIN_BOTTOM)) {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new Footer(clientRecord));
            document.addTitle("Ficha do cliente");
            document.open();

            new ClientRecordPdfRenderer(document, writer).addSections(clientRecord);
        }
        return output.toByteArray();
    }

    private void addSections(ClientRecord clientRecord) {
        addHeader(clientRecord);
        addPersonalData(clientRecord.client());
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
    }

    /** Escritório e data em cima; o nome em destaque; e uma faixa com o que se procura primeiro. */
    private void addHeader(ClientRecord clientRecord) {
        ClientDetailsDTO client = clientRecord.client();

        PdfPTable top = new PdfPTable(new float[] {1, 1});
        top.setWidthPercentage(100);
        top.addCell(bare(new Phrase(clientRecord.officeName(), OFFICE), Element.ALIGN_LEFT));
        top.addCell(
                bare(
                        new Phrase(
                                "Ficha do cliente · gerada em "
                                        + dateTime(clientRecord.generatedAt()),
                                OFFICE),
                        Element.ALIGN_RIGHT));
        document.add(top);

        Paragraph name = new Paragraph(client.getFullName(), NAME);
        name.setSpacingBefore(10);
        document.add(name);
        Paragraph confidential =
                new Paragraph("Documento sigiloso. Uso interno do escritório.", META);
        confidential.setSpacingBefore(2);
        document.add(confidential);

        PdfPTable summary = new PdfPTable(new float[] {1.1f, 1.6f, 1.3f, 1.1f});
        summary.setWidthPercentage(100);
        summary.setSpacingBefore(14);
        summaryCell(summary, "CPF", client.getCpf());
        summaryCell(summary, "Benefício", text(client.getBenefit()));
        summaryCell(summary, "Situação no escritório", text(client.getSituation()));
        summaryCell(summary, "Etapa no INSS", stageOf(client));
        document.add(summary);
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
            addBlockTitle("Observações");
            document.add(bodyParagraph(text(client.getNotes())));
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
        boolean first = true;
        for (ClientInterviewResponseDTO interview : clientRecord.interviews()) {
            if (!first) {
                document.add(rule(8));
            }
            first = false;
            String duration =
                    interview.getDurationMinutes() == null
                            ? ""
                            : " - " + interview.getDurationMinutes() + " min";
            addBlockTitle(
                    dateTime(interview.getOccurredAt())
                            + duration
                            + " - por "
                            + userName(clientRecord, interview.getCreatedBy()));
            addRichText(interview.getContent());
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
        // Título não fica sozinho no pé da página, separado do conteúdo que ele nomeia.
        if (writer.getVerticalPosition(true) - document.bottom() < MIN_SPACE_FOR_SECTION) {
            document.newPage();
        }
        Paragraph heading = new Paragraph(title, SECTION);
        heading.setSpacingBefore(SECTION_GAP);
        document.add(heading);
    }

    /** Linha fina na largura da página, {@code gap} pontos abaixo do que veio antes. */
    private static Paragraph rule(float gap) {
        Paragraph line = new Paragraph(new Chunk(new LineSeparator(0.6f, 100, RULE, 0, 0)));
        line.setSpacingBefore(gap);
        line.setLeading(0);
        return line;
    }

    private void addEmpty() {
        Paragraph empty = new Paragraph(NO_RECORDS, NOTE);
        empty.setSpacingBefore(6);
        document.add(empty);
    }

    private void addFields(Field... fields) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        for (int index = 0; index < fields.length; index++) {
            Field item = fields[index];
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(RULE);
            cell.setBorderWidth(0.6f);
            cell.setPaddingTop(6);
            cell.setPaddingBottom(7);
            cell.setPaddingLeft(0);
            // Coluna da esquerda guarda distância da direita; a da direita, da margem.
            cell.setPaddingRight(index % 2 == 0 ? 16 : 0);
            cell.setUseAscender(true);
            Paragraph label = new Paragraph(9, item.label(), LABEL);
            cell.addElement(label);
            Paragraph value =
                    new Paragraph(
                            13,
                            item.value(),
                            EMPTY_VALUE.equals(item.value()) ? VALUE_EMPTY : VALUE);
            value.setSpacingBefore(1);
            cell.addElement(value);
            table.addCell(cell);
        }
        table.completeRow();
        document.add(table);
    }

    private void addBlockTitle(String title) {
        Paragraph label = new Paragraph(title, BLOCK_TITLE);
        label.setSpacingBefore(10);
        document.add(label);
    }

    /**
     * O relato no desenho que o editor deu: parágrafos, ênfases, listas e citação, sem as marcas
     * cruas. Ver {@link RichText} para o que passa.
     */
    private void addRichText(String content) {
        List<Node> blocks = RichText.body(content).childNodes();
        if (blocks.stream().noneMatch(ClientRecordPdfRenderer::hasContent)) {
            document.add(bodyParagraph(EMPTY_VALUE));
            return;
        }
        for (Node block : blocks) {
            addBlock(block, 0, false);
        }
    }

    private void addBlock(Node node, int depth, boolean quoted) {
        if (node instanceof TextNode textNode) {
            if (!textNode.isBlank()) {
                document.add(styledParagraph(List.of(node), depth, quoted));
            }
            return;
        }
        if (!(node instanceof org.jsoup.nodes.Element element)) {
            return;
        }
        switch (element.normalName()) {
            case "ul", "ol" -> addList(element, depth, quoted);
            case "blockquote" ->
                    element.childNodes().forEach(child -> addBlock(child, depth, true));
            case "br" -> {
                // Quebra solta entre blocos não diz nada.
            }
            default -> document.add(styledParagraph(element.childNodes(), depth, quoted));
        }
    }

    private void addList(org.jsoup.nodes.Element list, int depth, boolean quoted) {
        boolean ordered = "ol".equals(list.normalName());
        int number = 1;
        for (org.jsoup.nodes.Element item : list.children()) {
            String marker = ordered ? (number++) + "." : "•";
            List<Node> inline =
                    item.childNodes().stream()
                            .flatMap(
                                    child ->
                                            child instanceof org.jsoup.nodes.Element e
                                                            && "p".equals(e.normalName())
                                                    ? e.childNodes().stream()
                                                    : Stream.of(child))
                            .filter(
                                    child ->
                                            !(child instanceof org.jsoup.nodes.Element e
                                                    && ("ul".equals(e.normalName())
                                                            || "ol".equals(e.normalName()))))
                            .toList();
            Paragraph paragraph = styledParagraph(inline, depth + 1, quoted);
            paragraph.add(0, new Chunk(marker + "  ", font(Font.NORMAL, quoted)));
            paragraph.setFirstLineIndent(-10);
            document.add(paragraph);
            item.children().stream()
                    .filter(
                            child ->
                                    "ul".equals(child.normalName())
                                            || "ol".equals(child.normalName()))
                    .forEach(nested -> addList(nested, depth + 1, quoted));
        }
    }

    private static Paragraph styledParagraph(List<Node> inline, int depth, boolean quoted) {
        Paragraph paragraph = new Paragraph();
        paragraph.setLeading(BODY_SIZE * 1.45f);
        paragraph.setSpacingBefore(3);
        paragraph.setIndentationLeft(depth * 14 + (quoted ? 10 : 0));
        inline.forEach(node -> addInline(paragraph, node, Font.NORMAL, quoted));
        return paragraph;
    }

    private static void addInline(Paragraph paragraph, Node node, int style, boolean quoted) {
        if (node instanceof TextNode textNode) {
            paragraph.add(new Chunk(textNode.text(), font(style, quoted)));
            return;
        }
        if (!(node instanceof org.jsoup.nodes.Element element)) {
            return;
        }
        int nested =
                switch (element.normalName()) {
                    case "strong", "b" -> style | Font.BOLD;
                    case "em", "i" -> style | Font.ITALIC;
                    case "u" -> style | Font.UNDERLINE;
                    case "s" -> style | Font.STRIKETHRU;
                    default -> style;
                };
        if ("br".equals(element.normalName())) {
            paragraph.add(Chunk.NEWLINE);
            return;
        }
        element.childNodes().forEach(child -> addInline(paragraph, child, nested, quoted));
    }

    private static Font font(int style, boolean quoted) {
        return new Font(
                Font.HELVETICA,
                BODY_SIZE,
                quoted ? style | Font.ITALIC : style,
                quoted ? MUTED : INK);
    }

    private static boolean hasContent(Node node) {
        return node instanceof TextNode text
                ? !text.isBlank()
                : node instanceof org.jsoup.nodes.Element element && element.hasText();
    }

    private static Paragraph bodyParagraph(String content) {
        return styledParagraph(List.of(new TextNode(content)), 0, false);
    }

    private void addTable(float[] widths, List<String> headers, List<List<String>> rows) {
        if (rows.isEmpty()) {
            addEmpty();
            return;
        }
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setHeaderRows(1);
        // Linha com justificativa longa pode passar de uma página; sem isto, o OpenPDF a corta.
        table.setSplitLate(false);
        for (String header : headers) {
            PdfPCell cell = cell(header, HEAD_CELL);
            cell.setBackgroundColor(SURFACE);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
        rows.stream()
                .flatMap(List::stream)
                .map(value -> cell(value, EMPTY_VALUE.equals(value) ? CELL_EMPTY : CELL))
                .forEach(table::addCell);
        document.add(table);
    }

    private static PdfPCell cell(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        // Célula de texto ignora o entrelinha da frase: linha quebrada colava na de cima.
        cell.setLeading(12, 0);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setBorderWidth(0.6f);
        cell.setUseAscender(true);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(7);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(6);
        return cell;
    }

    private static void summaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(SURFACE);
        cell.setUseAscender(true);
        cell.setPadding(10);
        cell.addElement(new Paragraph(9, label, LABEL));
        Paragraph paragraph = new Paragraph(12, text(value), SUMMARY_VALUE);
        paragraph.setSpacingBefore(2);
        cell.addElement(paragraph);
        table.addCell(cell);
    }

    private static PdfPCell bare(Phrase phrase, int alignment) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.setHorizontalAlignment(alignment);
        return cell;
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
                        .filter(ClientRecordPdfRenderer::hasText)
                        .filter(part -> !EMPTY_VALUE.equals(part))
                        .toList();
        return present.isEmpty() ? EMPTY_VALUE : String.join(separator, present);
    }

    private record Field(String label, String value) {}

    /**
     * Rodapé com "Página N de T". O total só se conhece ao fechar o documento, então cada página
     * reserva um template que é preenchido no fim.
     */
    private static final class Footer extends PdfPageEventHelper {

        private static final float FONT_SIZE = 7.5f;
        private static final float TOTAL_WIDTH = 14;

        private final String clientName;
        private BaseFont font;
        private PdfTemplate totalPages;

        Footer(ClientRecord clientRecord) {
            this.clientName = clientRecord.client().getFullName();
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            font = LABEL.getCalculatedBaseFont(false);
            totalPages = writer.getDirectContent().createTemplate(TOTAL_WIDTH, FONT_SIZE + 2);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            canvas.setColorStroke(RULE);
            canvas.setLineWidth(0.6f);
            canvas.moveTo(document.left(), FOOTER_Y + 12);
            canvas.lineTo(document.right(), FOOTER_Y + 12);
            canvas.stroke();

            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_LEFT,
                    new Phrase(clientName + " · Ficha do cliente · Documento sigiloso", FOOTER),
                    document.left(),
                    FOOTER_Y,
                    0);

            String page = "Página " + writer.getPageNumber() + " de ";
            float x = document.right() - TOTAL_WIDTH - font.getWidthPoint(page, FONT_SIZE);
            canvas.beginText();
            canvas.setFontAndSize(font, FONT_SIZE);
            canvas.setColorFill(MUTED);
            canvas.setTextMatrix(x, FOOTER_Y);
            canvas.showText(page);
            canvas.endText();
            canvas.addTemplate(totalPages, document.right() - TOTAL_WIDTH, FOOTER_Y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            totalPages.setFontAndSize(font, FONT_SIZE);
            totalPages.setColorFill(MUTED);
            totalPages.setTextMatrix(0, 0);
            // Ao fechar, o writer já avançou para a página seguinte, que não existe.
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }

        private static final Font FOOTER = new Font(Font.HELVETICA, FONT_SIZE, Font.NORMAL, MUTED);
    }
}
