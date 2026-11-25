package com.suma.common;

/** Request origin information captured on the backend. Never contains credentials. */
public record ClientInfo(String ip, short ipVersion, String userAgent, String device, String browser) {
    public static ClientInfo system() {
        return new ClientInfo("0.0.0.0", (short) 4, "system", "Server", "System");
    }
}
