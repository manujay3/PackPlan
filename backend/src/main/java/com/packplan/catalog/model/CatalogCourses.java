package com.packplan.catalog.model;

import java.util.List;

public record CatalogCourses(
        String catalogYear,
        String sourceUrl,
        String sourceSha256,
        List<Course> courses
) {
    public record Course(
            String id,
            String code,
            String subject,
            String number,
            String title,
            int credits,
            ReviewStatus reviewStatus
    ) {}
}
