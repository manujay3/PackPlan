package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;

import static com.packplan.catalog.model.RequirementExpression.*;
import static org.assertj.core.api.Assertions.assertThat;

class CourseRuleDraftTest {
    @Test
    void readsCsc216DraftWithBothConditionsAndUnresolvedReviewStatus() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-216-rule.json")) {
            assertThat(input).isNotNull();
            var rule = new ObjectMapper().readValue(input, CourseRule.class);

            assertThat(rule.courseId()).isEqualTo("csc-216-2026-2027");
            assertThat(rule.catalogYear()).isEqualTo("2026-2027");
            assertThat(rule.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
            assertThat(rule.sourceUrl()).isEqualTo("https://catalog.ncsu.edu/course-descriptions/csc/");
            assertThat(rule.originalText()).isEqualTo(
                    "Prerequisite: CSC 116 with a grade of C or better; Corequisite: CSC 217");
            assertThat(rule.expression()).isInstanceOfSatisfying(AllOf.class, group ->
                    assertThat(group.children()).containsExactly(
                            new Course("csc-116-2026-2027", Timing.COMPLETED_BEFORE, MinimumGrade.C),
                            new Course("csc-217-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT,
                                    MinimumGrade.NOT_SPECIFIED)));
        }
    }
}
