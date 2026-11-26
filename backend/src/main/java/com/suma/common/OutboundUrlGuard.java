package com.suma.common;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Checks URLs the server is asked to call (integration endpoints, RPA webhooks) before any request is sent.
 * Always refuses link-local addresses (including the 169.254.169.254 cloud metadata service), unspecified
 * (0.0.0.0, ::) and multicast addresses, and refuses loopback unless SUMA_OUTBOUND_ALLOW_LOOPBACK=true
 * (local development only). Private network ranges stay allowed because integrations often target an
 * internal Jira or ticketing system. The host is resolved here and every address it resolves to is checked.
 */
public final class OutboundUrlGuard {
    private OutboundUrlGuard() {}

    public static URI check(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only http and https URLs are allowed");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("URL must have a host and no embedded credentials");
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Host " + uri.getHost() + " could not be resolved");
        }
        boolean allowLoopback = "true".equalsIgnoreCase(System.getenv("SUMA_OUTBOUND_ALLOW_LOOPBACK"))
                || Boolean.getBoolean("suma.outbound.allowLoopback"); // system property: tests with a local stub server
        for (InetAddress a : addresses) {
            if (a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress() || (a.isLoopbackAddress() && !allowLoopback)) {
                throw new IllegalArgumentException("Calls to " + uri.getHost() + " are not allowed (local, link-local or metadata address)");
            }
        }
        return uri;
    }
}
