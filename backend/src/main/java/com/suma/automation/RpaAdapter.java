package com.suma.automation;

import java.util.Map;

/**
 * External RPA integration point. Implementations call an RPA platform (UiPath, Automation Anywhere, Power Automate...)
 * with a job name and payload. The default implementation posts JSON to an administrator-configured webhook URL.
 */
public interface RpaAdapter {
    String name();

    Result trigger(String endpoint, String job, Map<String, Object> payload);

    record Result(boolean success, int statusCode, String message) {}
}
