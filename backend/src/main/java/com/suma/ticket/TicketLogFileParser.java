package com.suma.ticket;

import com.suma.common.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class TicketLogFileParser {
    private static final int MAX_ROWS = 5_000;
    private static final int MAX_COLUMNS = 200;
    private static final int MAX_CELL_LENGTH = 20_000;

    public record DataRow(long rowNumber, List<String> values) {}
    public record Table(List<String> headers, List<DataRow> rows) {}

    public Table parse(MultipartFile file) {
        return parse(file, null);
    }

    public Table parse(MultipartFile file, String sheetName) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a non-empty CSV or Excel file");
        String fileName = file.getOriginalFilename();
        String extension = fileName == null || !fileName.contains(".") ? "" :
                fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            return switch (extension) {
                case "csv" -> parseCsv(file);
                case "xlsx" -> parseWorkbook(file, sheetName);
                default -> throw ApiException.badRequest("Only .csv and .xlsx log sheets are supported");
            };
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the log sheet");
        }
    }

    public List<String> listSheetNames(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a non-empty Excel file");
        String fileName = file.getOriginalFilename();
        String extension = fileName == null || !fileName.contains(".") ? "" :
                fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!"xlsx".equals(extension)) throw ApiException.badRequest("Choose an .xlsx workbook to list worksheets");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw ApiException.badRequest("Excel file has no worksheets");
            List<String> names = new ArrayList<>(workbook.getNumberOfSheets());
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) names.add(workbook.getSheetName(i));
            return List.copyOf(names);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the Excel workbook");
        }
    }

    private Table parseCsv(MultipartFile file) throws IOException {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).build().parse(reader)) {
            Iterator<org.apache.commons.csv.CSVRecord> records = parser.iterator();
            List<String> headers = null;
            List<DataRow> rows = new ArrayList<>();
            long rowNumber = 0;
            while (records.hasNext()) {
                var record = records.next();
                rowNumber = record.getRecordNumber();
                List<String> values = new ArrayList<>();
                record.forEach(value -> values.add(value.trim()));
                if (headers == null) {
                    if (values.stream().allMatch(String::isBlank)) continue;
                    headers = uniqueHeaders(values);
                    continue;
                }
                if (values.stream().allMatch(String::isBlank)) continue;
                if (rows.size() >= MAX_ROWS) throw ApiException.badRequest("Log sheets may contain at most 5,000 ticket rows");
                if (values.size() > MAX_COLUMNS) throw ApiException.badRequest("Log sheets may contain at most 200 columns");
                extendHeaders(headers, values.size());
                addRow(rows, rowNumber, values, headers.size());
            }
            return finish(headers, rows);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("CSV file has invalid rows");
        }
    }

    private Table parseWorkbook(MultipartFile file, String sheetName) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw ApiException.badRequest("Excel file has no worksheets");
            Sheet sheet = sheetName == null || sheetName.isBlank()
                    ? workbook.getSheetAt(0) : workbook.getSheet(sheetName);
            if (sheet == null) throw ApiException.badRequest("Worksheet \"" + sheetName + "\" was not found");
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            org.apache.poi.ss.usermodel.Row headerRow = null;
            int headerRowIndex = -1;
            for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
                var candidate = sheet.getRow(i);
                if (candidate == null) continue;
                List<String> candidateValues = excelValues(candidate, formatter, evaluator);
                if (candidateValues.stream().anyMatch(value -> !value.isBlank())) {
                    headerRow = candidate;
                    headerRowIndex = i;
                    break;
                }
            }
            if (headerRow == null) throw ApiException.badRequest("Excel file has no header row");
            int columnCount = Math.max(headerRow.getLastCellNum(), 0);
            if (columnCount == 0 || columnCount > MAX_COLUMNS)
                throw ApiException.badRequest("Log sheets must have between 1 and 200 columns");
            List<String> rawHeaders = new ArrayList<>(Collections.nCopies(columnCount, ""));
            for (Cell cell : headerRow) rawHeaders.set(cell.getColumnIndex(), formatter.formatCellValue(cell, evaluator).trim());
            List<String> headers = uniqueHeaders(rawHeaders);
            List<DataRow> rows = new ArrayList<>();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;
                List<String> values = excelValues(row, formatter, evaluator);
                if (values.stream().allMatch(String::isBlank)) continue;
                if (rows.size() >= MAX_ROWS) throw ApiException.badRequest("Log sheets may contain at most 5,000 ticket rows");
                if (values.size() > MAX_COLUMNS) throw ApiException.badRequest("Log sheets may contain at most 200 columns");
                extendHeaders(headers, values.size());
                addRow(rows, i + 1L, values, headers.size());
            }
            return finish(headers, rows);
        }
    }

    private static List<String> excelValues(org.apache.poi.ss.usermodel.Row row, DataFormatter formatter,
                                            FormulaEvaluator evaluator) {
        int count = Math.max(row.getLastCellNum(), 0);
        List<String> values = new ArrayList<>(Collections.nCopies(count, ""));
        for (Cell cell : row) values.set(cell.getColumnIndex(), formatter.formatCellValue(cell, evaluator).trim());
        return values;
    }

    private static List<String> uniqueHeaders(List<String> raw) {
        if (raw.size() > MAX_COLUMNS) throw ApiException.badRequest("Log sheets may contain at most 200 columns");
        List<String> headers = new ArrayList<>(raw.size());
        Set<String> used = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String header = raw.get(i).replace("\uFEFF", "").trim();
            if (header.isEmpty()) header = "Column " + (i + 1);
            String unique = header;
            int suffix = 2;
            while (!used.add(unique.toLowerCase(Locale.ROOT))) unique = header + " (" + suffix++ + ")";
            headers.add(unique);
        }
        return headers;
    }

    private static void extendHeaders(List<String> headers, int targetSize) {
        if (targetSize > MAX_COLUMNS) throw ApiException.badRequest("Log sheets may contain at most 200 columns");
        while (headers.size() < targetSize) headers.add("Column " + (headers.size() + 1));
    }

    private static void addRow(List<DataRow> rows, long rowNumber, List<String> values, int columnCount) {
        List<String> padded = new ArrayList<>(Collections.nCopies(columnCount, ""));
        for (int i = 0; i < Math.min(values.size(), columnCount); i++) {
            String value = values.get(i);
            if (value.length() > MAX_CELL_LENGTH)
                throw ApiException.badRequest("Row " + rowNumber + ": a cell exceeds the 20,000 character limit");
            padded.set(i, value);
        }
        rows.add(new DataRow(rowNumber, List.copyOf(padded)));
    }

    private static Table finish(List<String> headers, List<DataRow> rows) {
        if (headers == null || headers.isEmpty()) throw ApiException.badRequest("Log sheet must contain a header row");
        if (rows.isEmpty()) throw ApiException.badRequest("Log sheet must contain at least one ticket row");
        return new Table(List.copyOf(headers), List.copyOf(rows));
    }
}
