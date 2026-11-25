package com.suma.integration;

import java.util.List;
import java.util.Map;

/** Dot-path field mapping: "fields.priority.name" over nested maps; numeric segments index into lists. */
public final class FieldMapper {
    private FieldMapper() {}

    public static Object extract(Object root, String path) {
        if (path == null || path.isBlank()) return null;
        Object cur = root;
        for (String seg : path.split("\\.")) {
            if (cur instanceof Map<?, ?> m) cur = m.get(seg);
            else if (cur instanceof List<?> l && seg.matches("\\d+")) {
                int idx = Integer.parseInt(seg);
                cur = idx < l.size() ? l.get(idx) : null;
            } else return null;
            if (cur == null) return null;
        }
        return cur;
    }

    public static String string(Object root, String path) {
        Object v = extract(root, path);
        return v == null ? null : v.toString();
    }
}
