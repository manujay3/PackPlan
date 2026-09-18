package com.packplan;

import java.util.List;

public record CatalogPrograms(String catalogYear, List<Program> programs) {
    public record Program(
            String id,
            String name,
            String degree,
            int totalCredits,
            String sourceUrl,
            ReviewStatus reviewStatus
    ) {}

    public enum ReviewStatus {
        UNREVIEWED,
        NEEDS_REVIEW,
        REVIEWED
    }
}
