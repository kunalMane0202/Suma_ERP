package com.suma.ticket;

import com.suma.common.ApiException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TicketLogFileParserTest {
    private final TicketLogFileParser parser = new TicketLogFileParser();

    @Test
    void readsAnyCsvHeadersAndRowsAfterTheFirstNonEmptyHeaderRow() {
        var file = csv("\nCase Ref,Service Area,Summary,Customer Note\n" +
                "LEG-100,Email,Email outage,Mail is down\nLEG-101,VPN,Access request,Need VPN access\n");

        var table = parser.parse(file);

        assertEquals(4, table.headers().size());
        assertEquals("Case Ref", table.headers().get(0));
        assertEquals("Customer Note", table.headers().get(3));
        assertEquals(2, table.rows().size());
        assertEquals("LEG-100", table.rows().get(0).values().get(0));
        assertEquals("Email outage", table.rows().get(0).values().get(2));
        assertEquals("Need VPN access", table.rows().get(1).values().get(3));
    }

    @Test
    void makesBlankAndDuplicateHeadersUsableInsteadOfRejectingTheFile() {
        var table = parser.parse(csv("Ticket,Ticket,,\nA-1,old id,extra,other\n"));

        assertEquals("Ticket", table.headers().get(0));
        assertEquals("Ticket (2)", table.headers().get(1));
        assertEquals("Column 3", table.headers().get(2));
        assertEquals("Column 4", table.headers().get(3));
    }

    @Test
    void listsWorksheetsAndParsesTheSelectedXlsxSheetWithSparseColumns() throws Exception {
        byte[] content;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var cover = workbook.createSheet("Read Me");
            cover.createRow(0).createCell(0).setCellValue("Instructions");
            var sheet = workbook.createSheet("Log");
            sheet.createRow(0);
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("Case Ref");
            header.createCell(2).setCellValue("Project");
            header.createCell(4).setCellValue("Summary");
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("LEG-200");
            row.createCell(2).setCellValue("BETA");
            row.createCell(4).setCellValue("VPN access");
            workbook.write(output);
            content = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "ticket-log.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        assertEquals(List.of("Read Me", "Log"), parser.listSheetNames(file));
        var table = parser.parse(file, "Log");

        assertEquals("Case Ref", table.headers().get(0));
        assertEquals("Column 2", table.headers().get(1));
        assertEquals("Project", table.headers().get(2));
        assertEquals("LEG-200", table.rows().get(0).values().get(0));
        assertEquals("BETA", table.rows().get(0).values().get(2));
        assertEquals("VPN access", table.rows().get(0).values().get(4));
        assertTrue(assertThrows(ApiException.class, () -> parser.parse(file, "Missing"))
                .getMessage().contains("Worksheet"));
    }

    @Test
    void rejectsUnsupportedFiles() {
        var file = new MockMultipartFile("file", "tickets.pdf", "application/pdf", new byte[]{1});
        assertTrue(assertThrows(ApiException.class, () -> parser.parse(file)).getMessage().contains(".csv and .xlsx"));
    }

    private static MockMultipartFile csv(String contents) {
        return new MockMultipartFile("file", "ticket-log.csv", "text/csv", contents.getBytes(StandardCharsets.UTF_8));
    }
}
