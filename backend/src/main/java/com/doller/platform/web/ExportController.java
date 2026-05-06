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
import java.math.BigDecimal;
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
            @RequestParam(value = "sortDirection", required = false) String sortDirection,
            @RequestParam(value = "companyId", required = false) Long companyId,
            @RequestParam(value = "entryTypeFilter", required = false) String entryTypeFilter
    ) throws Exception {
        String normalizedType = reportType == null ? "BALANCE_SHEET" : reportType.trim().toUpperCase(Locale.ROOT);
        byte[] out;
        String filename;
        String rangeHeader;
        if ("TRANSACTION_DETAILS".equals(normalizedType)) {
            TradingDtos.TransactionExportReport response = service.transactionExportReport(from, to, type, partyId, search, sortField, sortDirection);
            out = renderTransactionPdf(response);
            filename = transactionPdfFilename(response);
            rangeHeader = response.from() + "_" + response.to();
        } else if ("CUSTOM_ENTRIES_ALL".equals(normalizedType)) {
            TradingDtos.CustomEntryAllExportReport response = service.customEntriesAllExportReport(from, to, entryTypeFilter, search);
            out = renderAllCustomEntriesPdf(response);
            filename = customEntriesAllPdfFilename(response);
            rangeHeader = response.from() + "_" + response.to();
        } else if ("CUSTOM_ENTRIES".equals(normalizedType)) {
            TradingDtos.CustomEntryExportReport response = service.customEntriesExportReport(companyId, from, to, entryTypeFilter);
            out = renderCustomEntriesPdf(response);
            filename = customEntriesPdfFilename(response);
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

    private String customEntriesPdfFilename(TradingDtos.CustomEntryExportReport report) {
        String dateRange = report.from() + "_" + report.to();
        String companySlug = sanitizeForFilename(report.companyName());
        if (!companySlug.isBlank()) {
            return companySlug + "_custom_entries_" + dateRange + ".pdf";
        }
        return "custom_entries_" + dateRange + ".pdf";
    }

    private String customEntriesAllPdfFilename(TradingDtos.CustomEntryAllExportReport report) {
        return "custom_entries_all_" + report.from() + "_" + report.to() + ".pdf";
    }

    private String transactionPdfFilename(TradingDtos.TransactionExportReport report) {
        String dateRange = report.from() + "_" + report.to();
        if (report.partySections() != null && report.partySections().size() == 1) {
            String partyName = report.partySections().get(0).party() == null
                    ? null
                    : report.partySections().get(0).party().partyName();
            String partySlug = sanitizeForFilename(partyName);
            if (!partySlug.isBlank()) {
                return partySlug + "_details_" + dateRange + ".pdf";
            }
        }
        return "statement_details_" + dateRange + ".pdf";
    }

    private String sanitizeForFilename(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        normalized = normalized.replaceAll("[^a-z0-9\\s_-]", "");
        normalized = normalized.replaceAll("\\s+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^-+|_+|-+$", "");
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > 60) {
            normalized = normalized.substring(0, 60);
            normalized = normalized.replaceAll("_+$", "");
        }
        return normalized;
    }

    private byte[] renderBalanceSheetPdf(TradingDtos.BalanceSheetResponse response) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD), 14);
                cs.beginText();
                cs.newLineAtOffset(50, 790);
                cs.showText("NexPay - Balance Sheet");
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

    private byte[] renderCustomEntriesPdf(TradingDtos.CustomEntryExportReport response) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
            PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.COURIER);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50f;
                float y = page.getMediaBox().getHeight() - 50f;
                DecimalFormat bdtFormat = new DecimalFormat("#,##0.00");

                // Header with logo
                byte[] logo = null;
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("pdf/logo.jpeg")) {
                    if (in != null) logo = in.readAllBytes();
                }
                float logoSize = 34f;
                float brandY = y - 16;
                if (logo != null) {
                    try {
                        PDImageXObject img = PDImageXObject.createFromByteArray(doc, logo, "logo");
                        cs.drawImage(img, margin, y - logoSize, logoSize, logoSize);
                    } catch (IOException ignored) {}
                }
                text(cs, "NexPay", margin + logoSize + 8, brandY, boldFont, 21f, new Color(17, 48, 87));
                text(cs, "Custom Profit/Cost Report", margin + logoSize + 8, brandY - 22, regularFont, 12f, new Color(60, 60, 60));
                y -= 70f;

                // Company name and date range
                text(cs, "Company: " + safe(response.companyName()), margin, y, boldFont, 12f, new Color(17, 48, 87));
                y -= 18f;
                text(cs, "Period: " + response.from() + " to " + response.to(), margin, y, regularFont, 10f, Color.GRAY);
                y -= 18f;
                if (response.entryTypeFilter() != null && !response.entryTypeFilter().isBlank()) {
                    String filterLabel = "PROFIT_ONLY".equalsIgnoreCase(response.entryTypeFilter()) ? "Profit Only" : "LOSS_ONLY".equalsIgnoreCase(response.entryTypeFilter()) ? "Loss Only" : response.entryTypeFilter();
                    text(cs, "Filter: " + filterLabel, margin, y, regularFont, 10f, Color.GRAY);
                    y -= 18f;
                }
                y -= 10f;

                // Summary cards
                float cardWidth = (page.getMediaBox().getWidth() - margin * 2 - 20f) / 3;
                float cardHeight = 50f;
                float profitCardX = margin;
                float costCardX = margin + cardWidth + 10f;
                float netCardX = margin + (cardWidth + 10f) * 2;

                // Profit card (green)
                fill(cs, profitCardX, y - cardHeight, cardWidth, cardHeight, new Color(222, 244, 235));
                text(cs, "Total Profit", profitCardX + 10, y - 18, boldFont, 9f, new Color(25, 109, 73));
                text(cs, "+" + bdtFormat.format(response.totalProfitBdt()), profitCardX + 10, y - 38, boldFont, 14f, new Color(25, 109, 73));

                // Cost card (red)
                fill(cs, costCardX, y - cardHeight, cardWidth, cardHeight, new Color(249, 229, 232));
                text(cs, "Total Cost", costCardX + 10, y - 18, boldFont, 9f, new Color(153, 39, 48));
                text(cs, "-" + bdtFormat.format(response.totalLossBdt()), costCardX + 10, y - 38, boldFont, 14f, new Color(153, 39, 48));

                // Net card
                Color netBgColor = response.netBdt().compareTo(BigDecimal.ZERO) >= 0 ? new Color(226, 236, 249) : new Color(249, 229, 232);
                Color netTextColor = response.netBdt().compareTo(BigDecimal.ZERO) >= 0 ? new Color(27, 61, 107) : new Color(153, 39, 48);
                fill(cs, netCardX, y - cardHeight, cardWidth, cardHeight, netBgColor);
                text(cs, "Net", netCardX + 10, y - 18, boldFont, 9f, netTextColor);
                String netPrefix = response.netBdt().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "-";
                text(cs, netPrefix + bdtFormat.format(response.netBdt().abs()), netCardX + 10, y - 38, boldFont, 14f, netTextColor);
                y -= (cardHeight + 25f);

                // Table header
                String[] headers = {"Date", "Purpose", "Type", "Amount", "Notes"};
                float pageWidth = page.getMediaBox().getWidth();
                float availableWidth = pageWidth - margin * 2;
                float[] cols = fitColumnsToAvailableWidth(new float[]{80, 140, 60, 100, 180}, availableWidth);
                float headerHeight = 20f;
                float tableWidth = sum(cols);
                fill(cs, margin, y - headerHeight, tableWidth, headerHeight, new Color(225, 233, 246));
                float x = margin;
                for (int i = 0; i < headers.length; i++) {
                    textCentered(cs, headers[i], x, cols[i], y - 14, boldFont, 8f, new Color(32, 47, 82));
                    x += cols[i];
                }
                drawRowBorder(cs, margin, y, tableWidth, headerHeight, cols, new Color(184, 196, 214));
                y -= headerHeight;

                // Table rows
                float rowHeight = 18f;
                for (TradingDtos.CustomEntryRow row : response.entries()) {
                    if (y < 60) break;
                    fill(cs, margin, y - rowHeight, tableWidth, rowHeight, Color.WHITE);
                    x = margin;
                    String[] cells = {
                            safe(row.entryTime() != null ? row.entryTime().toLocalDate().toString() : "-"),
                            safe(row.itemPurpose()),
                            safe(row.entryType() != null ? row.entryType().name() : "-"),
                            bdtFormat.format(row.amountBdt()),
                            safe(row.notes())
                    };
                    for (int i = 0; i < cells.length; i++) {
                        if (i == 3) { // Amount column - right aligned
                            textRight(cs, cells[i], x + cols[i] - 5, y - 12, regularFont, 8f, Color.BLACK);
                        } else {
                            text(cs, cells[i], x + 3, y - 12, regularFont, 8f, Color.BLACK);
                        }
                        x += cols[i];
                    }
                    drawRowBorder(cs, margin, y, tableWidth, rowHeight, cols, new Color(214, 220, 230));
                    y -= rowHeight;
                }
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] renderAllCustomEntriesPdf(TradingDtos.CustomEntryAllExportReport response) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
            PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.COURIER);
            DecimalFormat bdtFormat = new DecimalFormat("#,##0.00");
            byte[] logo = loadLogoBytes();

            // Page 1: overall summary
            PDPage summaryPage = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(summaryPage);
            try (PDPageContentStream cs = new PDPageContentStream(doc, summaryPage)) {
                float margin = 50f;
                float y = summaryPage.getMediaBox().getHeight() - 50f;
                y = drawCustomEntriesHeader(doc, cs, y, margin, logo, boldFont, regularFont);
                text(cs, "Company: All Companies", margin, y, boldFont, 12f, new Color(17, 48, 87));
                y -= 18f;
                text(cs, "Period: " + response.from() + " to " + response.to(), margin, y, regularFont, 10f, Color.GRAY);
                y -= 18f;
                if (response.entryTypeFilter() != null && !response.entryTypeFilter().isBlank()) {
                    text(cs, "Filter: " + entryFilterLabel(response.entryTypeFilter()), margin, y, regularFont, 10f, Color.GRAY);
                    y -= 18f;
                }
                if (response.search() != null && !response.search().trim().isBlank()) {
                    text(cs, "Search: " + response.search().trim(), margin, y, regularFont, 10f, Color.GRAY);
                    y -= 18f;
                }
                y -= 10f;
                drawCustomEntriesSummaryCards(
                        cs,
                        summaryPage.getMediaBox().getWidth(),
                        margin,
                        y,
                        response.totalProfitBdt(),
                        response.totalLossBdt(),
                        response.netBdt(),
                        bdtFormat,
                        boldFont
                );
            }

            // One page per company section
            for (TradingDtos.CustomEntryAllCompanySection company : response.companies()) {
                PDPage companyPage = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                doc.addPage(companyPage);
                PDPageContentStream cs = new PDPageContentStream(doc, companyPage);
                try {
                    float margin = 50f;
                    float y = companyPage.getMediaBox().getHeight() - 50f;
                    y = drawCustomEntriesHeader(doc, cs, y, margin, logo, boldFont, regularFont);

                    text(cs, "Company: " + safe(company.companyName()), margin, y, boldFont, 12f, new Color(17, 48, 87));
                    y -= 18f;
                    text(cs, "Period: " + response.from() + " to " + response.to(), margin, y, regularFont, 10f, Color.GRAY);
                    y -= 18f;
                    if (response.entryTypeFilter() != null && !response.entryTypeFilter().isBlank()) {
                        text(cs, "Filter: " + entryFilterLabel(response.entryTypeFilter()), margin, y, regularFont, 10f, Color.GRAY);
                        y -= 18f;
                    }
                    if (response.search() != null && !response.search().trim().isBlank()) {
                        text(cs, "Search: " + response.search().trim(), margin, y, regularFont, 10f, Color.GRAY);
                        y -= 18f;
                    }
                    y -= 10f;

                    drawCustomEntriesSummaryCards(
                            cs,
                            companyPage.getMediaBox().getWidth(),
                            margin,
                            y,
                            company.totalProfitBdt(),
                            company.totalLossBdt(),
                            company.netBdt(),
                            bdtFormat,
                            boldFont
                    );
                    y -= (50f + 25f);

                    String[] headers = {"Date", "Purpose", "Type", "Amount", "Notes"};
                    float availableWidth = companyPage.getMediaBox().getWidth() - margin * 2;
                    float[] cols = fitColumnsToAvailableWidth(new float[]{80, 140, 60, 100, 180}, availableWidth);
                    float headerHeight = 20f;
                    float tableWidth = sum(cols);
                    y = drawCustomEntriesTableHeader(cs, margin, y, tableWidth, cols, headers, boldFont);

                    float rowHeight = 18f;
                    for (TradingDtos.CustomEntryRow row : company.entries()) {
                        if (y < 60) {
                            cs.close();
                            PDPage continuedPage = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                            doc.addPage(continuedPage);
                            cs = new PDPageContentStream(doc, continuedPage);
                            y = continuedPage.getMediaBox().getHeight() - 50f;
                            y = drawCustomEntriesHeader(doc, cs, y, margin, logo, boldFont, regularFont);
                            text(cs, "Company: " + safe(company.companyName()) + " (continued)", margin, y, boldFont, 11f, new Color(17, 48, 87));
                            y -= 24f;
                            y = drawCustomEntriesTableHeader(cs, margin, y, tableWidth, cols, headers, boldFont);
                        }
                        fill(cs, margin, y - rowHeight, tableWidth, rowHeight, Color.WHITE);
                        float x = margin;
                        String[] cells = {
                                safe(row.entryTime() != null ? row.entryTime().toLocalDate().toString() : "-"),
                                safe(row.itemPurpose()),
                                safe(row.entryType() != null ? row.entryType().name() : "-"),
                                bdtFormat.format(row.amountBdt()),
                                safe(row.notes())
                        };
                        for (int i = 0; i < cells.length; i++) {
                            if (i == 3) {
                                textRight(cs, cells[i], x + cols[i] - 5, y - 12, regularFont, 8f, Color.BLACK);
                            } else {
                                text(cs, cells[i], x + 3, y - 12, regularFont, 8f, Color.BLACK);
                            }
                            x += cols[i];
                        }
                        drawRowBorder(cs, margin, y, tableWidth, rowHeight, cols, new Color(214, 220, 230));
                        y -= rowHeight;
                    }
                } finally {
                    cs.close();
                }
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private float drawCustomEntriesTableHeader(
            PDPageContentStream cs,
            float margin,
            float y,
            float tableWidth,
            float[] cols,
            String[] headers,
            PDType1Font boldFont
    ) throws IOException {
        float headerHeight = 20f;
        fill(cs, margin, y - headerHeight, tableWidth, headerHeight, new Color(225, 233, 246));
        float x = margin;
        for (int i = 0; i < headers.length; i++) {
            textCentered(cs, headers[i], x, cols[i], y - 14, boldFont, 8f, new Color(32, 47, 82));
            x += cols[i];
        }
        drawRowBorder(cs, margin, y, tableWidth, headerHeight, cols, new Color(184, 196, 214));
        return y - headerHeight;
    }

    private byte[] loadLogoBytes() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("pdf/logo.jpeg")) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException ignored) {
            // text-only fallback
        }
        return null;
    }

    private float drawCustomEntriesHeader(
            PDDocument doc,
            PDPageContentStream cs,
            float y,
            float margin,
            byte[] logo,
            PDType1Font boldFont,
            PDType1Font regularFont
    ) throws IOException {
        float logoSize = 34f;
        float brandY = y - 16;
        if (logo != null) {
            try {
                PDImageXObject img = PDImageXObject.createFromByteArray(doc, logo, "logo");
                cs.drawImage(img, margin, y - logoSize, logoSize, logoSize);
            } catch (IOException ignored) {
                // text-only fallback
            }
        }
        text(cs, "NexPay", margin + logoSize + 8, brandY, boldFont, 21f, new Color(17, 48, 87));
        text(cs, "Custom Profit/Cost Report", margin + logoSize + 8, brandY - 22, regularFont, 12f, new Color(60, 60, 60));
        return y - 70f;
    }

    private void drawCustomEntriesSummaryCards(
            PDPageContentStream cs,
            float pageWidth,
            float margin,
            float y,
            BigDecimal totalProfit,
            BigDecimal totalLoss,
            BigDecimal net,
            DecimalFormat bdtFormat,
            PDType1Font boldFont
    ) throws IOException {
        float cardWidth = (pageWidth - margin * 2 - 20f) / 3;
        float cardHeight = 50f;
        float profitCardX = margin;
        float costCardX = margin + cardWidth + 10f;
        float netCardX = margin + (cardWidth + 10f) * 2;

        fill(cs, profitCardX, y - cardHeight, cardWidth, cardHeight, new Color(222, 244, 235));
        text(cs, "Total Profit", profitCardX + 10, y - 18, boldFont, 9f, new Color(25, 109, 73));
        text(cs, "+" + bdtFormat.format(totalProfit), profitCardX + 10, y - 38, boldFont, 14f, new Color(25, 109, 73));

        fill(cs, costCardX, y - cardHeight, cardWidth, cardHeight, new Color(249, 229, 232));
        text(cs, "Total Cost", costCardX + 10, y - 18, boldFont, 9f, new Color(153, 39, 48));
        text(cs, "-" + bdtFormat.format(totalLoss), costCardX + 10, y - 38, boldFont, 14f, new Color(153, 39, 48));

        Color netBgColor = net.compareTo(BigDecimal.ZERO) >= 0 ? new Color(226, 236, 249) : new Color(249, 229, 232);
        Color netTextColor = net.compareTo(BigDecimal.ZERO) >= 0 ? new Color(27, 61, 107) : new Color(153, 39, 48);
        fill(cs, netCardX, y - cardHeight, cardWidth, cardHeight, netBgColor);
        text(cs, "Net", netCardX + 10, y - 18, boldFont, 9f, netTextColor);
        String netPrefix = net.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "-";
        text(cs, netPrefix + bdtFormat.format(net.abs()), netCardX + 10, y - 38, boldFont, 14f, netTextColor);
    }

    private String entryFilterLabel(String entryTypeFilter) {
        if ("PROFIT_ONLY".equalsIgnoreCase(entryTypeFilter)) return "Profit Only";
        if ("LOSS_ONLY".equalsIgnoreCase(entryTypeFilter)) return "Loss Only";
        return entryTypeFilter;
    }

    private void text(PDPageContentStream cs, String value, float x, float y, PDType1Font font, float size, Color color) throws IOException {
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(value == null ? "" : value);
        cs.endText();
    }

    private void textRight(PDPageContentStream cs, String value, float rightX, float y, PDType1Font font, float size, Color color) throws IOException {
        float w = font.getStringWidth(value) / 1000f * size;
        text(cs, value, rightX - w, y, font, size, color);
    }

    private void textCentered(PDPageContentStream cs, String value, float cellX, float cellWidth, float y, PDType1Font font, float size, Color color) throws IOException {
        String safeValue = value == null ? "" : value;
        float textWidth = font.getStringWidth(safeValue) / 1000f * size;
        float centeredX = cellX + Math.max((cellWidth - textWidth) / 2f, 0f);
        text(cs, safeValue, centeredX, y, font, size, color);
    }

    private void fill(PDPageContentStream cs, float x, float y, float width, float height, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, y, width, height);
        cs.fill();
    }

    private void drawRowBorder(PDPageContentStream cs, float margin, float topY, float tableWidth, float height, float[] cols, Color strokeColor) throws IOException {
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

    private float sum(float[] cols) {
        float out = 0;
        for (float col : cols) out += col;
        return out;
    }

    private float[] fitColumnsToAvailableWidth(float[] cols, float availableWidth) {
        float total = sum(cols);
        if (total <= 0 || availableWidth <= 0) return cols;
        float scale = availableWidth / total;
        float[] out = new float[cols.length];
        for (int i = 0; i < cols.length; i++) out[i] = cols[i] * scale;
        return out;
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static final class TransactionPdfWriter {
        private final PDDocument doc;
        private final TradingDtos.TransactionExportReport report;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.COURIER);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
        private final DecimalFormat bdt = new DecimalFormat("#,##0.00");
        private final float margin = 34f;
        private final float topPadding = 36f;
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
        private final float exposureHeight = 72f;
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
            page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - topPadding;
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
            Color customerCardBg = new Color(165, 216, 188);
            Color customerTitleColor = new Color(27, 82, 57);
            Color customerMetaColor = new Color(42, 102, 72);
            fill(margin, y - cardHeight, cardWidth, cardHeight, customerCardBg);
            float innerX = margin + cardPadding;
            float phoneX = innerX;
            float addressX = margin + cardWidth * 0.38f;
            text("Client Name: " + safe(section.party().partyName()), innerX, y - 22, bold, 11, customerTitleColor);
            text("Client Mobile Number: " + safe(section.party().phone()), phoneX, y - 42, regular, 9, customerMetaColor);
            String address = "Client Address: " + safe(section.party().address());
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
            String[] headers = {"Deal ID", "Date", "Time", "Direction", "Instrument/Currency", "Amount Foreign Currency", "Rate BDT", "Return Currency", "Amount BDT"};
            float[] cols = fitColumnsToAvailableWidth(new float[]{40, 46, 46, 54, 76, 92, 48, 70, 122});
            tableHeader(headers, cols);
            y -= firstRowTopGap;
            for (TradingDtos.TransactionDealExportRow r : rows) {
                ensure(tableRowHeight + 2);
                String[] cells = {
                        safe(r.dealId()),
                        safe(r.date()),
                        safe(r.time()),
                        safe(r.direction()),
                        safe(r.currencyCode()),
                        fmt(r.quantity()),
                        fmt(r.bdtRate()),
                        "BDT",
                        fmt(r.amountBdt())
                };
                tableRow(cells, cols, List.of(5, 6, 8));
            }
            y -= tableToSummaryGap;
            dealSummary(summary);
        }

        private void drawSettlementTable(List<TradingDtos.TransactionSettlementExportRow> rows, TradingDtos.TransactionSettlementSummary summary) throws IOException {
            ensure(80);
            sectionTitleCard("Settlements");
            String[] headers = {"Settlement ID", "Date", "Time", "Direction", "Payment Method", "Payment Reference", "Related Deal ID", "Amount BDT"};
            float[] cols = fitColumnsToAvailableWidth(new float[]{66, 58, 54, 126, 66, 90, 62, 84});
            tableHeader(headers, cols);
            y -= firstRowTopGap;
            for (TradingDtos.TransactionSettlementExportRow r : rows) {
                ensure(tableRowHeight + 2);
                String[] cells = {
                        safe(r.settlementId()),
                        safe(r.date()),
                        safe(r.time()),
                        safe(r.direction()),
                        paymentMethodLabel(r.paymentMethod()),
                        safe(r.paymentReference()),
                        safe(r.relatedDealId()),
                        fmt(r.amountBdt())
                };
                tableRow(cells, cols, List.of(7));
            }
            y -= tableToSummaryGap;
            settlementSummary(summary);
        }

        private void drawExposure(String title, TradingDtos.TransactionPartyExposureSummary b) throws IOException {
            ensure(80);
            // Title centered across full width
            float pageWidth = page.getMediaBox().getWidth() - (margin * 2);
            float titleWidth = bold.getStringWidth(title) / 1000f * 11f;
            float centeredX = margin + (pageWidth - titleWidth) / 2f;
            text(title, centeredX, y - 14, bold, 11, new Color(17, 48, 87));
            y -= (22f + sectionTitleGap);
            float[] cols = fitColumnsToAvailableWidth(new float[]{100, 120, 120, 120});
            float rowHeight = 20f;

            // Table header row
            fill(margin, y - rowHeight, sum(cols), rowHeight, new Color(225, 233, 246));
            float x = margin;
            String[] headers = {"Metric", "Receivable", "Payable", "Net"};
            for (int i = 0; i < headers.length; i++) {
                if (i == 0) {
                    text(headers[i], x + 4, y - 14, bold, 9f, new Color(32, 47, 82));
                } else {
                    textRight(headers[i], x + cols[i] - 5, y - 14, bold, 9f, new Color(32, 47, 82));
                }
                x += cols[i];
            }
            drawRowBorder(y, rowHeight, cols, new Color(184, 196, 214));
            y -= rowHeight;

            // Before row
            fill(margin, y - rowHeight, sum(cols), rowHeight, Color.WHITE);
            x = margin;
            String[] beforeCells = {"Before", fmt(b.beforeReceivableBdt()), fmt(b.beforePayableBdt()), "-"};
            for (int i = 0; i < beforeCells.length; i++) {
                if (i == 0) {
                    text(beforeCells[i], x + 4, y - 14, regular, 9f, Color.BLACK);
                } else {
                    textRight(beforeCells[i], x + cols[i] - 5, y - 14, regular, 9f, Color.BLACK);
                }
                x += cols[i];
            }
            drawRowBorder(y, rowHeight, cols, new Color(214, 220, 230));
            y -= rowHeight;

            // Now row
            fill(margin, y - rowHeight, sum(cols), rowHeight, Color.WHITE);
            x = margin;
            String[] nowCells = {"Now", fmt(b.receivableBdt()), fmt(b.payableBdt()), fmt(b.netBalanceBdt())};
            for (int i = 0; i < nowCells.length; i++) {
                if (i == 0) {
                    text(nowCells[i], x + 4, y - 14, regular, 9f, Color.BLACK);
                } else {
                    textRight(nowCells[i], x + cols[i] - 5, y - 14, regular, 9f, Color.BLACK);
                }
                x += cols[i];
            }
            drawRowBorder(y, rowHeight, cols, new Color(214, 220, 230));
            y -= (rowHeight + 10);
        }

        private void dealSummary(TradingDtos.TransactionDealSummary s) throws IOException {
            ensure(summaryHeight + 10);
            float cardWidth = page.getMediaBox().getWidth() - margin * 2;
            float contentHeight = drawMetricRowHeight(
                    new String[]{"Deal Count", "Total Buy", "Total Sell", "", "Net Exposure"},
                    new String[]{String.valueOf(s.count()), fmt(s.totalBuyBdt()), fmt(s.totalSellBdt()), "", fmt(s.netDealExposureBdt())}
            );
            float cardHeight = Math.max(summaryHeight, contentHeight + 8f);
            ensure(cardHeight + 10);
            fill(margin, y - cardHeight, cardWidth, cardHeight, new Color(236, 243, 252));
            drawMetricRow(
                    y,
                    cardHeight,
                    new String[]{"Deal Count", "Total Buy", "Total Sell", "", "Net Exposure"},
                    new String[]{String.valueOf(s.count()), fmt(s.totalBuyBdt()), fmt(s.totalSellBdt()), "", fmt(s.netDealExposureBdt())},
                    new Color[]{new Color(27, 61, 107), new Color(25, 109, 73), new Color(153, 39, 48), new Color(56, 69, 86), new Color(56, 69, 86)},
                    new Color[]{new Color(226, 236, 249), new Color(222, 244, 235), new Color(249, 229, 232), new Color(231, 236, 242), new Color(231, 236, 242)}
            );
            y -= (cardHeight + 8);
        }

        private void settlementSummary(TradingDtos.TransactionSettlementSummary s) throws IOException {
            ensure(summaryHeight + 10);
            float cardWidth = page.getMediaBox().getWidth() - margin * 2;
            float contentHeight = drawMetricRowHeight(
                    new String[]{"Settle Count", "Incoming", "Outgoing", "Linked", "Unlinked"},
                    new String[]{String.valueOf(s.count()), fmt(s.totalIncomingBdt()), fmt(s.totalOutgoingBdt()), String.valueOf(s.linkedCount()), String.valueOf(s.unlinkedCount())}
            );
            float cardHeight = Math.max(summaryHeight, contentHeight + 8f);
            ensure(cardHeight + 10);
            fill(margin, y - cardHeight, cardWidth, cardHeight, new Color(236, 243, 252));
            drawMetricRow(
                    y,
                    cardHeight,
                    new String[]{"Settle Count", "Incoming", "Outgoing", "Linked", "Unlinked"},
                    new String[]{String.valueOf(s.count()), fmt(s.totalIncomingBdt()), fmt(s.totalOutgoingBdt()), String.valueOf(s.linkedCount()), String.valueOf(s.unlinkedCount())},
                    new Color[]{new Color(27, 61, 107), new Color(25, 109, 73), new Color(153, 39, 48), new Color(56, 69, 86), new Color(90, 92, 102)},
                    new Color[]{new Color(226, 236, 249), new Color(222, 244, 235), new Color(249, 229, 232), new Color(231, 236, 242), new Color(240, 241, 246)}
            );
            y -= (cardHeight + 8);
        }

        private float drawMetricRowHeight(String[] labels, String[] values) throws IOException {
            float gutter = 6f;
            float availableWidth = page.getMediaBox().getWidth() - (margin * 2) - 16f;
            int count = labels.length;
            float chipWidth = (availableWidth - (gutter * (count - 1))) / count;
            int maxLines = 1;
            for (int i = 0; i < count; i++) {
                String chipText = labels[i] + ": " + values[i];
                if (labels[i].isBlank() && values[i].isBlank()) {
                    continue;
                }
                int lines = wrapLines(chipText, chipWidth - 8f, 9f, 4).size();
                if (lines > maxLines) {
                    maxLines = lines;
                }
            }
            return Math.max(summaryHeight - 8f, 8f + (maxLines * 11f));
        }

        private void drawMetricRow(float topY, float cardHeight, String[] labels, String[] values, Color[] textColors, Color[] bgColors) throws IOException {
            float gutter = 6f;
            float startX = margin + 8f;
            float availableWidth = page.getMediaBox().getWidth() - (margin * 2) - 16f;
            int count = labels.length;
            float chipWidth = (availableWidth - (gutter * (count - 1))) / count;
            float innerTopY = topY - 4f;
            float innerHeight = cardHeight - 8f;
            float x = startX;
            for (int i = 0; i < count; i++) {
                drawMetricChip(labels[i], values[i], x, innerTopY, innerHeight, chipWidth, 9f, textColors[i], bgColors[i]);
                x += chipWidth + gutter;
            }
        }

        private void drawMetricChip(String label, String value, float x, float topY, float height, float width, float fontSize,
                                    Color textColor, Color bgColor) throws IOException {
            String chipText = label + ": " + value;
            if (label.isBlank() && value.isBlank()) {
                return;
            }
            List<String> lines = wrapLines(chipText, width - 8f, fontSize, 4);
            float lineGap = fontSize + 2f;
            float totalHeight = lines.size() * lineGap;
            float firstBaseline = topY - ((height - totalHeight) / 2f) - fontSize;
            for (int i = 0; i < lines.size(); i++) {
                textCentered(lines.get(i), x, width, firstBaseline - (i * lineGap), bold, fontSize, textColor);
            }
        }

        private void drawGrandSummary() throws IOException {
            ensure(150);
            line(y + 4);
            y -= 10;
            dealSummary(report.grandDealSummary());
            settlementSummary(report.grandSettlementSummary());
            drawExposure("Grand Exposure Summary", report.grandExposureSummary());
        }

        private void tableHeader(String[] headers, float[] cols) throws IOException {
            int maxLines = 1;
            for (int i = 0; i < headers.length; i++) {
                int lines = wrapLines(headers[i], cols[i] - 4f, 8f, 2).size();
                if (lines > maxLines) {
                    maxLines = lines;
                }
            }
            float headerHeight = tableHeaderHeight * maxLines;
            ensure(headerHeight + 2);
            float tableWidth = sum(cols);
            fill(margin, y - headerHeight + 1, tableWidth, headerHeight, new Color(225, 233, 246));
            float x = margin;
            float lineGap = 8.5f;
            float firstBaseline = y - 11f;
            for (int i = 0; i < headers.length; i++) {
                List<String> lines = wrapLines(headers[i], cols[i] - 4f, 8f, 2);
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    textCentered(
                            lines.get(lineIndex),
                            x,
                            cols[i],
                            firstBaseline - (lineIndex * lineGap),
                            bold,
                            8,
                            new Color(32, 47, 82)
                    );
                }
                x += cols[i];
            }
            drawRowBorder(y, headerHeight, cols, new Color(184, 196, 214));
            y -= headerHeight;
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
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                int lines = wrapLines(cells[i], cols[i] - 4f, 8f, 2).size();
                if (lines > maxLines) {
                    maxLines = lines;
                }
            }
            float rowHeight = tableRowHeight * maxLines;
            ensure(rowHeight + 2);

            float rowTop = y;
            float x = margin + 2;
            float lineGap = 8.5f;
            float firstBaseline = rowTop - 11f;
            for (int i = 0; i < cells.length; i++) {
                List<String> lines = wrapLines(cells[i], cols[i] - 4f, 8f, 2);
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    String value = lines.get(lineIndex);
                    float lineY = firstBaseline - (lineIndex * lineGap);
                    if (rightAlignCols.contains(i)) {
                        textRight(value, x + cols[i] - 3, lineY, regular, 8, Color.BLACK);
                    } else {
                        text(value, x, lineY, regular, 8, Color.BLACK);
                    }
                }
                x += cols[i];
            }
            drawRowBorder(rowTop, rowHeight, cols, new Color(214, 220, 230));
            y -= rowHeight;
        }

        private float centeredTextY(float topY, float cellHeight, float fontSize) {
            return topY - ((cellHeight - fontSize) / 2f) - 1f;
        }

        private float centeredTextBaselineY(float topY, float cellHeight, PDType1Font font, float fontSize) {
            float ascent = (font.getFontDescriptor().getAscent() / 1000f) * fontSize;
            float descent = (font.getFontDescriptor().getDescent() / 1000f) * fontSize;
            float textHeight = ascent - descent;
            float topInset = (cellHeight - textHeight) / 2f;
            return topY - topInset - ascent;
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

        private void textCentered(String value, float cellX, float cellWidth, float yAt,
                                  PDType1Font font, float size, Color color) throws IOException {
            String safeValue = value == null ? "" : value;
            float textWidth = font.getStringWidth(safeValue) / 1000f * size;
            float centeredX = cellX + Math.max((cellWidth - textWidth) / 2f, 0f);
            text(safeValue, centeredX, yAt, font, size, color);
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

        private float[] fitColumnsToAvailableWidth(float[] cols) {
            float total = sum(cols);
            float available = page.getMediaBox().getWidth() - (margin * 2);
            if (total <= 0 || available <= 0) {
                return cols;
            }
            float scale = available / total;
            float[] out = new float[cols.length];
            for (int i = 0; i < cols.length; i++) {
                out[i] = cols[i] * scale;
            }
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

        private String paymentMethodLabel(String value) {
            if (value == null || value.isBlank()) {
                return "-";
            }
            return "CHECK".equalsIgnoreCase(value) ? "CHEQUE" : value;
        }

        private List<String> wrapLines(String value, float colWidth, float fontSize, int maxLines) throws IOException {
            String text = safe(value);
            if (text.isBlank()) {
                return List.of("-");
            }
            if (regular.getStringWidth(text) / 1000f * fontSize <= colWidth) {
                return List.of(text);
            }

            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            String[] words = text.split("\\s+");
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (regular.getStringWidth(candidate) / 1000f * fontSize <= colWidth) {
                    current = new StringBuilder(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        if (lines.size() >= maxLines) {
                            break;
                        }
                        current = new StringBuilder(word);
                    } else {
                        lines.add(truncate(word, colWidth, fontSize));
                        if (lines.size() >= maxLines) {
                            break;
                        }
                    }
                }
            }

            if (lines.size() < maxLines && !current.isEmpty()) {
                lines.add(current.toString());
            } else if (lines.size() >= maxLines) {
                int last = lines.size() - 1;
                lines.set(last, truncate(lines.get(last), colWidth, fontSize));
            }
            return lines.isEmpty() ? List.of(truncate(text, colWidth, fontSize)) : lines;
        }
    }
}
