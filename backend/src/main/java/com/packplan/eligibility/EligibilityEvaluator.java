package com.packplan.eligibility;

import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;
import com.packplan.catalog.model.ReviewStatus;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.packplan.eligibility.EligibilityResult.*;
import static com.packplan.eligibility.EligibilityResult.Status.*;
import static com.packplan.catalog.model.RequirementExpression.*;

@Service
public class EligibilityEvaluator {
    public EligibilityResult evaluate(CourseRule rule, EnrollmentEvidence evidence) {
        var condition = evaluate(rule.expression(), evidence);
        var status = condition.status();
        if (rule.reviewStatus() != ReviewStatus.REVIEWED && status != BLOCKED) status = MANUAL_REVIEW;
        var reason = rule.reviewStatus() == ReviewStatus.REVIEWED
                ? "Result applies only to the stored enrollment rule."
                : "Rule requires review (" + rule.reviewStatus() + "); known blockers remain visible.";
        return new EligibilityResult(rule.courseId(), status, rule.reviewStatus(), reason, condition);
    }

    public EligibilityResult missingRule(String courseId) {
        return new EligibilityResult(courseId, MANUAL_REVIEW, null,
                "No stored rule is available. This does not imply unrestricted enrollment.", null);
    }

    private Condition evaluate(RequirementExpression expression, EnrollmentEvidence evidence) {
        return switch (expression) {
            case AllOf group -> group("allOf", group.children(), evidence);
            case AnyOf group -> group("anyOf", group.children(), evidence);
            case Unsupported unsupported -> new Condition("unsupported", null, MANUAL_REVIEW,
                    unsupported.originalText() + " " + unsupported.reason(), List.of());
            case Course course -> course(course, evidence);
        };
    }

    private Condition group(String type, List<RequirementExpression> expressions, EnrollmentEvidence evidence) {
        var children = expressions.stream().map(expression -> evaluate(expression, evidence)).toList();
        var statuses = children.stream().map(Condition::status).toList();
        Status status;
        if (type.equals("allOf")) {
            status = statuses.contains(BLOCKED) ? BLOCKED
                    : statuses.contains(MANUAL_REVIEW) ? MANUAL_REVIEW : SATISFIED;
        } else {
            status = statuses.contains(SATISFIED) ? SATISFIED
                    : statuses.contains(MANUAL_REVIEW) ? MANUAL_REVIEW : BLOCKED;
        }
        return new Condition(type, null, status,
                type.equals("allOf") ? "Every condition is required." : "At least one alternative is required.", children);
    }

    private Condition course(Course course, EnrollmentEvidence evidence) {
        var outcomes = new java.util.ArrayList<Condition>();
        if (course.timing() != Timing.CONCURRENT) {
            evidence.completedCourses().stream().filter(attempt -> attempt.courseId().equals(course.courseId()))
                    .map(attempt -> attempt(course, attempt)).forEach(outcomes::add);
        }
        if (course.timing() != Timing.COMPLETED_BEFORE && evidence.concurrentCourses().contains(course.courseId())) {
            outcomes.add(leaf(course, course.minimumGrade() == MinimumGrade.NOT_SPECIFIED ? SATISFIED : MANUAL_REVIEW,
                    "Concurrent enrollment meets timing; a future final grade is not verified."));
        }
        // Any qualifying attempt suffices; a later failed attempt does not erase earned credit.
        return outcomes.stream().filter(outcome -> outcome.status() == SATISFIED).findFirst()
                .or(() -> outcomes.stream().filter(outcome -> outcome.status() == MANUAL_REVIEW).findFirst())
                .orElseGet(() -> outcomes.stream().findFirst().orElseGet(() -> leaf(course, BLOCKED,
                        "No qualifying evidence for " + course.timing()
                                + " with minimum grade " + course.minimumGrade() + ".")));
    }

    private Condition attempt(Course course, EnrollmentEvidence.Attempt attempt) {
        if (!attempt.creditEarned()) return leaf(course, BLOCKED, "Attempt did not earn credit.");
        if (attempt.grade() == EnrollmentEvidence.Grade.TRANSFER || attempt.grade() == EnrollmentEvidence.Grade.UNKNOWN) {
            return leaf(course, MANUAL_REVIEW, "Transfer equivalency or unknown grade requires manual review.");
        }
        if (course.minimumGrade() == MinimumGrade.NOT_SPECIFIED) {
            return leaf(course, SATISFIED, "Prior earned credit; no grade threshold is specified.");
        }
        if (attempt.grade().rank < 0) return leaf(course, MANUAL_REVIEW, "Non-letter grade requires threshold review.");
        var minimum = EnrollmentEvidence.Grade.valueOf(course.minimumGrade().name());
        return leaf(course, attempt.grade().rank >= minimum.rank ? SATISFIED : BLOCKED,
                "Recorded grade " + attempt.grade() + "; required " + course.minimumGrade() + " or better.");
    }

    private Condition leaf(Course course, Status status, String reason) {
        return new Condition("course", course.courseId(), status, reason, List.of());
    }
}
