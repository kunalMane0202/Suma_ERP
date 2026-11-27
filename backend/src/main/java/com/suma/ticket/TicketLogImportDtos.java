package com.suma.ticket;

import java.util.List;
import java.util.UUID;

public final class TicketLogImportDtos {
    private TicketLogImportDtos() {}

    public record ImportResult(int created, int updated, int skipped, int total, List<RowResult> tickets) {}

    public record RowResult(UUID id, String ticketNumber, String externalTicketNumber, String title, String status,
                            String priority, String projectCode, boolean created) {}
}
