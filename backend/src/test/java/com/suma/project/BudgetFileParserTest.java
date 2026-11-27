package com.suma.project;

import com.suma.common.ApiException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BudgetFileParserTest {
    private final BudgetFileParser parser = new BudgetFileParser();

    @Test
    void parsesCsvAndCalculatesRoundedLineAmounts() {
        var file = csv("category,item,quantity,unit_cost\nStaffing,Engineer,2,1250.25\n,Hosting,3,10.10\n");

        var items = parser.parse(file);

        assertEquals(2, items.size());
        assertEquals("Staffing", items.get(0).category());
        assertEquals("Engineer", items.get(0).item());
        assertEquals("2500.50", items.get(0).amount().toPlainString());
        assertEquals("Uncategorized", items.get(1).category());
        assertEquals("30.30", items.get(1).amount().toPlainString());
    }

    @Test
    void rejectsMissingColumnsAndInvalidRowsWithUsefulErrors() {
        var missingColumns = assertThrows(ApiException.class, () -> parser.parse(csv("item,quantity\nEngineer,2\n")));
        assertTrue(missingColumns.getMessage().contains("unit_cost"));

        var invalidRow = assertThrows(ApiException.class,
                () -> parser.parse(csv("item,quantity,unit_cost\nEngineer,0,10\n")));
        assertTrue(invalidRow.getMessage().contains("Row 2"));
    }

    @Test
    void parsesXlsxUsingRealColumnPositionsAndFormulaValues() throws Exception {
        byte[] content;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Budget");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("category");
            header.createCell(1).setCellValue("item");
            header.createCell(2).setCellValue("quantity");
            header.createCell(4).setCellValue("unit_cost");
            var line = sheet.createRow(1);
            line.createCell(0).setCellValue("Equipment");
            line.createCell(1).setCellValue("Laptop");
            line.createCell(2).setCellValue(2);
            line.createCell(4).setCellFormula("1250.25");
            workbook.write(output);
            content = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "budget.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        var items = parser.parse(file);

        assertEquals(1, items.size());
        assertEquals("2500.50", items.get(0).amount().toPlainString());
    }

    private static MockMultipartFile csv(String contents) {
        return new MockMultipartFile("file", "budget.csv", "text/csv", contents.getBytes(StandardCharsets.UTF_8));
    }
}
