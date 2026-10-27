package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseRuleImportServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CourseRuleRepository repository = mock(CourseRuleRepository.class);
    private final CourseRuleImportService service = new CourseRuleImportService(mapper, repository);
    private final ClassPathResource source = new ClassPathResource("catalog/drafts/csc-216-rule.json");

    private CatalogCourses catalog() throws Exception {
        try (var input = new ClassPathResource("catalog/courses.json").getInputStream()) {
            return mapper.readValue(input, CatalogCourses.class);
        }
    }

    @Test
    void writesOnlyWhenExplicitlyInvokedAndPreservesReviewedStatus() throws Exception {
        verifyNoInteractions(repository);
        var rule = service.importRule(source, catalog());
        assertThat(rule.courseId()).isEqualTo("csc-216-2026-2027");
        assertThat(rule.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
        verify(repository).save(rule);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingReferencePreventsAnyWrite() throws Exception {
        var complete = catalog();
        var incomplete = new CatalogCourses(complete.catalogYear(), complete.sourceUrl(),
                complete.sourceSha256(), complete.courses().stream()
                        .filter(course -> !course.code().equals("CSC 116")).toList());
        assertThatThrownBy(() -> service.importRule(source, incomplete))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("csc-116-2026-2027");
        verifyNoInteractions(repository);
    }

    @Test
    void malformedJsonPreventsAnyWrite() throws Exception {
        var catalog = catalog();
        var malformed = new ByteArrayResource("{invalid".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.importRule(malformed, catalog))
                .isInstanceOf(UncheckedIOException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void saveFailureIsReportedToCaller() throws Exception {
        var catalog = catalog();
        var failure = new IllegalStateException("Database unavailable");
        doThrow(failure).when(repository).save(any());
        assertThatThrownBy(() -> service.importRule(source, catalog)).isSameAs(failure);
    }
}
