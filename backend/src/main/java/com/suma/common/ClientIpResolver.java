package com.suma.common;

import com.suma.config.SumaProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves the real client IP. X-Forwarded-For is honoured ONLY when the direct peer is a configured trusted proxy;
 * the chain is walked right-to-left skipping trusted proxies, so a client cannot spoof its address by sending the header.
 */
@Component
public class ClientIpResolver {
    private static final Pattern IP_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    private final List<IpAddressMatcher> trusted = new ArrayList<>();

    public ClientIpResolver(SumaProperties props) {
        for (String cidr : props.trustedProxies()) {
            trusted.add(new IpAddressMatcher(cidr));
        }
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (remote == null) remote = "0.0.0.0";
        if (!isTrusted(remote)) return remote;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) return remote;
        String[] parts = xff.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalize(parts[i]);
            if (candidate == null) return remote; // malformed chain: fall back to the proxy address
            if (!isTrusted(candidate)) return candidate;
        }
        return remote;
    }

    public ClientInfo clientInfo(HttpServletRequest request) {
        String ip = resolve(request);
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
        return new ClientInfo(ip, version(ip), ua, UserAgentParser.device(ua), UserAgentParser.browser(ua));
    }

    boolean isTrusted(String ip) {
        for (IpAddressMatcher m : trusted) {
            try {
                if (m.matches(ip)) return true;
            } catch (IllegalArgumentException ignored) {
                // address family mismatch
            }
        }
        return false;
    }

    public static short version(String ip) {
        return (short) (ip != null && ip.contains(":") ? 6 : 4);
    }

    /** Normalises an IP literal (strips brackets/ports/zone ids, canonical IPv6). Returns null if not an IP literal. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end < 0) return null;
            s = s.substring(1, end);
        } else if (s.indexOf(':') > 0 && s.indexOf(':') == s.lastIndexOf(':') && s.contains(".")) {
            s = s.substring(0, s.indexOf(':')); // IPv4:port
        }
        int zone = s.indexOf('%');
        if (zone > 0) s = s.substring(0, zone);
        if (!IP_CHARS.matcher(s).matches()) return null;
        try {
            // Literal only (validated characters), so no DNS lookup happens.
            InetAddress addr = InetAddress.getByName(s);
            String host = addr.getHostAddress();
            if (host.contains(":")) {
                // compress IPv4-mapped IPv6 to plain IPv4
                if (addr instanceof java.net.Inet4Address) return host;
                return compressIpv6(host);
            }
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    /** RFC 5952 style compression of a full IPv6 address string. */
    static String compressIpv6(String full) {
        String[] groups = full.toLowerCase().split(":");
        for (int i = 0; i < groups.length; i++) groups[i] = groups[i].replaceFirst("^0+(?!$)", "");
        int bestStart = -1, bestLen = 0;
        for (int i = 0; i < groups.length; ) {
            if (groups[i].equals("0")) {
                int j = i;
                while (j < groups.length && groups[j].equals("0")) j++;
                if (j - i > bestLen) { bestStart = i; bestLen = j - i; }
                i = j;
            } else i++;
        }
        if (bestLen < 2) return String.join(":", groups);
        String left = String.join(":", java.util.Arrays.copyOfRange(groups, 0, bestStart));
        String right = String.join(":", java.util.Arrays.copyOfRange(groups, bestStart + bestLen, groups.length));
        return left + "::" + right;
    }
}
