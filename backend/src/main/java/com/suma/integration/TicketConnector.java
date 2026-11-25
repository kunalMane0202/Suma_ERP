package com.suma.integration;

import java.util.List;
import java.util.Map;

/** Extensible connector contract. Each connector fetches raw records; field mapping converts them into tickets. */
public interface TicketConnector {
    String type();

    /** Raw external records (already parsed into nested maps). */
    List<Map<String, Object>> fetch(Integration integration, Map<String, String> credentials) throws Exception;

    /** Lightweight connectivity check; returns a human readable message. */
    String test(Integration integration, Map<String, String> credentials) throws Exception;

    /** Default mapping used when the administrator has not configured one. */
    Map<String, Object> defaultMapping();
}
