package com.suma.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets")
public class TicketLogImportController {
    private final TicketLogImportService service;

    public TicketLogImportController(TicketLogImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/import-log-sheet", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('TICKET_CREATE') and hasAuthority('TICKET_UPDATE')")
    @Operation(summary = "Import a ticket log sheet and create or update tickets")
    public TicketLogImportDtos.ImportResult importLogSheet(@RequestPart("file") MultipartFile file,
                                                            @RequestParam(required = false) UUID defaultProjectId,
                                                            @RequestParam(required = false) String sheetName) {
        return service.importFile(file, defaultProjectId, sheetName);
    }

    @PostMapping(value = "/import-log-sheet/sheets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('TICKET_CREATE') and hasAuthority('TICKET_UPDATE')")
    @Operation(summary = "List worksheets in an Excel ticket log")
    public java.util.List<String> listLogSheetWorksheets(@RequestPart("file") MultipartFile file) {
        return service.listSheetNames(file);
    }
}
