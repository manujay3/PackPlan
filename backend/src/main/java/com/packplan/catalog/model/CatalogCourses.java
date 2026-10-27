package com.packplan.catalog.model;

import java.util.List;

public record CatalogCourses(
        String catalogYear,
        String sourceUrl,
        String sourceSha256,
        List<Course> courses
) {
    /** Legacy records inherit the catalog source; other subjects supply their own provenance. */
    public Source sourceFor(Course course) {
        return course.source() == null ? new Source(sourceUrl, sourceSha256, null) : course.source();
    }

    public record Source(String sourceUrl, String sourceSha256, String retrievedAt) {
        public Source {
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("Course source URL is required");
            }
            if (sourceSha256 == null || !sourceSha256.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Course source checksum must be SHA-256");
            }
            if (retrievedAt != null) java.time.Instant.parse(retrievedAt);
        }
    }

    public record Course(
            String id,
            String code,
            String subject,
            String number,
            String title,
            int credits,
            ReviewStatus reviewStatus,
            Source source
    ) {
        public Course {
            if (source != null && source.retrievedAt() == null) {
                throw new IllegalArgumentException("An explicit course source requires retrievedAt");
            }
        }

        public Course(String id, String code, String subject, String number, String title,
                      int credits, ReviewStatus reviewStatus) {
            this(id, code, subject, number, title, credits, reviewStatus, null);
        }
    }
}
