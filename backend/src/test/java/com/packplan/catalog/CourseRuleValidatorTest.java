package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.packplan.catalog.model.RequirementExpression.*;
import static org.assertj.core.api.Assertions.*;

class CourseRuleValidatorTest {
    private final CourseRuleValidator validator = new CourseRuleValidator();

    private CatalogCourses catalog() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/courses.json")) {
            return new ObjectMapper().readValue(input, CatalogCourses.class);
        }
    }

    private CourseRule rule(String owner, String year, RequirementExpression expression) {
        return new CourseRule(owner, year, "https://example.com/catalog", "a".repeat(64),
                "2026-10-18T00:00:00Z", "Synthetic test rule", ReviewStatus.NEEDS_REVIEW, expression);
    }

    @Test
    void draftReferencesExistWithoutChangingItsReviewStatus() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-216-rule.json")) {
            var draft = new ObjectMapper().readValue(input, CourseRule.class);
            validator.validateReferences(draft, catalog());
            assertThat(draft.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
        }
    }

    @Test
    void checksEveryAlternativeEvenWhenAnotherAlternativeExists() throws Exception {
        var catalog = catalog();
        var expression = new AllOf(List.of(new AnyOf(List.of(
                new Course("csc-116-2026-2027", Timing.COMPLETED_BEFORE, MinimumGrade.C),
                new Course("missing-2026-2027", Timing.COMPLETED_BEFORE, MinimumGrade.C)))));
        assertThatThrownBy(() -> validator.validateReferences(
                rule("csc-216-2026-2027", "2026-2027", expression), catalog))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing-2026-2027");
    }

    @Test
    void rejectsMissingOwnerAndMismatchedCatalogYear() throws Exception {
        var catalog = catalog();
        var expression = new Unsupported("Permission", "Manual review");
        assertThatThrownBy(() -> validator.validateReferences(
                rule("missing-2026-2027", "2026-2027", expression), catalog))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing-2026-2027");
        assertThatThrownBy(() -> validator.validateReferences(
                rule("csc-216-2026-2027", "2025-2026", expression), catalog))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("years must match");
    }

    @Test
    void rejectsWrongYearReferenceEvenIfAccidentallyIncludedInCatalog() throws Exception {
        var catalog = catalog();
        var wrongYear = new CatalogCourses.Course("csc-116-2025-2026", "CSC 116", "CSC", "116",
                "Example", 3, ReviewStatus.UNREVIEWED);
        var mixed = new CatalogCourses(catalog.catalogYear(), catalog.sourceUrl(), catalog.sourceSha256(),
                List.of(catalog.courses().get(1), wrongYear));
        var expression = new Course(wrongYear.id(), Timing.COMPLETED_BEFORE, MinimumGrade.C);
        assertThatThrownBy(() -> validator.validateReferences(
                rule("csc-216-2026-2027", "2026-2027", expression), mixed))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not match catalog year");
    }
}
