package com.packplan.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static com.packplan.catalog.model.RequirementExpression.*;
import static com.packplan.eligibility.EligibilityResult.Status.*;
import static com.packplan.eligibility.EnrollmentEvidence.*;
import static org.assertj.core.api.Assertions.*;

class EligibilityEvaluatorTest {
    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();
    private static final String ID = "sample-2026-2027";

    private CourseRule rule(RequirementExpression expression, ReviewStatus review) {
        return new CourseRule("target-2026-2027", "2026-2027", "https://example.com", "a".repeat(64),
                "2026-10-27T00:00:00Z", "Synthetic test rule", review, expression);
    }

    private EligibilityResult evaluate(RequirementExpression expression, EnrollmentEvidence evidence) {
        return evaluator.evaluate(rule(expression, ReviewStatus.REVIEWED), evidence);
    }

    private EnrollmentEvidence completed(Grade grade, boolean credit) {
        return new EnrollmentEvidence(List.of(new Attempt(ID, grade, credit)), Set.of());
    }

    @ParameterizedTest
    @CsvSource({"A_PLUS,A_PLUS,SATISFIED", "A,A_PLUS,BLOCKED", "B_PLUS,B,SATISFIED", "B_MINUS,B,BLOCKED",
            "C_PLUS,C,SATISFIED", "C,C,SATISFIED", "C_MINUS,C,BLOCKED", "D_MINUS,D_MINUS,SATISFIED",
            "D_MINUS,C,BLOCKED", "SATISFACTORY,C,MANUAL_REVIEW", "CREDIT,C,MANUAL_REVIEW",
            "TRANSFER,C,MANUAL_REVIEW", "UNKNOWN,C,MANUAL_REVIEW"})
    void comparesGradeThresholdsAndPreservesUncertainGrades(Grade grade, MinimumGrade minimum,
                                                            EligibilityResult.Status expected) {
        assertThat(evaluate(new Course(ID, Timing.COMPLETED_BEFORE, minimum), completed(grade, true)).status())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = Grade.class, names = {"F", "WITHDRAWN", "INCOMPLETE", "UNSATISFACTORY", "A"})
    void attemptsWithoutEarnedCreditNeverSatisfyEvenUnspecifiedThresholds(Grade grade) {
        assertThat(evaluate(new Course(ID, Timing.COMPLETED_BEFORE, MinimumGrade.NOT_SPECIFIED),
                completed(grade, false)).status()).isEqualTo(BLOCKED);
    }

    @ParameterizedTest
    @EnumSource(value = Grade.class, names = {"D_MINUS", "SATISFACTORY", "CREDIT"})
    void unspecifiedThresholdAcceptsExplicitEarnedCredit(Grade grade) {
        assertThat(evaluate(new Course(ID, Timing.COMPLETED_BEFORE, MinimumGrade.NOT_SPECIFIED),
                completed(grade, true)).status()).isEqualTo(SATISFIED);
    }

    @ParameterizedTest
    @EnumSource(value = Grade.class, names = {"TRANSFER", "UNKNOWN"})
    void transferAndUnknownGradesRequireReviewEvenWithoutThreshold(Grade grade) {
        assertThat(evaluate(new Course(ID, Timing.COMPLETED_BEFORE, MinimumGrade.NOT_SPECIFIED),
                completed(grade, true)).status()).isEqualTo(MANUAL_REVIEW);
    }

    @Test
    void aQualifyingRepeatIsIndependentOfAttemptOrder() {
        var good = new Attempt(ID, Grade.C, true);
        var bad = new Attempt(ID, Grade.F, false);
        var unknown = new Attempt(ID, Grade.UNKNOWN, true);
        var course = new Course(ID, Timing.COMPLETED_BEFORE, MinimumGrade.C);
        for (var attempts : List.of(List.of(good, bad, unknown), List.of(unknown, bad, good))) {
            assertThat(evaluate(course, new EnrollmentEvidence(attempts, Set.of())).status()).isEqualTo(SATISFIED);
        }
    }

    @Test
    void concurrentEnrollmentDoesNotSatisfyStrictPrerequisitesOrPromiseFinalGrades() {
        var evidence = new EnrollmentEvidence(List.of(), Set.of(ID));
        assertThat(evaluate(new Course(ID, Timing.COMPLETED_BEFORE, MinimumGrade.NOT_SPECIFIED), evidence).status())
                .isEqualTo(BLOCKED);
        assertThat(evaluate(new Course(ID, Timing.COMPLETED_BEFORE_OR_CONCURRENT, MinimumGrade.NOT_SPECIFIED), evidence).status())
                .isEqualTo(SATISFIED);
        assertThat(evaluate(new Course(ID, Timing.CONCURRENT, MinimumGrade.NOT_SPECIFIED), evidence).status())
                .isEqualTo(SATISFIED);
        assertThat(evaluate(new Course(ID, Timing.CONCURRENT, MinimumGrade.C), evidence).status())
                .isEqualTo(MANUAL_REVIEW);
        assertThat(evaluate(new Course(ID, Timing.CONCURRENT, MinimumGrade.NOT_SPECIFIED), completed(Grade.A, true)).status())
                .isEqualTo(BLOCKED);
    }

