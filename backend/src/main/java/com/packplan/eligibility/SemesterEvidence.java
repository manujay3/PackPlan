package com.packplan.eligibility;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** All proposed courses belong to one target term; completed evidence precedes it. */
public record SemesterEvidence(List<EnrollmentEvidence.Attempt> completedCourses, List<String> proposedCourses,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = CreditCapDeserializer.class) Integer creditCap) {
    public SemesterEvidence {
        completedCourses = List.copyOf(Objects.requireNonNull(completedCourses, "completedCourses"));
        proposedCourses = List.copyOf(Objects.requireNonNull(proposedCourses, "proposedCourses"));
        if (creditCap == null || creditCap < 1) {
            throw new IllegalArgumentException("creditCap must be a positive whole number");
        }
        if (proposedCourses.isEmpty() || proposedCourses.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Select at least one nonblank proposed course");
        }
        if (new HashSet<>(proposedCourses).size() != proposedCourses.size()) {
            throw new IllegalArgumentException("Proposed courses must be unique");
        }
    }

    public static class CreditCapDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            if (!parser.isExpectedNumberIntToken()) {
                return context.reportInputMismatch(Integer.class, "creditCap must be a positive whole number");
            }
            return parser.getIntValue();
        }
    }
}
