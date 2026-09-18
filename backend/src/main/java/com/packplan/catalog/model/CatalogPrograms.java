package com.packplan.catalog.model;

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
}
