package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogCoursesFlowTest {
    private CatalogCourses sample() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/courses.json")) {
            return new ObjectMapper().readValue(input, CatalogCourses.class);
        }
    }

    @Test
    void importsBothCoursesWithSourceMetadata() throws Exception {
        var repository = mock(CatalogRepository.class);
        new CatalogImportService(new ObjectMapper(), repository).importCourses();
        var catalog = sample();
        for (var course : catalog.courses()) {
            verify(repository).saveCourse(catalog, course);
        }
        verifyNoMoreInteractions(repository);
    }

    @Test
    void courseImportRequiresExplicitOptIn() {
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(CatalogCourseImportRunner.class)
                .withBean(CatalogImportService.class, () -> mock(CatalogImportService.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(CatalogCourseImportRunner.class));
        runner.withPropertyValues("packplan.catalog.import-courses=false")
                .run(context -> assertThat(context).doesNotHaveBean(CatalogCourseImportRunner.class));
        runner.withPropertyValues("packplan.catalog.import-courses=true").run(context -> {
            context.getBean(CatalogCourseImportRunner.class)
                    .run(new org.springframework.boot.DefaultApplicationArguments());
            verify(context.getBean(CatalogImportService.class)).importCourses();
        });
    }

    @Test
    void persistsCourseByStableIdAndReadsAllMetadata() throws Exception {
        var driver = mock(Driver.class);
        var session = mock(Session.class);
        var transaction = mock(TransactionContext.class);
        var result = mock(Result.class);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.executeWrite(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(transaction);
        });
        when(transaction.run(anyString(), anyMap())).thenReturn(result);
        var catalog = sample();
        var course = catalog.courses().getFirst();
        var repository = new CatalogRepository(driver);
        repository.saveCourse(catalog, course);
        var properties = Map.<String, Object>of(
                "id", course.id(), "code", course.code(), "subject", course.subject(),
                "number", course.number(), "title", course.title(), "credits", course.credits(),
                "catalogYear", catalog.catalogYear(), "sourceUrl", catalog.sourceUrl(),
                "sourceSha256", catalog.sourceSha256(), "reviewStatus", "REVIEWED");
        verify(transaction).run(contains("MERGE (course:Course {id: $id})"), eq(properties));
        verify(result).consume();
        var record = mock(org.neo4j.driver.Record.class);
        properties.forEach((key, value) -> when(record.get(key)).thenReturn(Values.value(value)));
        when(session.run(anyString())).thenReturn(result);
        when(result.list(any(Function.class))).thenAnswer(invocation -> {
            Function<org.neo4j.driver.Record, Object> mapper = invocation.getArgument(0);
            return List.of(mapper.apply(record));
        });
        assertThat(repository.findCourses()).containsExactly(new CatalogRepository.CatalogCourse(
                course.id(), course.code(), course.subject(), course.number(), course.title(),
                course.credits(), catalog.catalogYear(), catalog.sourceUrl(), catalog.sourceSha256(),
                ReviewStatus.REVIEWED));
        verify(session, times(2)).close();
    }

    @Test
    void listsCoursesAndEmptyCatalogWithoutCaching() throws Exception {
        var repository = mock(CatalogRepository.class);
        var mvc = MockMvcBuilders.standaloneSetup(new CatalogController(repository)).build();
        when(repository.findCourses()).thenReturn(List.of());
        mvc.perform(get("/api/catalog/courses")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        var catalog = sample();
        var course = catalog.courses().getFirst();
        when(repository.findCourses()).thenReturn(List.of(new CatalogRepository.CatalogCourse(
                course.id(), course.code(), course.subject(), course.number(), course.title(),
                course.credits(), catalog.catalogYear(), catalog.sourceUrl(), catalog.sourceSha256(),
                course.reviewStatus())));
        mvc.perform(get("/api/catalog/courses"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].code").value("CSC 216"))
                .andExpect(jsonPath("$[0].credits").value(3))
                .andExpect(jsonPath("$[0].catalogYear").value("2026-2027"))
                .andExpect(jsonPath("$[0].sourceSha256").value(catalog.sourceSha256()))
                .andExpect(jsonPath("$[0].reviewStatus").value("REVIEWED"));
    }
}
