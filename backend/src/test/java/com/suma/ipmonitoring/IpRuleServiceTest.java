package com.suma.ipmonitoring;

import com.suma.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpRuleServiceTest {
    @Test
    void validatesAndNormalisesCidr() {
        assertThat(IpRuleService.validateCidr("203.0.113.0/24")).isEqualTo("203.0.113.0/24");
        assertThat(IpRuleService.validateCidr("2001:0db8::/32")).isEqualTo("2001:db8::/32");
        assertThat(IpRuleService.validateCidr(" 198.51.100.7 ")).isEqualTo("198.51.100.7");
        assertThatThrownBy(() -> IpRuleService.validateCidr("203.0.113.0/40")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> IpRuleService.validateCidr("example.com")).isInstanceOf(ApiException.class);
    }
}
