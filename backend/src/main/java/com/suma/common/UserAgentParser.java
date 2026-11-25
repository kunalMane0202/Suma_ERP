package com.suma.common;

/** Lightweight, dependency-free user agent classification (best effort, informational only). */
public final class UserAgentParser {
    private UserAgentParser() {}

    public static String browser(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";
        String u = ua.toLowerCase();
        if (u.contains("edg/")) return "Edge";
        if (u.contains("opr/") || u.contains("opera")) return "Opera";
        if (u.contains("firefox/")) return "Firefox";
        if (u.contains("chrome/") || u.contains("crios/")) return u.contains("headlesschrome") ? "Headless Chrome" : "Chrome";
        if (u.contains("safari/")) return "Safari";
        if (u.contains("curl/")) return "curl";
        if (u.contains("postman")) return "Postman";
        if (u.contains("java/") || u.contains("apache-httpclient")) return "Java client";
        return "Other";
    }

    public static String device(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";
        String u = ua.toLowerCase();
        String os;
        if (u.contains("windows")) os = "Windows";
        else if (u.contains("android")) os = "Android";
        else if (u.contains("iphone") || u.contains("ipad")) os = "iOS";
        else if (u.contains("mac os")) os = "macOS";
        else if (u.contains("linux")) os = "Linux";
        else os = "Other";
        boolean mobile = u.contains("mobile") || u.contains("android") || u.contains("iphone");
        return os + (mobile ? " (mobile)" : " (desktop)");
    }
}
