package com.suma.ticket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TicketLogImportServiceTest {
    @Test
    void recognizesTheTicketExportHeaders() {
        var mapping = TicketLogImportService.Mapping.from(List.of(
                "index", "Sr.no", "Ticket ID", "Date of Creation", "Topic", "Status", "Priority", "Requester"));

        assertEquals(2, mapping.ticketNumber());
        assertTrue(mapping.hasExplicitTicketNumber());
        assertEquals(4, mapping.title());
        assertEquals(5, mapping.status());
        assertEquals(6, mapping.priority());
        assertEquals(7, mapping.reporterName());
    }

    @Test
    void doesNotTreatAnUnmappedIndexColumnAsTheTicketTitle() {
        var mapping = TicketLogImportService.Mapping.from(List.of("index", "Ticket ID", "Date of Creation"));

        assertNull(mapping.title());
    }

    @Test
    void normalizesExcelIntegerTicketNumbersWithoutRemovingLeadingZeros() {
        assertEquals("117397", TicketLogImportService.normalizeExternalNumber("117397.0"));
        assertEquals("00123", TicketLogImportService.normalizeExternalNumber("00123.00"));
        assertEquals("117397.5", TicketLogImportService.normalizeExternalNumber("117397.5"));
    }

    @Test
    void mapsSourceStatusesToStandardTicketStatuses() {
        assertEquals("RESOLVED", TicketLogImportService.mapStatus("Solved"));
        assertEquals("CLOSED", TicketLogImportService.mapStatus("Merged"));
        assertEquals("NEW", TicketLogImportService.mapStatus("Open"));
        assertNull(TicketLogImportService.mapStatus("Unknown status"));
    }

    @Test
    void allowsImportedOpenTicketsToReopenFromResolvedOrClosed() {
        assertEquals(List.of("REOPENED", "NEW"),
                TicketService.statusTransitionPath("CLOSED", "NEW").orElseThrow());
        assertEquals(List.of("REOPENED", "NEW"),
                TicketService.statusTransitionPath("RESOLVED", "NEW").orElseThrow());
    }

    @Test
    void retainsTopicHeaderAndValueForSearchAlongsideTicketTitle() {
        List<String> headers = List.of("Ticket ID", "Topic");
        var mapping = TicketLogImportService.Mapping.from(headers);
        var row = new TicketLogFileParser.DataRow(2, List.of("117397", "Printer connection issue"));

        var fields = TicketLogImportService.extraFields(headers, row, mapping, "117397", true, null);

        assertEquals("Printer connection issue", fields.get("Imported: Topic"));
    }
}
