package com.suma.ticket;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.project.Project;
import com.suma.project.ProjectRepository;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class TicketLogImportService {
    private static final String EXTERNAL_SYSTEM = "LOG_SHEET";
    private static final Set<String> TICKET_NUMBER_HEADERS = Set.of("ticket", "ticketno", "ticketnumber", "ticketid", "caseno",
            "casenumber", "incidentno", "incidentnumber", "incidentid", "requestno", "requestnumber", "requestid",
            "issueid", "externalid", "reference", "ref", "number", "id", "key");
    private static final Set<String> PROJECT_HEADERS = Set.of("project", "projectcode", "projectname", "projectid",
            "projectkey", "projectidentifier");
    private static final Set<String> TITLE_HEADERS = Set.of("title", "subject", "summary", "topic", "issue", "tickettitle",
            "shortdescription", "task", "name");
    private static final Set<String> DESCRIPTION_HEADERS = Set.of("description", "details", "detail", "longdescription",
            "comments", "notes", "requestdetails", "issuedescription");
    private static final Set<String> STATUS_HEADERS = Set.of("status", "state", "ticketstatus", "issuestatus");
    private static final Set<String> PRIORITY_HEADERS = Set.of("priority", "severity", "impact", "ticketpriority");
    private static final Set<String> REPORTER_NAME_HEADERS = Set.of("reporter", "reportername", "requester", "requestor",
            "requestername", "requestorname", "reportedby", "raisedby", "contactname");
    private static final Set<String> REPORTER_EMAIL_HEADERS = Set.of("reporteremail", "requesteremail", "requestoremail",
            "reportedbyemail", "contactemail", "email");
    private final TicketLogFileParser parser;
    private final TicketService tickets;
    private final TicketRepository ticketRepository;
    private final ProjectRepository projects;
    private final AccessScopeService scope;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public TicketLogImportService(TicketLogFileParser parser, TicketService tickets, TicketRepository ticketRepository,
                                  ProjectRepository projects, AccessScopeService scope, AuditService audit,
                                  JdbcTemplate jdbc) {
        this.parser = parser;
        this.tickets = tickets;
        this.ticketRepository = ticketRepository;
        this.projects = projects;
        this.scope = scope;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    public List<String> listSheetNames(MultipartFile file) {
        return parser.listSheetNames(file);
    }

    @Transactional
    public TicketLogImportDtos.ImportResult importFile(MultipartFile file, UUID defaultProjectId, String sheetName) {
        TicketLogFileParser.Table table = parser.parse(file, sheetName);
        Mapping mapping = Mapping.from(table.headers());
        AuthUser me = CurrentUser.get();
        Project defaultProject = defaultProjectId == null ? null : projects.findById(defaultProjectId)
                .orElseThrow(() -> ApiException.badRequest("Default project was not found"));
        if (defaultProject != null) scope.requireProject(me, defaultProject.getId());

        List<TicketLogImportDtos.RowResult> results = new ArrayList<>(table.rows().size());
        Map<String, Ticket> importedInFile = new HashMap<>();
        int created = 0;
        int skipped = 0;
        for (TicketLogFileParser.DataRow row : table.rows()) {
            String sourceTicketNumber = value(row, mapping.ticketNumber()).trim();
            if (mapping.hasExplicitTicketNumber() && sourceTicketNumber.isBlank()) {
                skipped++;
                continue;
            }
            Project project = resolveProject(row, mapping, defaultProject);
            scope.requireProject(me, project.getId());
            String externalNumber = normalizeExternalNumber(sourceTicketNumber);
            if (externalNumber.isBlank()) externalNumber = generatedExternalNumber(file.getOriginalFilename(), row.rowNumber());
            if (externalNumber.length() > 120)
                throw ApiException.badRequest("Row " + row.rowNumber() + ": ticket number must be 120 characters or fewer");
            String externalKey = externalNumber.toUpperCase(Locale.ROOT);
            lockExternalTicket(externalKey);

            String originalTitle = value(row, mapping.title()).trim();
            String title = originalTitle.isBlank() ? "Imported ticket " + externalNumber : originalTitle;
            boolean truncatedTitle = title.length() > 300;
            if (truncatedTitle) title = title.substring(0, 297) + "...";
            String description = emptyToNull(value(row, mapping.description()));
            String sourceStatus = value(row, mapping.status());
            String status = mapStatus(sourceStatus);
            String priority = mapPriority(value(row, mapping.priority()));
            String reporterName = emptyToNull(value(row, mapping.reporterName()));
            String reporterEmailValue = emptyToNull(value(row, mapping.reporterEmail()));
            boolean validReporterEmail = reporterEmailValue == null || reporterEmailValue.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
            String reporterEmail = validReporterEmail ? reporterEmailValue : null;

            Ticket ticket = importedInFile.get(externalKey);
            if (ticket == null)
                ticket = ticketRepository.findByExternalSystemAndExternalId(EXTERNAL_SYSTEM, externalNumber).orElse(null);
            if (ticket != null && !ticket.getProjectId().equals(project.getId()))
                throw ApiException.conflict("Row " + row.rowNumber() + ": ticket number is already imported for a different project");
            if (ticket == null) ticket = ticketRepository.findByTicketNumberAndProjectId(externalNumber, project.getId()).orElse(null);

            boolean isCreated = ticket == null;
            if (isCreated) {
                TicketDtos.CreateRequest request = new TicketDtos.CreateRequest(project.getId(), title, description,
                        priority, null, null, null, null, reporterName, reporterEmail, Map.of());
                ticket = tickets.createInternal(request, EXTERNAL_SYSTEM, EXTERNAL_SYSTEM, externalNumber, me.id());
            } else {
                tickets.update(me, ticket.getId(), new TicketDtos.UpdateRequest(title, description, priority,
                        null, null, null, null));
                ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
                tickets.updateImportedReporter(ticket, reporterName, reporterEmail, me.id());
            }
            importedInFile.put(externalKey, ticket);

            if (status != null) tickets.applyImportedStatus(ticket, status, me.id());
            tickets.updateImportedFields(ticket, extraFields(table.headers(), row, mapping, externalNumber,
                    validReporterEmail, truncatedTitle ? originalTitle : null), me.id());
            audit.log("TICKET_LOG_IMPORTED", "Ticket", ticket.getId(), "External ticket " + externalNumber);
            if (isCreated) created++;
            results.add(new TicketLogImportDtos.RowResult(ticket.getId(), ticket.getTicketNumber(), externalNumber,
                    ticket.getTitle(), ticket.getStatus(), ticket.getPriority(), project.getCode(), isCreated));
        }
        return new TicketLogImportDtos.ImportResult(created, results.size() - created, skipped,
                table.rows().size(), List.copyOf(results));
    }

    private void lockExternalTicket(String externalKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                (ResultSetExtractor<Void>) resultSet -> null, EXTERNAL_SYSTEM, externalKey);
    }

    private Project resolveProject(TicketLogFileParser.DataRow row, Mapping mapping, Project fallback) {
        String projectValue = value(row, mapping.project()).trim();
        if (projectValue.isBlank()) {
            if (fallback == null) throw ApiException.badRequest("Row " + row.rowNumber() + ": choose a default project or include a project column");
            return fallback;
        }
        Optional<Project> byCode = projects.findByCodeIgnoreCase(projectValue);
        if (byCode.isPresent()) return byCode.get();
        List<Project> byName = projects.findAllByNameIgnoreCase(projectValue);
        if (byName.size() == 1) return byName.get(0);
        try {
            Optional<Project> byId = projects.findById(UUID.fromString(projectValue));
            if (byId.isPresent()) return byId.get();
        } catch (IllegalArgumentException ignored) {
            // The value is not a project UUID; report the user-facing project lookup error below.
        }
        if (fallback != null) return fallback;
        if (byName.size() > 1) throw ApiException.badRequest("Row " + row.rowNumber() + ": project name is ambiguous; choose a default project or use a project code");
        throw ApiException.badRequest("Row " + row.rowNumber() + ": project " + projectValue + " was not found");
    }

    static Map<String, Object> extraFields(List<String> headers, TicketLogFileParser.DataRow row, Mapping mapping,
                                           String externalNumber, boolean validReporterEmail,
                                           String originalTitle) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < row.values().size() ? row.values().get(i) : "";
            if (value.isBlank()) continue;
            boolean mapped = mapping.isMapped(i);
            boolean preserveSourceValue = Objects.equals(mapping.status(), i)
                    || Objects.equals(mapping.priority(), i)
                    || Objects.equals(mapping.title(), i)
                    || (Objects.equals(mapping.ticketNumber(), i)
                    && !normalizeExternalNumber(value).equals(externalNumber));
            if (mapped && !preserveSourceValue
                    && !(Objects.equals(mapping.reporterEmail(), i) && !validReporterEmail)) continue;
            fields.put("Imported: " + headers.get(i), value);
        }
        if (originalTitle != null) fields.put("Imported: Original title", originalTitle);
        return fields;
    }

    private static String value(TicketLogFileParser.DataRow row, Integer index) {
        return index == null || index < 0 || index >= row.values().size() ? "" : row.values().get(index);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String normalizeExternalNumber(String value) {
        String normalized = value.trim();
        return normalized.matches("\\d+\\.0+") ? normalized.substring(0, normalized.indexOf('.')) : normalized;
    }

    static String mapStatus(String value) {
        if (value.isBlank()) return null;
        String normalized = normalize(value);
        return switch (normalized) {
            case "new", "open", "created", "submitted", "todo" -> "NEW";
            case "assigned" -> "ASSIGNED";
            case "inprogress", "working", "active", "underinvestigation" -> "IN_PROGRESS";
            case "pending", "waiting", "waitingforcustomer", "onhold", "blocked" -> "PENDING";
            case "resolved", "solve", "solved", "complete", "completed", "done" -> "RESOLVED";
            case "closed", "merged" -> "CLOSED";
            case "reopened" -> "REOPENED";
            default -> null;
        };
    }

    private static String mapPriority(String value) {
        if (value.isBlank()) return null;
        String normalized = normalize(value);
        return switch (normalized) {
            case "critical", "urgent", "emergency", "p1", "sev1", "severity1" -> "CRITICAL";
            case "high", "p2", "sev2", "severity2" -> "HIGH";
            case "medium", "normal", "moderate", "p3", "sev3", "severity3" -> "MEDIUM";
            case "low", "minor", "p4", "sev4", "severity4" -> "LOW";
            default -> null;
        };
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String generatedExternalNumber(String originalName, long rowNumber) {
        String fileName = originalName == null ? "upload" : originalName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        String generated = "LOG-" + fileName + "-" + rowNumber;
        return generated.length() > 120 ? generated.substring(generated.length() - 120) : generated;
    }

    static record Mapping(Integer ticketNumber, boolean hasExplicitTicketNumber, Integer project, Integer title, Integer description,
                          Integer status, Integer priority, Integer reporterName, Integer reporterEmail,
                          Set<Integer> mapped) {
        static Mapping from(List<String> headers) {
            Integer project = find(headers, PROJECT_HEADERS);
            Integer title = find(headers, TITLE_HEADERS);
            Integer description = find(headers, DESCRIPTION_HEADERS);
            Integer status = find(headers, STATUS_HEADERS);
            Integer priority = find(headers, PRIORITY_HEADERS);
            Integer reporterName = find(headers, REPORTER_NAME_HEADERS);
            Integer reporterEmail = find(headers, REPORTER_EMAIL_HEADERS);
            Set<Integer> mapped = new HashSet<>();
            Collections.addAll(mapped, project, title, description, status, priority, reporterName, reporterEmail);
            mapped.remove(null);
            Integer ticketNumber = find(headers, TICKET_NUMBER_HEADERS);
            boolean hasExplicitTicketNumber = ticketNumber != null;
            if (ticketNumber == null) ticketNumber = firstUnmapped(headers.size(), mapped);
            if (ticketNumber != null) mapped.add(ticketNumber);
            return new Mapping(ticketNumber, hasExplicitTicketNumber, project, title, description, status, priority, reporterName,
                    reporterEmail, Set.copyOf(mapped));
        }

        boolean isMapped(int index) { return mapped.contains(index); }

        private static Integer find(List<String> headers, Set<String> aliases) {
            for (int i = 0; i < headers.size(); i++) {
                if (aliases.contains(normalize(headers.get(i)))) return i;
            }
            return null;
        }

        private static Integer firstUnmapped(int size, Set<Integer> mapped) {
            for (int i = 0; i < size; i++) if (!mapped.contains(i)) return i;
            return null;
        }
    }
}
