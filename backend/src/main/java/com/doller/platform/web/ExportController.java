package com.doller.platform.web;

import com.doller.platform.dto.TradingDtos;
import com.doller.platform.service.TradingService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/exports")
public class ExportController {
    private final TradingService service;

    public ExportController(TradingService service) {
        this.service = service;
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(@RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) {
        List<TradingDtos.StatementLine> lines = service.statementRange(from, to);
        StringBuilder sb = new StringBuilder("date,openingCash,closingCash,openingUsd,closingUsd,openingReceivable,closingReceivable,openingPayable,closingPayable,openingAdvanceIn,closingAdvanceIn,openingAdvanceOut,closingAdvanceOut,openingAging,closingAging,pnl\n");
        lines.forEach(l -> sb.append(l.date()).append(',').append(l.openingCash()).append(',').append(l.closingCash()).append(',')
                .append(l.openingUsd()).append(',').append(l.closingUsd()).append(',')
                .append(l.openingReceivableBdt()).append(',').append(l.closingReceivableBdt()).append(',')
                .append(l.openingPayableBdt()).append(',').append(l.closingPayableBdt()).append(',')
                .append(l.openingAdvanceFromPartyBdt()).append(',').append(l.closingAdvanceFromPartyBdt()).append(',')
                .append(l.openingAdvanceToPartyBdt()).append(',').append(l.closingAdvanceToPartyBdt()).append(',')
                .append(l.openingAgingBdt()).append(',').append(l.closingAgingBdt()).append(',')
                .append(l.pnl()).append('\n'));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement.csv")
                .header("X-Export-Range", from + "_" + to)
                .contentType(MediaType.TEXT_PLAIN)
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(value = "reportType", required = false) String reportType,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "partyId", required = false) Long partyId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection
    ) throws Exception {
        String normalizedType = reportType == null ? "BALANCE_SHEET" : reportType.trim().toUpperCase(Locale.ROOT);
        byte[] out;
        String filename;
        String rangeHeader;
        if ("TRANSACTION_DETAILS".equals(normalizedType)) {
            TradingDtos.TransactionExportReport response = service.transactionExportReport(from, to, type, partyId, search, sortField, sortDirection);
            out = renderTransactionPdf(response);
            filename = "transaction_details.pdf";
            rangeHeader = response.from() + "_" + response.to();
        } else {
            TradingDtos.BalanceSheetResponse response = service.balanceSheetReport(mode, date, month, year, from, to);
            out = renderBalanceSheetPdf(response);
            filename = "balance_sheet.pdf";
            rangeHeader = response.from() + "_" + response.to();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .header("X-Export-Range", rangeHeader)
                .contentType(MediaType.APPLICATION_PDF)
                .body(out);
    }

    private byte[] renderBalanceSheetPdf(TradingDtos.BalanceSheetResponse response) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.beginText();
                cs.newLineAtOffset(50, 790);
                cs.showText("Doller Platform - Balance Sheet");
                cs.endText();

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.beginText();
                cs.newLineAtOffset(50, 772);
                cs.showText("Mode: " + response.mode() + " | Range: " + response.from() + " to " + response.to());
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(50, 756);
                cs.showText(String.format("Open Cash=%s | Close Cash=%s | Open FX Qty=%s | Close FX Qty=%s | Total P/L=%s",
                        response.openingCash(), response.closingCash(), response.openingUsd(), response.closingUsd(), response.totalPnl()));
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(50, 740);
                cs.showText(String.format("Open Rec=%s | Close Rec=%s | Open Pay=%s | Close Pay=%s",
                        response.openingReceivableBdt(), response.closingReceivableBdt(),
                        response.openingPayableBdt(), response.closingPayableBdt()));
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(50, 724);
                cs.showText(String.format("Open Adv In=%s | Close Adv In=%s | Open Adv Out=%s | Close Adv Out=%s",
                        response.openingAdvanceFromPartyBdt(), response.closingAdvanceFromPartyBdt(),
                        response.openingAdvanceToPartyBdt(), response.closingAdvanceToPartyBdt()));
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(50, 708);
                cs.showText(String.format("Open Aging=%s | Close Aging=%s",
                        response.openingAgingBdt(), response.closingAgingBdt()));
                cs.endText();

                float y = 692;
                for (TradingDtos.StatementLine line : response.lines()) {
                    if (y < 60) break;
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(String.format("%s | cash %s->%s | fx %s->%s | rec %s->%s | pay %s->%s | pnl=%s",
                            line.date(), line.openingCash(), line.closingCash(), line.openingUsd(), line.closingUsd(),
                            line.openingReceivableBdt(), line.closingReceivableBdt(),
                            line.openingPayableBdt(), line.closingPayableBdt(), line.pnl()));
                    cs.endText();
                    y -= 16;
                }
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] renderTransactionPdf(TradingDtos.TransactionExportReport response) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            TransactionPdfWriter writer = new TransactionPdfWriter(doc, response);
            writer.begin();
            for (TradingDtos.TransactionPartyExportSection section : response.partySections()) {
                writer.drawPartySection(section);
            }
            writer.drawGrandSummary();
            writer.end();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static final class TransactionPdfWriter {
        private final PDDocument doc;
        private final TradingDtos.TransactionExportReport report;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final DecimalFormat bdt = new DecimalFormat("#,##0.00");
        private final float margin = 34f;
        private final float top = 806f;
        private final float bottom = 48f;

        private PDPage page;
        private PDPageContentStream cs;
        private float y;
        private byte[] logo;

        private TransactionPdfWriter(PDDocument doc, TradingDtos.TransactionExportReport report) {
            this.doc = doc;
            this.report = report;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("pdf/logo.jpeg")) {
                if (in != null) logo = in.readAllBytes();
            } catch (IOException ignored) {
                logo = null;
            }
        }

