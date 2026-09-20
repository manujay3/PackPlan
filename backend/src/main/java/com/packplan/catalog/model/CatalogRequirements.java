package com.packplan.catalog.model;

import java.util.List;

public record CatalogRequirements(
        String catalogYear,
        String programId,
        String sourceUrl,
        String sourceSha256,
        List<Requirement> requirements
) {
    public record Requirement(
            String id,
            String name,
            String originalText,
            int requiredCredits,
            ReviewStatus reviewStatus,
            boolean evaluable,
            Rule rule
    ) {}

    public record Rule(
            String id,
            RuleType type,
            List<Rule> children,
            String courseId,
            String minimumGrade
    ) {}

    public enum RuleType {
        ALL_OF,
        ANY_OF,
        COURSE
    }
}
