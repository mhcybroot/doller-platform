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

                cs.beginText();
                cs.newLineAtOffset(50, 692);
                cs.showText(String.format("Open Aging Buckets 0-3=%s 4-7=%s 8-15=%s 15-30=%s 30+=%s",
                        response.openingAgingBuckets().days0To3Bdt(),
                        response.openingAgingBuckets().days4To7Bdt(),
                        response.openingAgingBuckets().days8To15Bdt(),
                        response.openingAgingBuckets().days15To30Bdt(),
                        response.openingAgingBuckets().days30PlusBdt()));
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(50, 676);
                cs.showText(String.format("Close Aging Buckets 0-3=%s 4-7=%s 8-15=%s 15-30=%s 30+=%s",
                        response.closingAgingBuckets().days0To3Bdt(),
                        response.closingAgingBuckets().days4To7Bdt(),
                        response.closingAgingBuckets().days8To15Bdt(),
                        response.closingAgingBuckets().days15To30Bdt(),
                        response.closingAgingBuckets().days30PlusBdt()));
                cs.endText();

                float y = 648;
                for (TradingDtos.StatementLine line : response.lines()) {
                    if (y < 60) break;
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(String.format("%s | cash %s->%s | usd %s->%s | rec %s->%s | pay %s->%s | pnl=%s",
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
