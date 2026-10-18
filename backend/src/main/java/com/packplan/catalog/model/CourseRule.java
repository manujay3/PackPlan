package com.packplan.catalog.model;

import java.util.Objects;

/** Provenance belongs to each rule, independently of the review of course metadata. */
public record CourseRule(
        String courseId,
        String catalogYear,
        String sourceUrl,
        String sourceSha256,
        String retrievedAt,
        String originalText,
        ReviewStatus reviewStatus,
        RequirementExpression expression
) {
    public CourseRule {
        requireText(courseId, "courseId");
        requireText(catalogYear, "catalogYear");
        requireText(sourceUrl, "sourceUrl");
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be a SHA-256 hex digest");
        }
        requireText(retrievedAt, "retrievedAt");
        java.time.Instant.parse(retrievedAt);
        requireText(originalText, "originalText");
        Objects.requireNonNull(reviewStatus, "reviewStatus");
        Objects.requireNonNull(expression, "expression");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
