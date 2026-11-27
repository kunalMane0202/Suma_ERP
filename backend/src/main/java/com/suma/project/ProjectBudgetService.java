package com.suma.project;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ProjectBudgetService {
    private static final Set<String> CURRENCIES = Set.of("INR", "USD", "EUR", "GBP", "AUD", "CAD", "JPY", "SGD");
    private final ProjectService projects;
    private final BudgetFileParser parser;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ProjectBudgetService(ProjectService projects, BudgetFileParser parser, JdbcTemplate jdbc, AuditService audit) {
        this.projects = projects;
        this.parser = parser;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProjectBudgetDtos.Dashboard dashboard(UUID projectId) {
        projects.load(projectId);
        List<ProjectBudgetDtos.UploadSummary> uploads = jdbc.query("""
                select u.id, u.file_name, u.currency, u.total_budget, u.item_count, u.uploaded_at, usr.full_name
                from project_budget_uploads u left join users usr on usr.id = u.uploaded_by
                where u.project_id = ? order by u.uploaded_at desc, u.id desc limit 20
                """, (rs, row) -> new ProjectBudgetDtos.UploadSummary(rs.getObject("id", UUID.class),
                rs.getString("file_name"), rs.getString("currency"), rs.getBigDecimal("total_budget"),
                rs.getInt("item_count"), rs.getTimestamp("uploaded_at").toInstant(), rs.getString("full_name")), projectId);
        if (uploads.isEmpty()) return new ProjectBudgetDtos.Dashboard(null, List.of(), List.of(), List.of());

        ProjectBudgetDtos.UploadSummary latest = uploads.get(0);
        ProjectBudgetDtos.Snapshot snapshot = new ProjectBudgetDtos.Snapshot(latest.id(), latest.fileName(),
                latest.currency(), latest.totalBudget(), latest.itemCount(), latest.uploadedAt(), latest.uploadedByName());
        List<ProjectBudgetDtos.CategoryTotal> categories = jdbc.query("""
                select category, sum(amount) as amount, count(*) as item_count
                from project_budget_items where upload_id = ?
                group by category order by amount desc, category
                """, (rs, row) -> new ProjectBudgetDtos.CategoryTotal(rs.getString("category"),
                rs.getBigDecimal("amount"), rs.getInt("item_count")), latest.id());
        List<ProjectBudgetDtos.BudgetLine> items = jdbc.query("""
                select category, item, quantity, unit_cost, amount
                from project_budget_items where upload_id = ? order by sort_order
                """, (rs, row) -> new ProjectBudgetDtos.BudgetLine(rs.getString("category"), rs.getString("item"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("amount")), latest.id());
        return new ProjectBudgetDtos.Dashboard(snapshot, categories, items, uploads);
    }

    @Transactional
    public ProjectBudgetDtos.Dashboard upload(UUID projectId, MultipartFile file, String currency) {
        projects.load(projectId);
        String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCIES.contains(code)) throw ApiException.badRequest("Choose a supported currency");
        List<BudgetFileParser.Item> items = parser.parse(file);
        BigDecimal total = items.stream().map(BudgetFileParser.Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (total.precision() > 18) throw ApiException.badRequest("Calculated project budget is too large");

        UUID uploadId = UUID.randomUUID();
        AuthUser me = CurrentUser.get();
        String fileName = safeFileName(file.getOriginalFilename());
        jdbc.update("""
                insert into project_budget_uploads (id, project_id, uploaded_by, file_name, currency, total_budget, item_count)
                values (?, ?, ?, ?, ?, ?, ?)
                """, uploadId, projectId, me.id(), fileName, code, total, items.size());
        List<Object[]> rows = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            BudgetFileParser.Item item = items.get(i);
            rows.add(new Object[]{UUID.randomUUID(), uploadId, item.category(), item.item(), item.quantity(),
                    item.unitCost(), item.amount(), i});
        }
        jdbc.batchUpdate("""
                insert into project_budget_items (id, upload_id, category, item, quantity, unit_cost, amount, sort_order)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
        audit.log("PROJECT_BUDGET_UPLOADED", "Project", projectId, fileName + " · " + total + " " + code);
        return dashboard(projectId);
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "budget-upload";
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isEmpty() || base.startsWith(".")) base = "budget-upload" + base;
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
