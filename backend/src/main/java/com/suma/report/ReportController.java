package com.suma.report;

import com.suma.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports & Analytics")
public class ReportController {
    private final OperationalReportService service;

    public ReportController(OperationalReportService service) {
        this.service = service;
    }

    @GetMapping("/{type}")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "type: productivity, sla, pending, ageing, client, project, team, daily, weekly, monthly")
    public OperationalReportService.Report run(@PathVariable String type,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID clientId,
                                               @RequestParam(required = false) UUID teamId) {
        return service.run(CurrentUser.get(), type, from, to, projectId, clientId, teamId);
    }

    @GetMapping("/{type}/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID clientId,
                                         @RequestParam(required = false) UUID teamId) throws IOException {
        var r = service.run(CurrentUser.get(), type, from, to, projectId, clientId, teamId);
        String base = "report-" + type + "-" + r.from() + "_" + r.to();
        if ("xlsx".equals(format)) {
            try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
                Sheet s = wb.createSheet(r.title());
                Row h = s.createRow(0);
                for (int i = 0; i < r.columns().size(); i++) h.createCell(i).setCellValue(r.columns().get(i));
                int n = 1;
                for (var row : r.rows()) {
                    Row x = s.createRow(n++);
                    for (int i = 0; i < r.columns().size(); i++) {
                        Object v = row.get(r.columns().get(i));
                        if (v instanceof Number num) x.createCell(i).setCellValue(num.doubleValue());
                        else x.createCell(i).setCellValue(v == null ? "" : v.toString());
                    }
                }
                wb.write(out);
                return file(out.toByteArray(), base + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }
        }
        try (var out = new ByteArrayOutputStream(); var w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             var p = new CSVPrinter(w, CSVFormat.DEFAULT.builder().setHeader(r.columns().toArray(String[]::new)).build())) {
            for (var row : r.rows()) {
                p.printRecord(r.columns().stream().map(c -> {
                    Object v = row.get(c);
                    String s = v == null ? "" : v.toString();
                    return !s.isEmpty() && "=+@".indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
                }).toList());
            }
            p.flush();
            return file(out.toByteArray(), base + ".csv", "text/csv");
        }
    }

    private static ResponseEntity<byte[]> file(byte[] content, String name, String type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType(type)).body(content);
    }
}
