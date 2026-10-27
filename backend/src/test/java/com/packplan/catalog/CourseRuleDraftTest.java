package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.ReviewStatus;
import com.packplan.catalog.model.CatalogCourses;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static com.packplan.catalog.model.RequirementExpression.*;
import static org.assertj.core.api.Assertions.assertThat;

class CourseRuleDraftTest {
    private CourseRule load(String number) throws Exception {
        var mapper = new ObjectMapper();
        try (var input = getClass().getResourceAsStream("/catalog/courses.json")) {
            return new CourseRuleLoader(mapper).load(
                    new ClassPathResource("catalog/drafts/csc-" + number + "-rule.json"),
                    mapper.readValue(input, CatalogCourses.class));
        }
    }

    @Test
    void csc217RequiresPassing116AndPriorOrConcurrent216() throws Exception {
        var rule = load("217");
        assertThat(rule.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
        assertThat(rule.originalText()).isEqualTo(
                "Prerequisite: CSC 116 with a C or better; Corequisites: CSC 216");
        assertThat(rule.expression()).isEqualTo(new AllOf(java.util.List.of(
                new Course("csc-116-2026-2027", Timing.COMPLETED_BEFORE, MinimumGrade.C),
                new Course("csc-216-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT,
                        MinimumGrade.NOT_SPECIFIED))));
    }

    @Test
    void csc116RequiresBothAlternativeGroupsAndRetainsSeatReservationsForManualReview() throws Exception {
        var rule = load("116");
        assertThat(rule.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(rule.expression()).isInstanceOfSatisfying(AllOf.class, group -> {
            assertThat(group.children()).hasSize(3);
            assertThat(group.children().get(0)).isEqualTo(new AnyOf(java.util.List.of(
                    new Course("e-115-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT, MinimumGrade.NOT_SPECIFIED),
                    new Course("cos-100-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT, MinimumGrade.NOT_SPECIFIED))));
            assertThat(group.children().get(1)).isEqualTo(new AnyOf(java.util.List.of(
                    new Course("ma-121-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT, MinimumGrade.NOT_SPECIFIED),
                    new Course("ma-131-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT, MinimumGrade.NOT_SPECIFIED),
                    new Course("ma-141-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT, MinimumGrade.NOT_SPECIFIED))));
            var seats = (Unsupported) group.children().getLast();
            assertThat(seats.originalText()).isEqualTo("Most seats reserved for Computer Science majors.");
            assertThat(seats.reason()).contains("not a blanket exclusion");
        });
    }

    @Test
    void readsCsc216RuleWithReviewedTimingAndBothConditions() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-216-rule.json")) {
            assertThat(input).isNotNull();
            var rule = new ObjectMapper().readValue(input, CourseRule.class);

            assertThat(rule.courseId()).isEqualTo("csc-216-2026-2027");
            assertThat(rule.catalogYear()).isEqualTo("2026-2027");
            assertThat(rule.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
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
