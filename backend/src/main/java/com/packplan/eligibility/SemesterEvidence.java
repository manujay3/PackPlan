package com.packplan.eligibility;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** All proposed courses belong to one target term; completed evidence precedes it. */
public record SemesterEvidence(List<EnrollmentEvidence.Attempt> completedCourses, List<String> proposedCourses) {
    public SemesterEvidence {
        completedCourses = List.copyOf(Objects.requireNonNull(completedCourses, "completedCourses"));
        proposedCourses = List.copyOf(Objects.requireNonNull(proposedCourses, "proposedCourses"));
        if (proposedCourses.isEmpty() || proposedCourses.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Select at least one nonblank proposed course");
        }
        if (new HashSet<>(proposedCourses).size() != proposedCourses.size()) {
            throw new IllegalArgumentException("Proposed courses must be unique");
        }
    }
}
