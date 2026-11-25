package com.suma.common;

import com.suma.config.SumaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {
    private ClientIpResolver resolver(String... trusted) {
        return new ClientIpResolver(new SumaProperties(null, false, List.of(), List.of(trusted), "k", ".", "x", false, null, false, null));
    }

    private MockHttpServletRequest req(String remote, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        assertThat(resolver().resolve(req("198.51.100.20", "1.2.3.4"))).isEqualTo("198.51.100.20");
        assertThat(resolver("10.0.0.0/8").resolve(req("198.51.100.20", "1.2.3.4"))).isEqualTo("198.51.100.20");
    }

    @Test
    void usesRightmostUntrustedAddressBehindTrustedProxies() {
        ClientIpResolver r = resolver("10.0.0.0/8", "192.168.0.0/16");
        // client-supplied spoofed value on the left must be ignored
        assertThat(r.resolve(req("10.0.0.5", "6.6.6.6, 203.0.113.9, 192.168.1.2"))).isEqualTo("203.0.113.9");
        assertThat(r.resolve(req("10.0.0.5", "203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToProxyWhenChainMalformed() {
        assertThat(resolver("10.0.0.0/8").resolve(req("10.0.0.5", "not-an-ip"))).isEqualTo("10.0.0.5");
    }

    @Test
    void normalisesIpv6AndPorts() {
        assertThat(ClientIpResolver.normalize("2001:0db8:0000:0000:0000:0000:0000:0001")).isEqualTo("2001:db8::1");
        assertThat(ClientIpResolver.normalize("[2001:db8::7f]:443")).isEqualTo("2001:db8::7f");
        assertThat(ClientIpResolver.normalize("::1")).isEqualTo("::1");
        assertThat(ClientIpResolver.normalize("203.0.113.9:8080")).isEqualTo("203.0.113.9");
        assertThat(ClientIpResolver.normalize("evil.example.com")).isNull();
        assertThat(ClientIpResolver.version("2001:db8::1")).isEqualTo((short) 6);
        assertThat(ClientIpResolver.version("203.0.113.9")).isEqualTo((short) 4);
    }

    @Test
    void trustsIpv6ProxyRanges() {
        ClientIpResolver r = resolver("fd00::/8");
        assertThat(r.resolve(req("fd00::1", "2001:db8::42"))).isEqualTo("2001:db8::42");
    }

    @Test
    void classifiesUserAgents() {
        String chrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";
        assertThat(UserAgentParser.browser(chrome)).isEqualTo("Chrome");
        assertThat(UserAgentParser.device(chrome)).isEqualTo("Windows (desktop)");
        assertThat(UserAgentParser.browser(null)).isEqualTo("Unknown");
    }
}
