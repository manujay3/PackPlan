package com.packplan.catalog;

import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;

import java.util.Set;
import java.util.stream.Collectors;

/** Checks references only; success does not establish academic review or eligibility. */
public final class CourseRuleValidator {
    public void validateReferences(CourseRule rule, CatalogCourses catalog) {
        if (!rule.catalogYear().equals(catalog.catalogYear())) {
            throw new IllegalArgumentException("Rule and course catalog years must match");
        }
        var courseIds = catalog.courses().stream()
                .map(CatalogCourses.Course::id).collect(Collectors.toSet());
        requireCourse(rule.courseId(), catalog.catalogYear(), courseIds);
        validateExpression(rule.expression(), catalog.catalogYear(), courseIds);
    }

    private void validateExpression(RequirementExpression expression, String year, Set<String> courseIds) {
        switch (expression) {
            case RequirementExpression.AllOf group ->
                    group.children().forEach(child -> validateExpression(child, year, courseIds));
            case RequirementExpression.AnyOf group ->
                    group.children().forEach(child -> validateExpression(child, year, courseIds));
            case RequirementExpression.Course course -> requireCourse(course.courseId(), year, courseIds);
            case RequirementExpression.Unsupported ignored -> {
                // No course reference to check. The condition still needs manual review.
            }
        }
    }

    private void requireCourse(String id, String year, Set<String> courseIds) {
        if (!id.endsWith("-" + year)) {
            throw new IllegalArgumentException("Course ID does not match catalog year: " + id);
        }
        if (!courseIds.contains(id)) {
            throw new IllegalArgumentException("Course is missing from catalog: " + id);
        }
    }
}
