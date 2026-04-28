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

@RestController
@RequestMapping("/exports")
public class ExportController {
    private final TradingService service;

    public ExportController(TradingService service) {
        this.service = service;
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(@RequestParam LocalDate from, @RequestParam LocalDate to) {
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
    public ResponseEntity<byte[]> pdf(@RequestParam LocalDate from, @RequestParam LocalDate to) throws Exception {
        List<TradingDtos.StatementLine> lines = service.statementRange(from, to);
        byte[] out = renderPdf(lines, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement.pdf")
                .header("X-Export-Range", from + "_" + to)
                .contentType(MediaType.APPLICATION_PDF)
                .body(out);
    }

    private byte[] renderPdf(List<TradingDtos.StatementLine> lines, LocalDate from, LocalDate to) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.beginText();
                cs.newLineAtOffset(50, 790);
                cs.showText("Doller Platform - Statement Report");
                cs.endText();

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.beginText();
                cs.newLineAtOffset(50, 772);
                cs.showText("Range: " + from + " to " + to);
                cs.endText();

                float y = 748;
                for (TradingDtos.StatementLine line : lines) {
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
}
