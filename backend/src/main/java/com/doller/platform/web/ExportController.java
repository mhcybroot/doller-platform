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
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD), 14);
                cs.beginText();
                cs.newLineAtOffset(50, 790);
                cs.showText("Doller Platform - Balance Sheet");
                cs.endText();

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 10);
                cs.beginText();
                cs.newLineAtOffset(50, 772);
                cs.showText("Mode: " + response.mode() + " | Range: " + response.from() + " to " + response.to());
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(50, 756);
                cs.showText(String.format("Open Cash=%s | Close Cash=%s | Open FX Amt=%s | Close FX Amt=%s | Total P/L=%s",
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
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.COURIER);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
        private final DecimalFormat bdt = new DecimalFormat("#,##0.00");
        private final float margin = 34f;
        private final float top = 806f;
        private final float bottom = 48f;
        private final float sectionTitleGap = 14f;
        private final float sectionGap = 10f;
        private final float customerToDealsGap = 10f;
        private final float cardPadding = 10f;
        private final float tableHeaderHeight = 20f;
        private final float tableRowHeight = 18f;
        private final float tableHeaderTextOffset = 7f;
        private final float tableRowTextOffset = 6f;
        private final float firstRowTopGap = 4f;
        private final float tableToSummaryGap = 10f;
        private final float summaryHeight = 32f;
        private final float summaryTextOffset = 20f;
        private final float exposureHeight = 48f;
        private final float sectionTitleCardHeight = 22f;

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
            float logoSize = 34f;
            float brandY = y - 16;
            if (logo != null) {
                try {
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, logo, "logo");
                    cs.drawImage(img, margin, y - logoSize, logoSize, logoSize);
                } catch (IOException ignored) {
                    // fallback to text-only header
                }
            }
            text("NexPay", margin + logoSize + 8, brandY, bold, 21, new Color(17, 48, 87));
            if (continued) {
                text("Continued", page.getMediaBox().getWidth() - margin - 40, brandY, regular, 8, Color.GRAY);
            }
            line(y - 42);
            y -= 56;
        }

        private void drawPartySection(TradingDtos.TransactionPartyExportSection section) throws IOException {
            ensure(230);
            float cardWidth = page.getMediaBox().getWidth() - margin * 2;
            float cardHeight = 62f;
            Color customerCardBg = new Color(32, 110, 68);
            Color customerTitleColor = new Color(238, 255, 245);
            Color customerMetaColor = new Color(218, 243, 227);
            fill(margin, y - cardHeight, cardWidth, cardHeight, customerCardBg);
            float innerX = margin + cardPadding;
            float phoneX = innerX;
            float addressX = margin + cardWidth * 0.38f;
            text("Customer: " + safe(section.party().partyName()), innerX, y - 22, bold, 11, customerTitleColor);
            text("Phone: " + safe(section.party().phone()), phoneX, y - 42, regular, 9, customerMetaColor);
            String address = "Address: " + safe(section.party().address());
            text(truncate(address, cardWidth * 0.60f, 9), addressX, y - 42, regular, 9, customerMetaColor);
            y -= (cardHeight + customerToDealsGap);

            drawDealTable(section.deals(), section.dealSummary());
            y -= sectionGap;
            drawSettlementTable(section.settlements(), section.settlementSummary());
            y -= sectionGap;
            drawExposure("Party Exposure Summary", section.exposureSummary());
            y -= 14;
        }

        private void drawDealTable(List<TradingDtos.TransactionDealExportRow> rows, TradingDtos.TransactionDealSummary summary) throws IOException {
            ensure(80);
            sectionTitleCard("Deals");
            String[] headers = {"Deal ID", "Date", "Time", "Direction", "Instrument/Currency", "Amt", "Rate", "Amount"};
            float[] cols = {44, 58, 56, 66, 118, 60, 58, 74};
            tableHeader(headers, cols);
            y -= firstRowTopGap;
            for (TradingDtos.TransactionDealExportRow r : rows) {
                ensure(tableRowHeight + 2);
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
            y -= tableToSummaryGap;
            dealSummary(summary);
        }

        private void drawSettlementTable(List<TradingDtos.TransactionSettlementExportRow> rows, TradingDtos.TransactionSettlementSummary summary) throws IOException {
            ensure(80);
            sectionTitleCard("Settlements");
            String[] headers = {"Settlement ID", "Date", "Time", "Direction", "Payment Method", "Related Deal ID"};
            float[] cols = {72, 64, 56, 132, 72, 92};
            tableHeader(headers, cols);
            y -= firstRowTopGap;
            for (TradingDtos.TransactionSettlementExportRow r : rows) {
                ensure(tableRowHeight + 2);
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
            y -= tableToSummaryGap;
            settlementSummary(summary);
        }

        private void drawExposure(String title, TradingDtos.PartyBalanceSummary b) throws IOException {
            ensure(exposureHeight + 18);
            boolean grandSummary = title != null && title.toLowerCase().contains("grand");
            Color cardBg = grandSummary ? new Color(154, 28, 36) : new Color(181, 37, 49);
            Color textColor = new Color(255, 248, 248);
            fill(margin, y - exposureHeight, page.getMediaBox().getWidth() - margin * 2, exposureHeight, cardBg);
            text(title, margin + 8, y - 18, bold, 10, textColor);
            text(String.format("Receivable: %s | Payable: %s | Net: %s",
                    fmt(b.receivableBdt()), fmt(b.payableBdt()), fmt(b.netBalanceBdt())),
                    margin + 8, y - 36, regular, 9, textColor);
            y -= (exposureHeight + 8);
        }

        private void dealSummary(TradingDtos.TransactionDealSummary s) throws IOException {
            ensure(summaryHeight + 10);
            float cardWidth = page.getMediaBox().getWidth() - margin * 2;
            fill(margin, y - summaryHeight, cardWidth, summaryHeight, new Color(236, 243, 252));
            drawMetricRow(
                    new String[]{"Deal Count", "Total Buy", "Total Sell", "Net Exposure"},
                    new String[]{String.valueOf(s.count()), fmt(s.totalBuyBdt()), fmt(s.totalSellBdt()), fmt(s.netDealExposureBdt())},
                    new Color[]{new Color(27, 61, 107), new Color(25, 109, 73), new Color(153, 39, 48), new Color(56, 69, 86)},
                    new Color[]{new Color(226, 236, 249), new Color(222, 244, 235), new Color(249, 229, 232), new Color(231, 236, 242)}
            );
            y -= (summaryHeight + 8);
        }

        private void settlementSummary(TradingDtos.TransactionSettlementSummary s) throws IOException {
            ensure(summaryHeight + 10);
            float cardWidth = page.getMediaBox().getWidth() - margin * 2;
            fill(margin, y - summaryHeight, cardWidth, summaryHeight, new Color(236, 243, 252));
            drawMetricRow(
                    new String[]{"Settle Count", "Incoming", "Outgoing", "Linked", "Unlinked"},
                    new String[]{String.valueOf(s.count()), fmt(s.totalIncomingBdt()), fmt(s.totalOutgoingBdt()), String.valueOf(s.linkedCount()), String.valueOf(s.unlinkedCount())},
                    new Color[]{new Color(27, 61, 107), new Color(25, 109, 73), new Color(153, 39, 48), new Color(56, 69, 86), new Color(90, 92, 102)},
                    new Color[]{new Color(226, 236, 249), new Color(222, 244, 235), new Color(249, 229, 232), new Color(231, 236, 242), new Color(240, 241, 246)}
            );
            y -= (summaryHeight + 8);
        }

        private void drawMetricRow(String[] labels, String[] values, Color[] textColors, Color[] bgColors) throws IOException {
            float gutter = 6f;
            float startX = margin + 8f;
            float availableWidth = page.getMediaBox().getWidth() - (margin * 2) - 16f;
            int count = labels.length;
            float chipWidth = (availableWidth - (gutter * (count - 1))) / count;
            float x = startX;
            for (int i = 0; i < count; i++) {
                drawMetricChip(labels[i], values[i], x, y - 7, chipWidth, 9f, textColors[i], bgColors[i]);
                x += chipWidth + gutter;
            }
        }

        private void drawMetricChip(String label, String value, float x, float topY, float width, float fontSize,
                                    Color textColor, Color bgColor) throws IOException {
            float chipHeight = 15f;
            fill(x, topY - chipHeight, width, chipHeight, bgColor);
            String chipText = label + ": " + value;
            text(truncate(chipText, width - 4f, fontSize), x + 3, centeredTextY(topY, chipHeight, fontSize), bold, fontSize, textColor);
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
            ensure(tableHeaderHeight + 2);
            float tableWidth = sum(cols);
            fill(margin, y - tableHeaderHeight + 1, tableWidth, tableHeaderHeight, new Color(225, 233, 246));
            float x = margin + 2;
            float headerTextY = centeredTextY(y, tableHeaderHeight, 8f);
            for (int i = 0; i < headers.length; i++) {
                text(headers[i], x, headerTextY, bold, 8, new Color(32, 47, 82));
                x += cols[i];
            }
            drawRowBorder(y, tableHeaderHeight, cols, new Color(184, 196, 214));
            y -= tableHeaderHeight;
        }

        private void sectionTitleCard(String title) throws IOException {
            ensure(sectionTitleCardHeight + sectionTitleGap + 4);
            float cardWidth = 104f;
            float cardX = margin;
            fill(cardX, y - sectionTitleCardHeight, cardWidth, sectionTitleCardHeight, new Color(234, 241, 251));
            float titleWidth = bold.getStringWidth(title) / 1000f * 11f;
            float centeredX = cardX + (cardWidth - titleWidth) / 2f;
            text(title, centeredX, y - 15, bold, 11, new Color(17, 48, 87));
            y -= (sectionTitleCardHeight + sectionTitleGap);
        }

        private void tableRow(String[] cells, float[] cols, List<Integer> rightAlignCols) throws IOException {
            float rowTop = y;
            float x = margin + 2;
            float rowTextY = centeredTextY(y, tableRowHeight, 8f);
            for (int i = 0; i < cells.length; i++) {
                String value = truncate(cells[i], cols[i], 8);
                if (rightAlignCols.contains(i)) {
                    textRight(value, x + cols[i] - 3, rowTextY, regular, 8, Color.BLACK);
                } else {
                    text(value, x, rowTextY, regular, 8, Color.BLACK);
                }
                x += cols[i];
            }
            drawRowBorder(rowTop, tableRowHeight, cols, new Color(214, 220, 230));
            y -= tableRowHeight;
        }

        private float centeredTextY(float topY, float cellHeight, float fontSize) {
            return topY - ((cellHeight - fontSize) / 2f) - 1f;
        }

        private void drawRowBorder(float topY, float height, float[] cols, Color strokeColor) throws IOException {
            float tableWidth = sum(cols);
            float bottomY = topY - height;
            cs.setStrokingColor(strokeColor);
            cs.addRect(margin, bottomY, tableWidth, height);
            cs.stroke();

            float columnX = margin;
            for (int i = 0; i < cols.length - 1; i++) {
                columnX += cols[i];
                cs.moveTo(columnX, bottomY);
                cs.lineTo(columnX, topY);
                cs.stroke();
            }
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
    }
}
