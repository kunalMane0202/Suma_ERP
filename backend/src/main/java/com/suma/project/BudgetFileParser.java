package com.suma.project;

import com.suma.common.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class BudgetFileParser {
    private static final int MAX_ITEMS = 5_000;

    public record Item(String category, String item, BigDecimal quantity, BigDecimal unitCost, BigDecimal amount) {}

    public List<Item> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a non-empty CSV or Excel file");
        String fileName = file.getOriginalFilename();
        String extension = fileName == null || !fileName.contains(".") ? "" :
                fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            return switch (extension) {
                case "csv" -> parseCsv(file);
                case "xlsx" -> parseWorkbook(file);
                default -> throw ApiException.badRequest("Only .csv and .xlsx budget files are supported");
            };
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the budget file");
        }
    }

    private List<Item> parseCsv(MultipartFile file) throws IOException {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).setAllowDuplicateHeaderNames(false).build().parse(reader)) {
            Map<String, Integer> columns = headerColumns(parser.getHeaderMap().keySet());
            List<Item> items = new ArrayList<>();
            for (var row : parser) {
                if (parser.getRecordNumber() > MAX_ITEMS + 1L)
                    throw ApiException.badRequest("Budget files may contain at most 5,000 line items");
                if (row.size() == 0 || row.stream().allMatch(String::isBlank)) continue;
                addItem(items, value(row, columns, "category"), value(row, columns, "item"),
                        value(row, columns, "quantity"), value(row, columns, "unit_cost"), row.getRecordNumber() + 1);
            }
            return finish(items);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("CSV file has invalid headers or rows");
        }
    }

    private List<Item> parseWorkbook(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw ApiException.badRequest("Excel file has no worksheets");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw ApiException.badRequest("Excel file is missing its header row");
            if (sheet.getLastRowNum() > header.getRowNum() + MAX_ITEMS)
                throw ApiException.badRequest("Budget files may contain at most 5,000 line items");
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : header) registerHeader(columns, formatter.formatCellValue(cell, evaluator), cell.getColumnIndex());
            validateHeaders(columns);
            List<Item> items = new ArrayList<>();
            for (int rowIndex = header.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String category = cellValue(row, columns.get("category"), formatter, evaluator);
                String item = cellValue(row, columns.get("item"), formatter, evaluator);
                String quantity = cellValue(row, columns.get("quantity"), formatter, evaluator);
                String unitCost = cellValue(row, columns.get("unit_cost"), formatter, evaluator);
                if (category.isBlank() && item.isBlank() && quantity.isBlank() && unitCost.isBlank()) continue;
                addItem(items, category, item, quantity, unitCost, rowIndex + 1);
            }
            return finish(items);
        }
    }

    private static Map<String, Integer> headerColumns(Collection<String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        int index = 0;
        for (String header : headers) registerHeader(columns, header, index++);
        return validateHeaders(columns);
    }

    private static void registerHeader(Map<String, Integer> columns, String header, int index) {
        String key = header.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("[ -]+", "_");
        if (!key.isEmpty() && columns.putIfAbsent(key, index) != null)
            throw ApiException.badRequest("Budget file contains duplicate columns");
    }

    private static Map<String, Integer> validateHeaders(Map<String, Integer> columns) {
        for (String required : List.of("item", "quantity", "unit_cost")) {
            if (!columns.containsKey(required)) throw ApiException.badRequest("Budget file must include item, quantity, and unit_cost columns");
        }
        columns.putIfAbsent("category", -1);
        return columns;
    }

    private static String value(org.apache.commons.csv.CSVRecord row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        return index == null || index < 0 || index >= row.size() ? "" : row.get(index).trim();
    }

    private static String cellValue(Row row, Integer index, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (index == null || index < 0) return "";
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        CellValue evaluated = evaluator.evaluate(cell);
        if (evaluated == null) return formatter.formatCellValue(cell);
        return switch (evaluated.getCellType()) {
            case STRING -> evaluated.getStringValue().trim();
            case NUMERIC -> BigDecimal.valueOf(evaluated.getNumberValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(evaluated.getBooleanValue());
            default -> "";
        };
    }

    private static void addItem(List<Item> items, String category, String name, String rawQuantity,
                                String rawUnitCost, long rowNumber) {
        if (items.size() >= MAX_ITEMS) throw ApiException.badRequest("Budget files may contain at most 5,000 line items");
        String item = name.trim();
        if (item.isEmpty()) throw ApiException.badRequest("Row " + rowNumber + ": item is required");
        if (item.length() > 250) throw ApiException.badRequest("Row " + rowNumber + ": item must be 250 characters or fewer");
        String itemCategory = category.isBlank() ? "Uncategorized" : category.trim();
        if (itemCategory.length() > 120) throw ApiException.badRequest("Row " + rowNumber + ": category must be 120 characters or fewer");
        BigDecimal quantity = number(rawQuantity, "quantity", rowNumber).setScale(4, RoundingMode.HALF_UP);
        BigDecimal unitCost = number(rawUnitCost, "unit_cost", rowNumber).setScale(2, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) throw ApiException.badRequest("Row " + rowNumber + ": quantity must be greater than zero");
        if (unitCost.signum() < 0) throw ApiException.badRequest("Row " + rowNumber + ": unit_cost cannot be negative");
        if (quantity.precision() > 18 || unitCost.precision() > 18)
            throw ApiException.badRequest("Row " + rowNumber + ": quantity or unit_cost is too large");
        BigDecimal amount = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        if (amount.precision() > 18) throw ApiException.badRequest("Row " + rowNumber + ": calculated amount is too large");
        items.add(new Item(itemCategory, item, quantity, unitCost, amount));
    }

    private static BigDecimal number(String raw, String column, long rowNumber) {
        try {
            return new BigDecimal(raw.trim().replaceAll("[,$€£₹\\s]", ""));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Row " + rowNumber + ": " + column + " must be a valid number");
        }
    }

    private static List<Item> finish(List<Item> items) {
        if (items.isEmpty()) throw ApiException.badRequest("Budget file must contain at least one line item");
        return List.copyOf(items);
    }
}