        private void begin() throws IOException {
            newPage(false);
        }

        private void end() throws IOException {
            if (cs != null) cs.close();
        }

        private void newPage(boolean continued) throws IOException {
            if (cs != null) cs.close();
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = top;
            drawHeader(continued);
        }

        private void ensure(float h) throws IOException {
            if (y - h < bottom) newPage(true);
        }

        private void drawHeader(boolean continued) throws IOException {
            if (logo != null) {
                try {
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, logo, "logo");
                    cs.drawImage(img, margin, y - 24, 24, 24);
                } catch (IOException ignored) {
                    // fallback to text-only header
                }
            }
            text("NexPay", margin + 30, y - 5, bold, 16, new Color(17, 48, 87));
            text("Transaction Details Report | Range: " + report.from() + " to " + report.to(), margin, y - 26, regular, 9, Color.DARK_GRAY);
            text(String.format("Type=%s | Search=%s | Sort=%s %s",
                    isBlank(report.typeFilter()) ? "ALL" : report.typeFilter(),
                    isBlank(report.search()) ? "-" : report.search(),
                    report.sortField(),
                    report.sortDirection()), margin, y - 38, regular, 9, Color.DARK_GRAY);
            if (continued) {
                text("Continued", page.getMediaBox().getWidth() - margin - 40, y - 5, regular, 8, Color.GRAY);
            }
            line(y - 44);
            y -= 52;
        }

        private void drawPartySection(TradingDtos.TransactionPartyExportSection section) throws IOException {
            ensure(180);
            fill(margin, y - 50, page.getMediaBox().getWidth() - margin * 2, 44, new Color(245, 248, 252));
            text("Customer: " + safe(section.party().partyName()), margin + 8, y - 18, bold, 11, Color.BLACK);
            text("Phone: " + safe(section.party().phone()) + "   Address: " + safe(section.party().address()), margin + 8, y - 34, regular, 9, Color.DARK_GRAY);
            y -= 58;

            drawDealTable(section.deals(), section.dealSummary());
            y -= 6;
            drawSettlementTable(section.settlements(), section.settlementSummary());
            y -= 6;
            drawExposure("Party Exposure Summary", section.exposureSummary());
            y -= 10;
        }

        private void drawDealTable(List<TradingDtos.TransactionDealExportRow> rows, TradingDtos.TransactionDealSummary summary) throws IOException {
            ensure(80);
            text("Deals", margin, y, bold, 11, new Color(17, 48, 87));
            y -= 8;
            String[] headers = {"Deal ID", "Date", "Time", "Direction", "Instrument/Currency", "Quantity", "Rate", "Amount"};
            float[] cols = {44, 60, 54, 58, 126, 60, 58, 74};
            tableHeader(headers, cols);
            for (TradingDtos.TransactionDealExportRow r : rows) {
                ensure(16);
                String[] cells = {
                        safe(r.dealId()),
                        safe(r.date()),
                        safe(r.time()),
                        safe(r.direction()),
                        safe(r.instrumentCode()),
                        fmt(r.quantity()),
                        fmt(r.bdtRate()),
                        fmt(r.amountBdt())
                };
                tableRow(cells, cols, List.of(5, 6, 7));
            }
            dealSummary(summary);
        }

        private void drawSettlementTable(List<TradingDtos.TransactionSettlementExportRow> rows, TradingDtos.TransactionSettlementSummary summary) throws IOException {
            ensure(80);
            text("Settlements", margin, y, bold, 11, new Color(17, 48, 87));
            y -= 8;
            String[] headers = {"Settlement ID", "Date", "Time", "Direction", "Payment Method", "Related Deal ID"};
            float[] cols = {72, 68, 58, 80, 116, 96};
            tableHeader(headers, cols);
            for (TradingDtos.TransactionSettlementExportRow r : rows) {
                ensure(16);
                String[] cells = {
                        safe(r.settlementId()),
                        safe(r.date()),
                        safe(r.time()),
                        safe(r.direction()),
                        safe(r.paymentMethod()),
                        safe(r.relatedDealId())
                };
                tableRow(cells, cols, List.of());
            }
            settlementSummary(summary);
        }

        private void drawExposure(String title, TradingDtos.PartyBalanceSummary b) throws IOException {
            ensure(56);
            fill(margin, y - 46, page.getMediaBox().getWidth() - margin * 2, 40, new Color(249, 250, 252));
            text(title, margin + 8, y - 16, bold, 10, Color.BLACK);
            text(String.format("Receivable: %s | Payable: %s | Advance In: %s | Advance Out: %s | Aging: %s | Net: %s",
                    fmt(b.receivableBdt()), fmt(b.payableBdt()), fmt(b.advanceFromPartyBdt()), fmt(b.advanceToPartyBdt()), fmt(b.agingDueBdt()), fmt(b.netBalanceBdt())),
                    margin + 8, y - 32, regular, 9, Color.DARK_GRAY);
            y -= 52;
        }

        private void dealSummary(TradingDtos.TransactionDealSummary s) throws IOException {
            ensure(38);
            fill(margin, y - 32, page.getMediaBox().getWidth() - margin * 2, 26, new Color(240, 245, 252));
            text(String.format("Deal Count: %d | Total Buy: %s | Total Sell: %s | Net Deal Exposure: %s",
                    s.count(), fmt(s.totalBuyBdt()), fmt(s.totalSellBdt()), fmt(s.netDealExposureBdt())),
                    margin + 8, y - 17, bold, 9, new Color(20, 40, 90));
            y -= 36;
        }

        private void settlementSummary(TradingDtos.TransactionSettlementSummary s) throws IOException {
            ensure(38);
            fill(margin, y - 32, page.getMediaBox().getWidth() - margin * 2, 26, new Color(240, 245, 252));
            text(String.format("Settlement Count: %d | Incoming: %s | Outgoing: %s | Linked: %d | Unlinked: %d",
                    s.count(), fmt(s.totalIncomingBdt()), fmt(s.totalOutgoingBdt()), s.linkedCount(), s.unlinkedCount()),
                    margin + 8, y - 17, bold, 9, new Color(20, 40, 90));
            y -= 36;
        }

        private void drawGrandSummary() throws IOException {
            ensure(150);
            line(y + 4);
            text("Grand Totals (All Parties)", margin, y - 8, bold, 12, new Color(17, 48, 87));
            y -= 22;
            dealSummary(report.grandDealSummary());
            settlementSummary(report.grandSettlementSummary());
            drawExposure("Grand Exposure Summary", report.grandExposureSummary());
        }

        private void tableHeader(String[] headers, float[] cols) throws IOException {
            ensure(18);
            fill(margin, y - 15, sum(cols), 14, new Color(225, 233, 246));
            float x = margin + 2;
            for (int i = 0; i < headers.length; i++) {
                text(headers[i], x, y - 5, bold, 8, new Color(32, 47, 82));
                x += cols[i];
            }
            y -= 16;
            line(y + 3);
        }

        private void tableRow(String[] cells, float[] cols, List<Integer> rightAlignCols) throws IOException {
            float x = margin + 2;
            for (int i = 0; i < cells.length; i++) {
                String value = truncate(cells[i], cols[i], 8);
                if (rightAlignCols.contains(i)) {
                    textRight(value, x + cols[i] - 3, y - 5, regular, 8, Color.BLACK);
                } else {
                    text(value, x, y - 5, regular, 8, Color.BLACK);
                }
                x += cols[i];
            }
            y -= 14;
        }

        private void text(String value, float x, float yAt, PDType1Font font, float size, Color color) throws IOException {
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.beginText();
            cs.newLineAtOffset(x, yAt);
            cs.showText(value == null ? "" : value);
            cs.endText();
        }

        private void textRight(String value, float rightX, float yAt, PDType1Font font, float size, Color color) throws IOException {
            float w = font.getStringWidth(value) / 1000f * size;
            text(value, rightX - w, yAt, font, size, color);
        }

        private void fill(float x, float yAt, float width, float height, Color color) throws IOException {
            cs.setNonStrokingColor(color);
            cs.addRect(x, yAt, width, height);
            cs.fill();
        }

        private void line(float yAt) throws IOException {
            cs.setStrokingColor(new Color(222, 226, 232));
            cs.moveTo(margin, yAt);
            cs.lineTo(page.getMediaBox().getWidth() - margin, yAt);
            cs.stroke();
        }

        private float sum(float[] cols) {
            float out = 0;
            for (float col : cols) out += col;
            return out;
        }

        private String truncate(String value, float colWidth, float fontSize) throws IOException {
            String text = safe(value);
            float limit = colWidth - 4;
            if (regular.getStringWidth(text) / 1000f * fontSize <= limit) return text;
            String out = text;
            while (!out.isEmpty() && regular.getStringWidth(out + "...") / 1000f * fontSize > limit) {
                out = out.substring(0, out.length() - 1);
            }
            return out + "...";
        }

        private String fmt(java.math.BigDecimal value) {
            if (value == null) return "0.00";
            return bdt.format(value);
        }

        private String safe(Object value) {
            return value == null ? "-" : String.valueOf(value);
        }

        private static boolean isBlank(String v) {
            return v == null || v.isBlank();
        }
    }
}
