package com.suma.ticket;

import com.suma.common.PageResponse;
import com.suma.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets")
public class TicketController {
    private final TicketService service;
    private final AttachmentService attachments;

    public TicketController(TicketService service, AttachmentService attachments) {
        this.service = service;
        this.attachments = attachments;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public PageResponse<TicketDtos.TicketSummary> list(@RequestParam(required = false) String q, @RequestParam(required = false) UUID projectId,
                                                       @RequestParam(required = false) UUID clientId, @RequestParam(required = false) List<String> status,
                                                       @RequestParam(required = false) List<String> priority, @RequestParam(required = false) UUID assigneeId,
                                                       @RequestParam(required = false) UUID teamId, @RequestParam(required = false) Boolean overdue,
                                                       @RequestParam(required = false) Boolean breached, @RequestParam(required = false) Boolean mine,
                                                       @RequestParam(required = false) Boolean unassigned,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                       @RequestParam(required = false) String sort,
                                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.get(), new TicketService.Filter(q, projectId, clientId, status, priority, assigneeId, teamId,
                overdue, breached, mine, unassigned, from, to), page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public TicketDtos.TicketDetail get(@PathVariable UUID id) {
        return service.get(CurrentUser.get(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_CREATE')")
    public TicketDtos.TicketDetail create(@Valid @RequestBody TicketDtos.CreateRequest r) {
        return service.create(CurrentUser.get(), r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public TicketDtos.TicketDetail update(@PathVariable UUID id, @Valid @RequestBody TicketDtos.UpdateRequest r) {
        return service.update(CurrentUser.get(), id, r);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public TicketDtos.TicketDetail status(@PathVariable UUID id, @Valid @RequestBody TicketDtos.StatusRequest r) {
        return service.changeStatus(CurrentUser.get(), id, r);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('TICKET_ASSIGN')")
    public TicketDtos.TicketDetail assign(@PathVariable UUID id, @Valid @RequestBody TicketDtos.AssignRequest r) {
        return service.assign(CurrentUser.get(), id, r);
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAuthority('TICKET_ESCALATE')")
    public TicketDtos.TicketDetail escalate(@PathVariable UUID id, @Valid @RequestBody TicketDtos.EscalateRequest r) {
        return service.escalate(CurrentUser.get(), id, r.reason());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public TicketDtos.CommentDto comment(@PathVariable UUID id, @Valid @RequestBody TicketDtos.CommentRequest r) {
        return service.comment(CurrentUser.get(), id, r);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_UPDATE')")
    public TicketDtos.AttachmentDto upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return attachments.upload(CurrentUser.get(), id, file);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('TICKET_READ')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        var d = attachments.download(CurrentUser.get(), id, attachmentId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(d.contentType())).body(d.content());
    }
}
