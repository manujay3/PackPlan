package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCoursesTest {
    @Test
    void rejectsIncompleteOrMalformedExplicitCourseProvenance() {
        assertThatThrownBy(() -> new CatalogCourses.Source("", "a".repeat(64), "2026-10-27T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogCourses.Source("https://example.com", "bad", "2026-10-27T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogCourses.Source("https://example.com", "a".repeat(64), "bad"))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
        assertThatThrownBy(() -> new CatalogCourses.Course("e-115-2026-2027", "E 115", "E", "115", "Example", 1,
                ReviewStatus.REVIEWED, new CatalogCourses.Source("https://example.com", "a".repeat(64), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retrievedAt");
    }

    @Test
    void readsReviewedCoursesFromJson() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/courses.json")) {
            assertThat(input).isNotNull();

            var catalog = new ObjectMapper().readValue(input, CatalogCourses.class);

            assertThat(catalog.catalogYear()).isEqualTo("2026-2027");
            assertThat(catalog.sourceSha256()).hasSize(64);
            assertThat(catalog.courses()).hasSize(8);
            assertThat(catalog.courses())
                    .extracting(CatalogCourses.Course::code)
                    .containsExactly("CSC 116", "CSC 216", "CSC 217", "E 115", "COS 100", "MA 121", "MA 131", "MA 141");
            assertThat(catalog.courses())
                    .extracting(CatalogCourses.Course::credits)
                    .containsExactly(3, 3, 1, 1, 2, 3, 3, 4);
            assertThat(catalog.courses().getFirst()).isEqualTo(new CatalogCourses.Course(
                    "csc-116-2026-2027", "CSC 116", "CSC", "116",
                    "Introduction to Computing - Java", 3, ReviewStatus.REVIEWED));
            assertThat(catalog.courses())
                    .allMatch(course -> course.reviewStatus() == ReviewStatus.REVIEWED);
            for (var course : catalog.courses().subList(3, 8)) {
                assertThat(course.source().sourceUrl()).isEqualTo(
                        "https://catalog.ncsu.edu/course-descriptions/" + course.subject().toLowerCase() + "/");
                assertThat(course.source().sourceSha256()).matches("[0-9a-f]{64}");
                assertThat(course.source().retrievedAt()).isNotBlank();
            }
        }
    }
}
