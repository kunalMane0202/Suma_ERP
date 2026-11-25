package com.suma.attendance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportSafetyTest {
    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(AttendanceExportService.csvSafe("=HYPERLINK(\"x\")")).startsWith("'");
        assertThat(AttendanceExportService.csvSafe("@SUM(A1)")).startsWith("'");
        assertThat(AttendanceExportService.csvSafe("-5")).isEqualTo("-5");
        assertThat(AttendanceExportService.hms(3725)).isEqualTo("01:02:05");
    }
}
