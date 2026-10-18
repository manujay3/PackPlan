package com.packplan.catalog.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Objects;

/** Structured rules; unsupported conditions remain visible until an evaluator can handle them. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RequirementExpression.AllOf.class, name = "allOf"),
        @JsonSubTypes.Type(value = RequirementExpression.AnyOf.class, name = "anyOf"),
        @JsonSubTypes.Type(value = RequirementExpression.Course.class, name = "course"),
        @JsonSubTypes.Type(value = RequirementExpression.Unsupported.class, name = "unsupported")
})
public sealed interface RequirementExpression {
    record AllOf(List<RequirementExpression> children) implements RequirementExpression {
        public AllOf { children = checkedChildren(children); }
    }

    record AnyOf(List<RequirementExpression> children) implements RequirementExpression {
        public AnyOf { children = checkedChildren(children); }
    }

    /** courseId identifies a course in the same year-specific catalog as the owning rule. */
    record Course(String courseId, Timing timing, MinimumGrade minimumGrade)
            implements RequirementExpression {
        public Course {
            requireText(courseId, "courseId");
            Objects.requireNonNull(timing, "timing");
            Objects.requireNonNull(minimumGrade, "minimumGrade");
        }
    }

    record Unsupported(String originalText, String reason) implements RequirementExpression {
        public Unsupported {
            requireText(originalText, "originalText");
            requireText(reason, "reason");
        }
    }

    enum Timing { COMPLETED_BEFORE, COMPLETED_BEFORE_OR_CONCURRENT, CONCURRENT }

    /** NOT_SPECIFIED preserves absence of a threshold; it does not mean any grade passes. */
    enum MinimumGrade {
        NOT_SPECIFIED, A_PLUS, A, A_MINUS, B_PLUS, B, B_MINUS,
        C_PLUS, C, C_MINUS, D_PLUS, D, D_MINUS
    }

    private static List<RequirementExpression> checkedChildren(List<RequirementExpression> children) {
        var copy = List.copyOf(Objects.requireNonNull(children, "children"));
        if (copy.isEmpty()) throw new IllegalArgumentException("Rule groups must not be empty");
        return copy;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
