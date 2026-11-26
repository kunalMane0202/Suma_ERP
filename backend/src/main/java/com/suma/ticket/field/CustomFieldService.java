package com.suma.ticket.field;

import com.suma.common.ApiException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Validates ticket custom-field values against the active definitions for the ticket's project. */
@Service
public class CustomFieldService {
    private static final int MAX_TEXT = 1000;
    private final CustomFieldRepository repo;

    public CustomFieldService(CustomFieldRepository repo) {
        this.repo = repo;
    }

    public List<CustomField> applicable(UUID projectId) {
        return repo.applicable(projectId);
    }

    /**
     * Returns the cleaned values (typed, blanks removed). Unknown keys are rejected. Required fields are enforced
     * when {@code enforceRequired} is true (people creating or editing tickets); external ingestion is not blocked.
     */
    public Map<String, Object> clean(UUID projectId, Map<String, Object> values, boolean enforceRequired) {
        Map<String, Object> in = values == null ? Map.of() : values;
        Map<String, CustomField> defs = new LinkedHashMap<>();
        for (CustomField f : repo.applicable(projectId)) defs.put(f.getFieldKey(), f);
        for (String k : in.keySet()) if (!defs.containsKey(k)) throw ApiException.badRequest("Unknown custom field: " + k);
        Map<String, Object> out = new LinkedHashMap<>();
        for (CustomField f : defs.values()) {
            Object raw = in.get(f.getFieldKey());
            boolean blank = raw == null || (raw instanceof String s && s.isBlank());
            if (blank) {
                if (f.isRequired() && enforceRequired) throw ApiException.badRequest(f.getLabel() + " is required");
                continue;
            }
            out.put(f.getFieldKey(), coerce(f, raw));
        }
        return out;
    }

    static Object coerce(CustomField f, Object raw) {
        String label = f.getLabel();
        return switch (f.getFieldType()) {
            case "NUMBER" -> {
                if (raw instanceof Number n) yield n;
                try { yield Double.parseDouble(raw.toString().trim()); } catch (NumberFormatException e) { throw ApiException.badRequest(label + " must be a number"); }
            }
            case "BOOLEAN" -> {
                if (raw instanceof Boolean b) yield b;
                String s = raw.toString().trim().toLowerCase();
                if (s.equals("true") || s.equals("false")) yield Boolean.parseBoolean(s);
                throw ApiException.badRequest(label + " must be true or false");
            }
            case "DATE" -> {
                try { yield LocalDate.parse(raw.toString().trim()).toString(); } catch (DateTimeParseException e) { throw ApiException.badRequest(label + " must be a date (YYYY-MM-DD)"); }
            }
            case "SELECT" -> {
                String v = raw.toString().trim();
                List<String> choices = f.getOptions() == null ? List.of() : Arrays.stream(f.getOptions().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (!choices.contains(v)) throw ApiException.badRequest(label + " must be one of: " + String.join(", ", choices));
                yield v;
            }
            default -> {
                String v = raw.toString().trim();
                if (v.length() > MAX_TEXT) throw ApiException.badRequest(label + " is longer than " + MAX_TEXT + " characters");
                yield v;
            }
        };
    }
}
