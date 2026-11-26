package com.suma.attendance;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Renders attendance reports to CSV, Excel (XLSX) and PDF. Times are shown in the report's reporting timezone. */
@Service
public class AttendanceExportService {
    public record Export(byte[] content, String fileName, String contentType) {}

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final List<String> DAILY_HEADERS = List.of("Employee ID", "Employee Name", "Project", "Team", "Date",
            "Login Time", "Logout Time", "Session Duration", "Sessions", "Number of Breaks", "Break Timings",
            "Total Break Duration", "Average Break", "Idle Events", "Idle Duration", "Attendance Exceptions", "Missing Records");

    // ------------------------------------------------------------------ daily

    public Export daily(AttendanceReportService.DailyReport r, String format) {
        ZoneId z = ZoneId.of(r.timezone());
        List<List<String>> rows = r.rows().stream().map(x -> dailyCells(x.employee(), x.day(), z)).toList();
        String base = "attendance-daily-" + r.date();
        String title = "Daily Attendance Report - " + r.date() + " (" + r.timezone() + ")";
        return switch (format) {
            case "csv" -> new Export(csv(DAILY_HEADERS, rows), base + ".csv", "text/csv");
            case "xlsx" -> new Export(xlsx(List.of(new Sheet("Daily attendance", DAILY_HEADERS, rows))), base + ".xlsx", XLSX);
            case "pdf" -> new Export(pdf(title, summaryLine(r.totals()), DAILY_HEADERS, rows, r.generatedAt(), z), base + ".pdf", "application/pdf");
            default -> throw new IllegalArgumentException("format must be csv, xlsx or pdf");
        };
    }

    // ------------------------------------------------------------------ monthly

    private static final List<String> MONTHLY_SUMMARY = List.of("Employee ID", "Employee Name", "Project", "Team", "Days Present",
            "Total Working Session", "Number of Breaks", "Total Break Duration", "Average Break Duration", "Idle Events",
            "Idle Duration", "Breaks Exceeding Limit", "Attendance Exceptions", "Missing Logout Records", "Days Without Login");

    private static final List<String> BREAK_HEADERS = List.of("Employee ID", "Employee Name", "Date", "Category", "Break Start",
            "Break End", "Duration", "Status", "Exceeded Limit");

    private static final List<String> IDLE_HEADERS = List.of("Employee ID", "Employee Name", "Date", "Idle Start", "Idle End",
            "Duration", "Status", "Employee Response");

