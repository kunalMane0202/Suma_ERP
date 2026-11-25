package com.suma.knowledge;

import com.suma.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Base & SOP")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/articles")
    @PreAuthorize("hasAuthority('KB_READ')")
    public List<KnowledgeService.ArticleRow> search(@RequestParam(required = false) String q, @RequestParam(required = false) String type,
                                                    @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) UUID projectId,
                                                    @RequestParam(required = false) String status) {
        return service.search(CurrentUser.get(), q, type, categoryId, projectId, status);
    }

    @GetMapping("/articles/{id}")
    @PreAuthorize("hasAuthority('KB_READ')")
    public KnowledgeService.ArticleDetail view(@PathVariable UUID id, @RequestParam(required = false) UUID ticketId) {
        return service.view(CurrentUser.get(), id, ticketId);
    }

    @PostMapping("/articles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('KB_WRITE')")
    public KnowledgeService.ArticleDetail create(@RequestBody KnowledgeService.ArticleRequest r) {
        return service.save(CurrentUser.get(), null, r);
    }

    @PutMapping("/articles/{id}")
    @PreAuthorize("hasAuthority('KB_WRITE')")
    public KnowledgeService.ArticleDetail update(@PathVariable UUID id, @RequestBody KnowledgeService.ArticleRequest r) {
        return service.save(CurrentUser.get(), id, r);
    }

    @PostMapping("/articles/{id}/helpful")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('KB_READ')")
    public void helpful(@PathVariable UUID id) {
        service.helpful(CurrentUser.get(), id);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('KB_READ')")
    public List<KbCategory> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('KB_WRITE')")
    public KbCategory addCategory(@RequestBody Map<String, String> body) {
        String parent = body.get("parentId");
        return service.addCategory(body.get("name"), parent == null || parent.isBlank() ? null : UUID.fromString(parent));
    }

    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('KB_READ')")
    public List<Map<String, Object>> usage() {
        return service.usageStats();
    }
}
