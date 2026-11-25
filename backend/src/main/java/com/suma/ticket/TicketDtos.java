package com.suma.ticket;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TicketDtos {
    private TicketDtos() {}

    public record CreateRequest(@NotNull UUID projectId, @NotBlank @Size(max = 300) String title, @Size(max = 20000) String description,
                                @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW") String priority, UUID categoryId, UUID subcategoryId,
                                UUID assigneeId, UUID teamId, @Size(max = 150) String reporterName, @Email @Size(max = 200) String reporterEmail,
                                Map<String, Object> customFields) {}

    public record UpdateRequest(@Size(max = 300) String title, @Size(max = 20000) String description,
                                @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW") String priority, UUID categoryId, UUID subcategoryId,
                                UUID teamId, Map<String, Object> customFields) {}

    public record StatusRequest(@NotBlank @Pattern(regexp = "NEW|ASSIGNED|IN_PROGRESS|PENDING|RESOLVED|CLOSED|REOPENED") String status,
                                @Size(max = 5000) String note) {}

    public record AssignRequest(UUID assigneeId, UUID teamId, @Size(max = 1000) String note) {}

    public record CommentRequest(@NotBlank @Size(max = 20000) String body, boolean internal) {}

    public record EscalateRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record SlaInfo(Instant responseDueAt, Instant resolutionDueAt, Instant firstResponseAt, boolean responseBreached,
                          boolean resolutionBreached, boolean paused, String state, Long resolutionRemainingSeconds, String policyName) {}

    public record TicketSummary(UUID id, String ticketNumber, String title, String status, String priority, UUID projectId,
                                String projectName, UUID clientId, String clientName, UUID assigneeId, String assigneeName,
                                UUID teamId, String teamName, String categoryName, String source, int escalationLevel,
                                Instant createdAt, Instant updatedAt, long ageHours, SlaInfo sla) {}

    public record CommentDto(UUID id, UUID authorId, String authorName, String body, boolean internal, Instant createdAt) {}

    public record AttachmentDto(UUID id, String fileName, String contentType, long sizeBytes, String uploadedByName, Instant createdAt) {}

    public record HistoryDto(UUID id, String action, String field, String oldValue, String newValue, String note, String actorName, Instant createdAt) {}

    public record TicketDetail(TicketSummary summary, String description, UUID categoryId, UUID subcategoryId, String reporterName,
                               String reporterEmail, String externalSystem, String externalId, int reopenedCount, Instant resolvedAt,
                               Instant closedAt, Map<String, Object> customFields, List<CommentDto> comments,
                               List<AttachmentDto> attachments, List<HistoryDto> history, List<String> allowedTransitions) {}
}