    public Export monthly(AttendanceReportService.MonthlyReport r, String format, String fileSuffix) {
        ZoneId z = ZoneId.of(r.timezone());
        String period = String.format("%d-%02d", r.year(), r.month());
        String base = "attendance-monthly-" + period + (fileSuffix == null ? "" : "-" + fileSuffix);
        List<List<String>> summary = new ArrayList<>();
        List<List<String>> daily = new ArrayList<>();
        List<List<String>> brk = new ArrayList<>();
        List<List<String>> idle = new ArrayList<>();
        for (var m : r.employees()) {
            var e = m.employee();
            summary.add(List.of(e.employeeCode(), e.fullName(), nz(e.projectName()), nz(e.teamName()), String.valueOf(m.daysPresent()),
                    hms(m.sessionSeconds()), String.valueOf(m.breakCount()), hms(m.breakSeconds()), hms(m.averageBreakSeconds()),
                    String.valueOf(m.idleCount()), hms(m.idleSeconds()), String.valueOf(m.exceededBreakCount()),
                    String.valueOf(m.exceptionCount()), String.valueOf(m.missingLogoutCount()), String.valueOf(m.missingLoginDays())));
            for (var d : m.days()) {
                if (!d.present() && d.exceptions().isEmpty()) continue;
                daily.add(dailyCells(e, d, z));
                for (var b : d.breaks()) {
                    brk.add(List.of(e.employeeCode(), e.fullName(), d.date().toString(), b.category(), time(b.startedAt(), z),
                            b.endedAt() == null ? "in progress" : time(b.endedAt(), z), hms(b.durationSeconds()), b.status(), b.exceeded() ? "Yes" : "No"));
                }
                for (var i : d.idles()) {
                    idle.add(List.of(e.employeeCode(), e.fullName(), d.date().toString(), time(i.startedAt(), z),
                            i.endedAt() == null ? "open" : time(i.endedAt(), z), hms(i.durationSeconds()), i.status(), nz(i.response())));
                }
            }
        }
        String title = "Monthly Attendance Report - " + period + (r.scopeLabel() == null ? "" : " - " + r.scopeLabel()) + " (" + r.timezone() + ")";
        return switch (format) {
            case "csv" -> new Export(csv(MONTHLY_SUMMARY, summary), base + ".csv", "text/csv");
            case "xlsx" -> new Export(xlsx(List.of(new Sheet("Monthly summary", MONTHLY_SUMMARY, summary),
                    new Sheet("Daily details", DAILY_HEADERS, daily), new Sheet("Break timings", BREAK_HEADERS, brk),
                    new Sheet("Idle events", IDLE_HEADERS, idle))), base + ".xlsx", XLSX);
            case "pdf" -> new Export(pdf(title, summaryLine(r.totals()), List.of(
                    new PdfSection("Summary", MONTHLY_SUMMARY, summary),
                    new PdfSection("Daily details", PDF_DAILY_HEADERS, daily.stream().map(AttendanceExportService::compactDaily).toList()),
                    new PdfSection("Break timings", BREAK_HEADERS, brk),
                    new PdfSection("Idle events", IDLE_HEADERS, idle)), r.generatedAt(), z), base + ".pdf", "application/pdf");
            default -> throw new IllegalArgumentException("format must be csv, xlsx or pdf");
        };
    }

    /** Daily detail columns that fit a landscape PDF page (indexes into DAILY_HEADERS). */
    private static final int[] PDF_DAILY_COLUMNS = {0, 1, 4, 5, 6, 7, 9, 10, 11, 14, 15};
    private static final List<String> PDF_DAILY_HEADERS = java.util.Arrays.stream(PDF_DAILY_COLUMNS).mapToObj(DAILY_HEADERS::get).toList();

    private static List<String> compactDaily(List<String> row) {
        return java.util.Arrays.stream(PDF_DAILY_COLUMNS).mapToObj(row::get).toList();
    }

    // ------------------------------------------------------------------ cells

    private static List<String> dailyCells(AttendanceReportService.Employee e, AttendanceCalculator.DayResult d, ZoneId z) {
        String breaks = d.breaks().stream().map(b -> time(b.startedAt(), z) + "-" + (b.endedAt() == null ? "now" : time(b.endedAt(), z))
                + " (" + hms(b.durationSeconds()) + (b.exceeded() ? ", exceeded" : "") + ")").collect(Collectors.joining("; "));
        boolean anyOpen = d.sessions().stream().anyMatch(AttendanceCalculator.SessionLine::ongoing);
        String missing = d.missingLogout() ? "Missing logout" : (!d.present() && d.exceptions().contains("NO_LOGIN_RECORD") ? "No login record" : "");
        return List.of(e.employeeCode(), e.fullName(), nz(e.projectName()), nz(e.teamName()), d.date().toString(),
                d.firstLoginAt() == null ? "" : time(d.firstLoginAt(), z),
                anyOpen ? "active" : d.lastLogoutAt() == null ? "" : time(d.lastLogoutAt(), z),
                hms(d.sessionSeconds()), String.valueOf(d.sessions().size()), String.valueOf(d.breakCount()), breaks,
                hms(d.breakSeconds()), hms(d.averageBreakSeconds()), String.valueOf(d.idleCount()), hms(d.idleSeconds()),
                String.join(", ", d.exceptions()), missing);
    }

