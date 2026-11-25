package com.suma.automation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineConditionTest {
    @Test
    void operators() {
        assertThat(RuleEngine.evaluate("HIGH", "eq", "HIGH")).isTrue();
        assertThat(RuleEngine.evaluate("HIGH", "neq", "LOW")).isTrue();
        assertThat(RuleEngine.evaluate("HIGH", "in", List.of("CRITICAL", "HIGH"))).isTrue();
        assertThat(RuleEngine.evaluate("VPN is down", "contains", "vpn")).isTrue();
        assertThat(RuleEngine.evaluate(null, "empty", null)).isTrue();
        assertThat(RuleEngine.evaluate("x", "notEmpty", null)).isTrue();
        assertThat(RuleEngine.evaluate("3", "gte", "2")).isTrue();
        assertThatThrownBy(() -> RuleEngine.evaluate("x", "regex", "y")).isInstanceOf(IllegalArgumentException.class);
    }
}
