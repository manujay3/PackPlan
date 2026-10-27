package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class CourseRuleLoaderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CourseRuleLoader loader = new CourseRuleLoader(mapper);
    private final ClassPathResource draft = new ClassPathResource("catalog/drafts/csc-216-rule.json");

    private CatalogCourses catalog() throws Exception {
        try (var input = new ClassPathResource("catalog/courses.json").getInputStream()) {
            return mapper.readValue(input, CatalogCourses.class);
        }
    }

    @Test
    void loadsDraftWithoutPromotingReviewStatus() throws Exception {
        var rule = loader.load(draft, catalog());
        assertThat(rule.courseId()).isEqualTo("csc-216-2026-2027");
        assertThat(rule.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
    }

    @Test
    void rejectsDraftWhenReferencedCourseIsMissing() throws Exception {
        var complete = catalog();
        var incomplete = new CatalogCourses(complete.catalogYear(), complete.sourceUrl(),
                complete.sourceSha256(), complete.courses().stream()
                        .filter(course -> !course.code().equals("CSC 116")).toList());
        assertThatThrownBy(() -> loader.load(draft, incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("csc-116-2026-2027");
    }

    @Test
    void reportsUnreadableAndMalformedResources() throws Exception {
        var catalog = catalog();
        assertThatThrownBy(() -> loader.load(new ClassPathResource("catalog/missing-rule.json"), catalog))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining("missing-rule.json");
        var malformed = new ByteArrayResource("{invalid".getBytes(StandardCharsets.UTF_8), "malformed rule");
        assertThatThrownBy(() -> loader.load(malformed, catalog))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining("malformed rule");
    }

    @ParameterizedTest
    @ValueSource(strings = {"e-115-2026-2027", "cos-100-2026-2027", "ma-121-2026-2027", "ma-131-2026-2027", "ma-141-2026-2027"})
    void csc116RejectsEachMissingAlternativeEvenWhenOthersExist(String missingId) throws Exception {
        var complete = catalog();
        var incomplete = new CatalogCourses(complete.catalogYear(), complete.sourceUrl(), complete.sourceSha256(),
                complete.courses().stream().filter(course -> !course.id().equals(missingId)).toList());
        assertThatThrownBy(() -> loader.load(new ClassPathResource("catalog/drafts/csc-116-rule.json"), incomplete))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(missingId);
    }
}