    private static String summaryLine(AttendanceReportService.Totals t) {
        return "Employees: " + t.employees() + " | Present (days): " + t.present() + " | Total session: " + hms(t.sessionSeconds())
                + " | Breaks: " + t.breakCount() + " (" + hms(t.breakSeconds()) + ", avg " + hms(t.averageBreakSeconds()) + ")"
                + " | Idle events: " + t.idleCount() + " | Exceptions: " + t.exceptions() + " | Missing logouts: " + t.missingLogouts();
    }

    public static String hms(long seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static String time(Instant i, ZoneId z) {
        return i == null ? "" : TIME.format(i.atZone(z));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ writers

    private record Sheet(String name, List<String> headers, List<List<String>> rows) {}

    static byte[] csv(List<String> headers, List<List<String>> rows) {
        try (var out = new ByteArrayOutputStream(); var w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             var p = new CSVPrinter(w, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build())) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM for Excel
            for (var r : rows) p.printRecord(r.stream().map(AttendanceExportService::csvSafe).toList());
            p.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Neutralises spreadsheet formula injection. */
    static String csvSafe(String v) {
        return com.suma.common.SpreadsheetSafety.cell(v);
    }

    private static byte[] xlsx(List<Sheet> sheets) {
        try (Workbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (Sheet s : sheets) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(s.name());
                Row h = sheet.createRow(0);
                for (int i = 0; i < s.headers().size(); i++) {
                    Cell c = h.createCell(i);
                    c.setCellValue(s.headers().get(i));
                    c.setCellStyle(header);
                }
                int r = 1;
                for (var row : s.rows()) {
                    Row x = sheet.createRow(r++);
                    for (int i = 0; i < row.size(); i++) x.createCell(i).setCellValue(row.get(i));
                }
                sheet.createFreezePane(0, 1);
                for (int i = 0; i < s.headers().size(); i++) sheet.setColumnWidth(i, Math.min(60, Math.max(12, s.headers().get(i).length() + 4)) * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One table in a PDF; each section after the first starts on a new page. */
    record PdfSection(String heading, List<String> headers, List<List<String>> rows) {}

    private static byte[] pdf(String title, String subtitle, List<String> headers, List<List<String>> rows, Instant generatedAt, ZoneId z) {
        return pdf(title, subtitle, List.of(new PdfSection(null, headers, rows)), generatedAt, z);
    }

    private static byte[] pdf(String title, String subtitle, List<PdfSection> sections, Instant generatedAt, ZoneId z) {
        try (var out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 20, 20, 24, 24);
            PdfWriter.getInstance(doc, out);
            doc.open();
            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 23, 42));
            com.lowagie.text.Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(15, 23, 42));
            com.lowagie.text.Font small = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(51, 65, 85));
            com.lowagie.text.Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);
            doc.add(new Paragraph(title, titleFont));
            doc.add(new Paragraph("Generated " + DATE_TIME.format(generatedAt.atZone(z)) + " by SUMA Tracking Tool", small));
            doc.add(new Paragraph(subtitle, small));
            boolean first = true;
            for (PdfSection sec : sections) {
                if (!first) doc.newPage();
                first = false;
                if (sec.heading() != null) doc.add(new Paragraph(sec.heading(), sectionFont));
                doc.add(Chunk.NEWLINE);
                PdfPTable table = new PdfPTable(sec.headers().size());
                table.setWidthPercentage(100);
                table.setHeaderRows(1);
                for (String h : sec.headers()) {
                    PdfPCell c = new PdfPCell(new Phrase(h, head));
                    c.setBackgroundColor(new Color(30, 58, 138));
                    c.setPadding(3);
                    table.addCell(c);
                }
                boolean alt = false;
                for (var r : sec.rows()) {
                    for (String v : r) {
                        PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, small));
                        c.setPadding(2.5f);
                        if (alt) c.setBackgroundColor(new Color(241, 245, 249));
                        table.addCell(c);
                    }
                    alt = !alt;
                }
                if (sec.rows().isEmpty()) {
                    PdfPCell c = new PdfPCell(new Phrase("No records for the selected filters.", small));
                    c.setColspan(sec.headers().size());
                    table.addCell(c);
                }
                doc.add(table);
            }
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException(e);
        }
    }
}
