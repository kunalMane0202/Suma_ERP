package com.suma.common;

/**
 * Neutralises spreadsheet formula injection in CSV exports (OWASP "CSV injection").
 * A cell that starts with =, +, -, @, tab or carriage return is prefixed with an apostrophe so Excel,
 * LibreOffice and Google Sheets show it as text instead of evaluating it. Plain negative numbers are left alone.
 */
public final class SpreadsheetSafety {
    private SpreadsheetSafety() {}

    public static String cell(String v) {
        if (v == null || v.isEmpty()) return v;
        char c = v.charAt(0);
        boolean risky = c == '=' || c == '+' || c == '@' || c == '\t' || c == '\r'
                || (c == '-' && !v.matches("-\\d+(\\.\\d+)?"));
        return risky ? "'" + v : v;
    }
}
