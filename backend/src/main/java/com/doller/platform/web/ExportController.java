package com.doller.platform.web;

import com.doller.platform.dto.TradingDtos;
import com.doller.platform.service.TradingService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
        StringBuilder sb = new StringBuilder("date,openingCash,closingCash,openingUsd,closingUsd,pnl\n");
        lines.forEach(l -> sb.append(l.date()).append(',').append(l.openingCash()).append(',').append(l.closingCash()).append(',')
                .append(l.openingUsd()).append(',').append(l.closingUsd()).append(',').append(l.pnl()).append('\n'));
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
            TradingDtos.TransactionDetailsResponse response = service.transactionDetails(from, to, type, partyId, search, sortField, sortDirection);
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
                cs.showText(String.format("Open Cash=%s | Close Cash=%s | Open USD=%s | Close USD=%s | Total P/L=%s",
                        response.openingCash(), response.closingCash(), response.openingUsd(), response.closingUsd(), response.totalPnl()));
                cs.endText();

                float y = 732;
                for (TradingDtos.StatementLine line : response.lines()) {
                    if (y < 60) break;
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(String.format("%s | openCash=%s | closeCash=%s | openUsd=%s | closeUsd=%s | pnl=%s",
                            line.date(), line.openingCash(), line.closingCash(), line.openingUsd(), line.closingUsd(), line.pnl()));
                    cs.endText();
                    y -= 16;
                }
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] renderTransactionPdf(TradingDtos.TransactionDetailsResponse response) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.beginText();
                cs.newLineAtOffset(50, 790);
                cs.showText("Doller Platform - Transaction Details");
                cs.endText();

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.beginText();
                cs.newLineAtOffset(50, 772);
                cs.showText("Range: " + response.from() + " to " + response.to());
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(50, 756);
                cs.showText(String.format("Type=%s | Search=%s | Sort=%s %s",
                        response.typeFilter() == null || response.typeFilter().isBlank() ? "ALL" : response.typeFilter(),
                        response.search() == null || response.search().isBlank() ? "-" : response.search(),
                        response.sortField(),
                        response.sortDirection()));
                cs.endText();

                float y = 732;
                for (TradingDtos.TransactionDetailRow row : response.rows()) {
                    if (y < 60) break;
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(String.format("%s | %s | %s | %s | amount=%s",
                            row.occurredAt(), row.entryType(), row.partyName(), row.directionLabel(), row.amountBdt()));
                    cs.endText();
                    y -= 14;
                }
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
