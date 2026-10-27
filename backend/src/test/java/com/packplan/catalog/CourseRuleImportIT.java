package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.PackPlanApplication;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit live check: updates bundled courses and all three sample rules in the configured database. */
@EnabledIfSystemProperty(named = "packplan.live-import", matches = "true")
class CourseRuleImportIT {
    @Test
    void importsTwiceAndPreservesEachRuleOnNormalStartup() throws Exception {
        CatalogCourses expectedCatalog;
        try (var input = getClass().getResourceAsStream("/catalog/courses.json")) {
            expectedCatalog = new ObjectMapper().readValue(input, CatalogCourses.class);
        }
        var expectedRules = new java.util.ArrayList<CourseRule>();
        for (var number : new String[] {"116", "216", "217"}) {
            try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-" + number + "-rule.json")) {
                expectedRules.add(new ObjectMapper().readValue(input, CourseRule.class));
            }
        }
        for (boolean importing : new boolean[] {true, true, false}) {
            try (var context = new SpringApplicationBuilder(PackPlanApplication.class)
                    .web(WebApplicationType.NONE)
                    .run("--packplan.catalog.import-programs=false",
                            "--packplan.catalog.import-courses=" + importing,
                            "--packplan.catalog.import-draft-rules=" + importing)) {
                if (!importing) {
                    assertThat(context.getBeansOfType(CatalogDraftRuleImportRunner.class)).isEmpty();
                    assertThat(context.getBeansOfType(CatalogCourseImportRunner.class)).isEmpty();
                }
                for (var expected : expectedRules) {
                    assertThat(context.getBean(CourseRuleRepository.class).findByCourseId(expected.courseId()))
                            .contains(expected);
                    assertThat(expected.reviewStatus()).isEqualTo(expected.courseId().startsWith("csc-116-")
                            ? ReviewStatus.NEEDS_REVIEW : ReviewStatus.REVIEWED);
                    var mvc = MockMvcBuilders.standaloneSetup(
                            context.getBean(CourseRuleController.class)).build();
                    mvc.perform(get("/api/catalog/courses/{courseId}/rule", expected.courseId()))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(content().json(new ObjectMapper().writeValueAsString(expected),
                                    org.springframework.test.json.JsonCompareMode.STRICT));
                    try (var session = context.getBean(Driver.class).session(SessionConfig.forDatabase("neo4j"))) {
                        assertThat(session.run("MATCH (r:CourseRule {id: $id}) RETURN count(r) AS count",
                                java.util.Map.of("id", expected.courseId())).single().get("count").asLong()).isEqualTo(1);
                        assertThat(session.run("""
                                MATCH (:Course {id: $id})-[link:HAS_RULE]->(:CourseRule {id: $id})
                                RETURN count(link) AS count
                                """, java.util.Map.of("id", expected.courseId())).single().get("count").asLong()).isEqualTo(1);
                    }
                }
                var courses = context.getBean(CatalogRepository.class).findCourses();
                for (var expected : expectedCatalog.courses()) {
                    var source = expectedCatalog.sourceFor(expected);
                    assertThat(courses).contains(new CatalogRepository.CatalogCourse(
                            expected.id(), expected.code(), expected.subject(), expected.number(), expected.title(),
                            expected.credits(), expectedCatalog.catalogYear(), source.sourceUrl(), source.sourceSha256(),
                            expected.reviewStatus(), source.retrievedAt()));
                    try (var session = context.getBean(Driver.class).session(SessionConfig.forDatabase("neo4j"))) {
                        assertThat(session.run("MATCH (c:Course {id: $id}) RETURN count(c) AS count",
                                java.util.Map.of("id", expected.id())).single().get("count").asLong()).isEqualTo(1);
                    }
                }
                MockMvcBuilders.standaloneSetup(context.getBean(CatalogController.class)).build()
                        .perform(get("/api/catalog/courses"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(content().json(new ObjectMapper().writeValueAsString(courses),
                                org.springframework.test.json.JsonCompareMode.STRICT));
            }
        }
    }
}
