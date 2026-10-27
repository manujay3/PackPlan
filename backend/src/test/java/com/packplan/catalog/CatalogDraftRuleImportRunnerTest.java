package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogDraftRuleImportRunnerTest {
    private final CourseRuleImportService rules = mock(CourseRuleImportService.class);
    private final CatalogImportService courses = mock(CatalogImportService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogDraftRuleImportRunner.class, CatalogCourseImportRunner.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(CourseRuleImportService.class, () -> rules)
            .withBean(CatalogImportService.class, () -> courses);

    @Test
    void disabledUnlessDraftFlagIsExplicitlyTrue() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(CatalogDraftRuleImportRunner.class));
        contextRunner.withPropertyValues("packplan.catalog.import-draft-rules=false")
                .run(context -> assertThat(context).doesNotHaveBean(CatalogDraftRuleImportRunner.class));
        contextRunner.withPropertyValues("packplan.catalog.import-courses=true")
                .run(context -> assertThat(context).doesNotHaveBean(CatalogDraftRuleImportRunner.class));
        verifyNoInteractions(rules);
    }

    @Test
    void draftFlagLoadsBundledCatalogWithoutImplicitCourseImport() {
        contextRunner.withPropertyValues("packplan.catalog.import-draft-rules=true").run(context -> {
            assertThat(context).doesNotHaveBean(CatalogCourseImportRunner.class);
            context.getBean(CatalogDraftRuleImportRunner.class).run(new DefaultApplicationArguments());
            for (var number : new String[] {"116", "216", "217"}) {
                verify(rules).importRule(eq(new ClassPathResource("catalog/drafts/csc-" + number + "-rule.json")),
                    argThat(catalog -> catalog.catalogYear().equals("2026-2027")
                            && catalog.courses().stream().map(CatalogCourses.Course::code).toList()
                            .containsAll(java.util.List.of("CSC 116", "CSC 216", "CSC 217"))));
            }
            verifyNoMoreInteractions(rules);
            verifyNoInteractions(courses);
        });
    }

    @Test
    void combinedFlagsImportCoursesBeforeRules() {
        contextRunner.withPropertyValues("packplan.catalog.import-draft-rules=true",
                "packplan.catalog.import-courses=true").run(context -> {
            var runners = context.getBeanProvider(ApplicationRunner.class).orderedStream().toList();
            for (var runner : runners) runner.run(new DefaultApplicationArguments());
            var order = inOrder(courses, rules);
            order.verify(courses).importCourses();
            for (var number : new String[] {"116", "216", "217"}) {
                order.verify(rules).importRule(eq(new ClassPathResource("catalog/drafts/csc-" + number + "-rule.json")), any());
            }
            order.verifyNoMoreInteractions();
        });
    }

    @Test
    void importFailurePropagatesToStartup() {
        var failure = new IllegalStateException("Database unavailable");
        when(rules.importRule(any(), any())).thenThrow(failure);
        contextRunner.withPropertyValues("packplan.catalog.import-draft-rules=true").run(context ->
                assertThatThrownBy(() -> context.getBean(CatalogDraftRuleImportRunner.class)
                        .run(new DefaultApplicationArguments())).isSameAs(failure));
    }
}
