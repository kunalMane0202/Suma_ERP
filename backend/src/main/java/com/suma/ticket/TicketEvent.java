package com.suma.ticket;

import java.util.UUID;

/** Domain event consumed by the automation engine. type: TICKET_CREATED, TICKET_UPDATED, TICKET_STATUS_CHANGED, TICKET_ASSIGNED, SLA_BREACHED, TICKET_PENDING_REMINDER */
public record TicketEvent(String type, UUID ticketId, UUID actorId, String detail) {}
