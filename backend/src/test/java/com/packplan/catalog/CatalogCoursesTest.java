package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCoursesTest {
    @Test
    void readsReviewedCoursesFromJson() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/courses.json")) {
            assertThat(input).isNotNull();

            var catalog = new ObjectMapper().readValue(input, CatalogCourses.class);

            assertThat(catalog.catalogYear()).isEqualTo("2026-2027");
            assertThat(catalog.sourceSha256()).hasSize(64);
            assertThat(catalog.courses()).hasSize(2);
            assertThat(catalog.courses())
                    .extracting(CatalogCourses.Course::code)
                    .containsExactly("CSC 216", "CSC 217");
            assertThat(catalog.courses())
                    .extracting(CatalogCourses.Course::credits)
                    .containsExactly(3, 1);
            assertThat(catalog.courses())
                    .allMatch(course -> course.reviewStatus() == ReviewStatus.REVIEWED);
        }
    }
}