    private RequirementExpression condition(EligibilityResult.Status status) {
        return switch (status) {
            case SATISFIED -> new Course(ID, Timing.COMPLETED_BEFORE, MinimumGrade.C);
            case BLOCKED -> new Course("missing-2026-2027", Timing.COMPLETED_BEFORE, MinimumGrade.C);
            case MANUAL_REVIEW -> new Unsupported("Permission", "Manual approval required");
        };
    }

    @ParameterizedTest
    @CsvSource({"SATISFIED,SATISFIED,SATISFIED,SATISFIED", "SATISFIED,BLOCKED,BLOCKED,SATISFIED",
            "SATISFIED,MANUAL_REVIEW,MANUAL_REVIEW,SATISFIED", "BLOCKED,SATISFIED,BLOCKED,SATISFIED",
            "BLOCKED,BLOCKED,BLOCKED,BLOCKED", "BLOCKED,MANUAL_REVIEW,BLOCKED,MANUAL_REVIEW",
            "MANUAL_REVIEW,SATISFIED,MANUAL_REVIEW,SATISFIED", "MANUAL_REVIEW,BLOCKED,BLOCKED,MANUAL_REVIEW",
            "MANUAL_REVIEW,MANUAL_REVIEW,MANUAL_REVIEW,MANUAL_REVIEW"})
    void combinesAllThreeStatusesWithoutDroppingChildExplanations(EligibilityResult.Status left,
            EligibilityResult.Status right, EligibilityResult.Status all, EligibilityResult.Status any) {
        var children = List.of(condition(left), condition(right));
        var evidence = completed(Grade.C, true);
        var and = evaluate(new AllOf(children), evidence);
        var or = evaluate(new AnyOf(children), evidence);
        assertThat(and.status()).isEqualTo(all);
        assertThat(or.status()).isEqualTo(any);
        assertThat(and.condition().children()).extracting(EligibilityResult.Condition::status).containsExactly(left, right);
        assertThat(or.condition().children()).extracting(EligibilityResult.Condition::status).containsExactly(left, right);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"UNREVIEWED", "NEEDS_REVIEW"})
    void unreviewedRulesCannotReportSatisfiedButStillReportKnownBlockers(ReviewStatus review) {
        assertThat(evaluator.evaluate(rule(condition(SATISFIED), review), completed(Grade.C, true)).status())
                .isEqualTo(MANUAL_REVIEW);
        assertThat(evaluator.evaluate(rule(condition(BLOCKED), review), completed(Grade.C, true)).status())
                .isEqualTo(BLOCKED);
    }

    @Test
    void missingRulesRequireManualReview() {
        var result = evaluator.missingRule(ID);
        assertThat(result.status()).isEqualTo(MANUAL_REVIEW);
        assertThat(result.condition()).isNull();
        assertThat(result.reviewStatus()).isNull();
    }

    @Test
    void csc116NeedsBothAlternativeGroupsAndStillRequiresSeatReview() throws Exception {
        CourseRule sample;
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-116-rule.json")) {
            sample = new ObjectMapper().readValue(input, CourseRule.class);
        }
        var evidence = new EnrollmentEvidence(List.of(), Set.of("cos-100-2026-2027", "ma-131-2026-2027"));
        var result = evaluator.evaluate(sample, evidence);
        assertThat(result.status()).isEqualTo(MANUAL_REVIEW);
        assertThat(result.condition().children()).extracting(EligibilityResult.Condition::status)
                .containsExactly(SATISFIED, SATISFIED, MANUAL_REVIEW);
        assertThat(evaluator.evaluate(sample, new EnrollmentEvidence(List.of(), Set.of("e-115-2026-2027"))).status())
                .isEqualTo(BLOCKED);
    }

    @Test
    void exactCourseIdentitiesDoNotSilentlyMapAcrossCatalogYears() {
        var evidence = new EnrollmentEvidence(List.of(new Attempt("sample-2025-2026", Grade.A, true)), Set.of());
        assertThat(evaluate(new Course(ID, Timing.COMPLETED_BEFORE, MinimumGrade.C), evidence).status()).isEqualTo(BLOCKED);
    }
}
