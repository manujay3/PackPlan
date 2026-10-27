package com.packplan.eligibility;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Caller asserts attempts precede the target term; concurrent IDs belong to that term only. */
public record EnrollmentEvidence(List<Attempt> completedCourses, Set<String> concurrentCourses) {
    public EnrollmentEvidence {
        completedCourses = List.copyOf(Objects.requireNonNull(completedCourses, "completedCourses"));
        concurrentCourses = Set.copyOf(Objects.requireNonNull(concurrentCourses, "concurrentCourses"));
        concurrentCourses.forEach(EnrollmentEvidence::requireId);
    }

    public record Attempt(String courseId, Grade grade, Boolean creditEarned) {
        public Attempt {
            requireId(courseId);
            Objects.requireNonNull(grade, "grade");
            Objects.requireNonNull(creditEarned, "creditEarned");
            if (creditEarned && (grade == Grade.F || grade == Grade.UNSATISFACTORY
                    || grade == Grade.WITHDRAWN || grade == Grade.INCOMPLETE)) {
                throw new IllegalArgumentException("This outcome cannot assert earned credit");
            }
        }
    }

    public enum Grade {
        A_PLUS(13), A(12), A_MINUS(11), B_PLUS(10), B(9), B_MINUS(8),
        C_PLUS(7), C(6), C_MINUS(5), D_PLUS(4), D(3), D_MINUS(2), F(0),
        SATISFACTORY(-1), UNSATISFACTORY(-1), CREDIT(-1), TRANSFER(-1),
        WITHDRAWN(-1), INCOMPLETE(-1), UNKNOWN(-1);

        final int rank;
        Grade(int rank) { this.rank = rank; }

        @com.fasterxml.jackson.annotation.JsonCreator
        public static Grade fromJson(String value) { return Grade.valueOf(value); }
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("courseId must not be blank");
    }
}
