package com.suma.knowledge;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class KnowledgeService {
    private final KbArticleRepository articles;
    private final KbCategoryRepository categories;
    private final AccessScopeService scope;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeService(KbArticleRepository articles, KbCategoryRepository categories, AccessScopeService scope, AuditService audit,
                            NamedParameterJdbcTemplate jdbc) {
        this.articles = articles;
        this.categories = categories;
        this.scope = scope;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    public record ArticleRequest(String title, String summary, String content, String articleType, UUID categoryId, UUID projectId,
                                 String tags, String status) {}

    public record ArticleRow(UUID id, String title, String slug, String summary, String articleType, UUID categoryId, String categoryName,
                             UUID projectId, String projectName, String tags, String status, String authorName, int viewCount,
                             int helpfulCount, Instant updatedAt, Double rank, String snippet) {}

    public record ArticleDetail(ArticleRow article, String content) {}

    @Transactional(readOnly = true)
    public List<ArticleRow> search(AuthUser me, String q, String type, UUID categoryId, UUID projectId, String status) {
        Set<UUID> projects = scope.visibleProjectIds(me);
        boolean canDraft = me.has("KB_WRITE");
        var p = new MapSqlParameterSource().addValue("q", q == null || q.isBlank() ? null : q.trim())
                .addValue("type", type).addValue("category", categoryId).addValue("project", projectId)
                .addValue("status", canDraft ? status : "PUBLISHED")
                .addValue("restrict", projects != null)
                .addValue("projects", projects == null || projects.isEmpty() ? List.of(new UUID(0, 0)) : projects);
        return jdbc.query("""
                select a.id, a.title, a.slug, a.summary, a.article_type, a.category_id, c.name as category_name, a.project_id,
                  pr.name as project_name, a.tags, a.status, u.full_name as author_name, a.view_count, a.helpful_count, a.updated_at,
                  case when cast(:q as text) is null then null else ts_rank(a.search_vector, websearch_to_tsquery('english', :q)) end as rank,
                  case when cast(:q as text) is null then null else ts_headline('english', a.content, websearch_to_tsquery('english', :q),
                     'MaxWords=30, MinWords=10, StartSel=[[, StopSel=]]') end as snippet
                from kb_articles a left join kb_categories c on c.id = a.category_id left join projects pr on pr.id = a.project_id
                left join users u on u.id = a.author_id
                where (cast(:q as text) is null or a.search_vector @@ websearch_to_tsquery('english', :q))
                  and (cast(:type as varchar) is null or a.article_type = :type)
                  and (cast(:category as uuid) is null or a.category_id = :category)
                  and (cast(:project as uuid) is null or a.project_id = :project)
                  and (cast(:status as varchar) is null or a.status = :status)
                  and (a.status <> 'ARCHIVED' or cast(:status as varchar) = 'ARCHIVED')
                  and (a.project_id is null or :restrict = false or a.project_id in (:projects))
                """
                + " order by rank desc nulls last, a.helpful_count desc, a.updated_at desc limit 200",
                p, (rs, i) -> new ArticleRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("slug"), rs.getString("summary"),
                        rs.getString("article_type"), rs.getObject("category_id", UUID.class), rs.getString("category_name"),
                        rs.getObject("project_id", UUID.class), rs.getString("project_name"), rs.getString("tags"), rs.getString("status"),
                        rs.getString("author_name"), rs.getInt("view_count"), rs.getInt("helpful_count"), rs.getTimestamp("updated_at").toInstant(),
                        rs.getObject("rank") == null ? null : rs.getDouble("rank"), rs.getString("snippet")));
    }

    @Transactional
    public ArticleDetail view(AuthUser me, UUID id, UUID ticketId) {
        KbArticle a = load(me, id);
        if (!"PUBLISHED".equals(a.getStatus()) && !me.has("KB_WRITE")) throw ApiException.notFound("Article");
        a.setViewCount(a.getViewCount() + 1);
        usage(a.getId(), me.id(), ticketId, ticketId == null ? "VIEW" : "LINKED");
        return detail(me, a);
    }

    @Transactional
    public void helpful(AuthUser me, UUID id) {
        KbArticle a = load(me, id);
        Integer already = jdbc.queryForObject("select count(*) from kb_article_usage where article_id = :a and user_id = :u and action = 'HELPFUL'",
                new MapSqlParameterSource("a", id).addValue("u", me.id()), Integer.class);
        if (already != null && already > 0) return;
        a.setHelpfulCount(a.getHelpfulCount() + 1);
        usage(id, me.id(), null, "HELPFUL");
    }

    @Transactional
    public ArticleDetail save(AuthUser me, UUID id, ArticleRequest r) {
        if (r.title() == null || r.title().isBlank() || r.content() == null || r.content().isBlank()) throw ApiException.badRequest("Title and content are required");
        if (!Set.of("SOP", "TROUBLESHOOTING", "FAQ", "RESOLUTION").contains(r.articleType())) throw ApiException.badRequest("Invalid article type");
        if (r.projectId() != null) scope.requireProject(me, r.projectId());
        KbArticle a = id == null ? new KbArticle() : load(me, id);
        a.setTitle(r.title().trim());
        if (id == null) {
            a.setSlug(uniqueSlug(r.title()));
            a.setAuthorId(me.id());
        }
        a.setSummary(r.summary());
        a.setContent(r.content());
        a.setArticleType(r.articleType());
        a.setCategoryId(r.categoryId());
        a.setProjectId(r.projectId());
        a.setTags(r.tags());
        if (r.status() != null) {
            if (!Set.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(r.status())) throw ApiException.badRequest("Invalid status");
            a.setStatus(r.status());
        }
        a.setUpdatedAt(Instant.now());
        articles.saveAndFlush(a);
        audit.log(id == null ? "KB_ARTICLE_CREATED" : "KB_ARTICLE_UPDATED", "KbArticle", a.getId(), a.getTitle() + " [" + a.getStatus() + "]");
        return detail(me, a);
    }

    @Transactional(readOnly = true)
    public List<KbCategory> categories() {
        return categories.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional
    public KbCategory addCategory(String name, UUID parentId) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Name required");
        if (categories.existsByNameIgnoreCase(name)) throw ApiException.conflict("Category exists");
        KbCategory c = new KbCategory();
        c.setName(name.trim());
        c.setParentId(parentId);
        return categories.save(c);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> usageStats() {
        return jdbc.queryForList("""
                select a.title, a.view_count as views, a.helpful_count as helpful,
                  (select count(*) from kb_article_usage x where x.article_id = a.id and x.action = 'LINKED') as "linkedTickets"
                from kb_articles a where a.status = 'PUBLISHED' order by a.view_count desc limit 20
                """, new MapSqlParameterSource());
    }

    private KbArticle load(AuthUser me, UUID id) {
        KbArticle a = articles.findById(id).orElseThrow(() -> ApiException.notFound("Article"));
        if (a.getProjectId() != null && !scope.canViewProject(me, a.getProjectId())) throw ApiException.notFound("Article");
        return a;
    }

    private void usage(UUID articleId, UUID userId, UUID ticketId, String action) {
        jdbc.update("insert into kb_article_usage (id, article_id, user_id, ticket_id, action) values (:id, :a, :u, :t, :x)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("a", articleId).addValue("u", userId).addValue("t", ticketId).addValue("x", action));
    }

    private String uniqueSlug(String title) {
        String base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "article";
        if (base.length() > 200) base = base.substring(0, 200);
        String slug = base;
        int i = 2;
        while (articles.existsBySlug(slug)) slug = base + "-" + i++;
        return slug;
    }

    private ArticleDetail detail(AuthUser me, KbArticle a) {
        var rows = jdbc.query("""
                select c.name as category_name, pr.name as project_name, u.full_name as author_name from kb_articles a
                left join kb_categories c on c.id = a.category_id left join projects pr on pr.id = a.project_id left join users u on u.id = a.author_id
                where a.id = :id
                """, new MapSqlParameterSource("id", a.getId()), (rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
        String[] n = rows.isEmpty() ? new String[3] : rows.get(0);
        return new ArticleDetail(new ArticleRow(a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getArticleType(), a.getCategoryId(), n[0],
                a.getProjectId(), n[1], a.getTags(), a.getStatus(), n[2], a.getViewCount(), a.getHelpfulCount(), a.getUpdatedAt(), null, null), a.getContent());
    }
}
