package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.PackPlanApplication;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit live check: updates bundled courses and the CSC 216 draft in the configured database. */
@EnabledIfSystemProperty(named = "packplan.live-import", matches = "true")
class CourseRuleImportIT {
    @Test
    void importsTwiceAndPreservesOneRuleOnNormalStartup() throws Exception {
        CourseRule expected;
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-216-rule.json")) {
            expected = new ObjectMapper().readValue(input, CourseRule.class);
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
                assertThat(context.getBean(CourseRuleRepository.class).findByCourseId(expected.courseId()))
                        .contains(expected);
                assertThat(expected.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
                var courses = context.getBean(CatalogRepository.class).findCourses();
                assertThat(courses).extracting(CatalogRepository.CatalogCourse::id)
                        .contains("csc-116-2026-2027", "csc-216-2026-2027", "csc-217-2026-2027");
                try (var session = context.getBean(Driver.class).session(SessionConfig.forDatabase("neo4j"))) {
                    assertThat(session.run("MATCH (r:CourseRule {id: $id}) RETURN count(r) AS count",
                            java.util.Map.of("id", expected.courseId())).single().get("count").asLong()).isEqualTo(1);
                    assertThat(session.run("""
                            MATCH (:Course {id: $id})-[link:HAS_RULE]->(:CourseRule {id: $id})
                            RETURN count(link) AS count
                            """, java.util.Map.of("id", expected.courseId())).single().get("count").asLong()).isEqualTo(1);
                }
            }
        }
    }
}
