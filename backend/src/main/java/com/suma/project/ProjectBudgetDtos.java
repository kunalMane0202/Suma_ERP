package com.suma.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectBudgetDtos {
    private ProjectBudgetDtos() {}

    public record Dashboard(Snapshot current, List<CategoryTotal> categoryTotals, List<BudgetLine> items,
                            List<UploadSummary> uploads) {}

    public record Snapshot(UUID id, String fileName, String currency, BigDecimal totalBudget, int itemCount,
                           Instant uploadedAt, String uploadedByName) {}

    public record CategoryTotal(String category, BigDecimal amount, int itemCount) {}

    public record BudgetLine(String category, String item, BigDecimal quantity, BigDecimal unitCost,
                             BigDecimal amount) {}

    public record UploadSummary(UUID id, String fileName, String currency, BigDecimal totalBudget,
                                int itemCount, Instant uploadedAt, String uploadedByName) {}
}
