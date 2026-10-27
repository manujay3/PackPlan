package com.packplan.eligibility;

import com.packplan.catalog.model.ReviewStatus;

import java.util.List;

/** SATISFIED applies only to this stored rule, not to registration or graduation eligibility. */
public record EligibilityResult(String courseId, Status status, ReviewStatus reviewStatus,
                                String reason, Condition condition) {
    public enum Status { SATISFIED, BLOCKED, MANUAL_REVIEW }

    public record Condition(String type, String courseId, Status status, String reason,
                            List<Condition> children) {
        public Condition { children = List.copyOf(children); }
    }
}
