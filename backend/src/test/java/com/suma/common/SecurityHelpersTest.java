package com.suma.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityHelpersTest {

    @Test
    void spreadsheetCellsThatCouldRunAsFormulasAreEscaped() {
        assertThat(SpreadsheetSafety.cell("=HYPERLINK(\"http://x\")")).startsWith("'=");
        assertThat(SpreadsheetSafety.cell("+1+cmd|' /C calc'!A0")).startsWith("'+");
        assertThat(SpreadsheetSafety.cell("-2+3")).isEqualTo("'-2+3");
        assertThat(SpreadsheetSafety.cell("@SUM(A1)")).startsWith("'@");
        assertThat(SpreadsheetSafety.cell("\t=1")).startsWith("'\t");
    }

    @Test
    void ordinaryValuesAndNegativeNumbersAreUnchanged() {
        assertThat(SpreadsheetSafety.cell("Printer jam")).isEqualTo("Printer jam");
        assertThat(SpreadsheetSafety.cell("-12")).isEqualTo("-12");
        assertThat(SpreadsheetSafety.cell("-3.5")).isEqualTo("-3.5");
        assertThat(SpreadsheetSafety.cell("")).isEmpty();
        assertThat(SpreadsheetSafety.cell(null)).isNull();
    }

    @Test
    void outboundCallsToMetadataLoopbackAndOddSchemesAreRefused() {
        assertThatThrownBy(() -> OutboundUrlGuard.check("http://169.254.169.254/latest/meta-data/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.check("http://127.0.0.1:8080/actuator")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.check("http://[::1]/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.check("http://0.0.0.0/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.check("file:///etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.check("http://user:pw@10.0.0.5/")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privateNetworkAddressesStayAllowedForInternalSystems() {
        assertThat(OutboundUrlGuard.check("https://10.20.30.40/rest/api/2/search").getHost()).isEqualTo("10.20.30.40");
        assertThat(OutboundUrlGuard.check("http://192.168.1.10:8080/hook").getHost()).isEqualTo("192.168.1.10");
    }
}
