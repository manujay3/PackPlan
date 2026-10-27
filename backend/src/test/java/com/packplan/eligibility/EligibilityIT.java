package com.packplan.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.PackPlanApplication;
import com.packplan.catalog.CatalogDraftRuleImportRunner;
import com.packplan.catalog.CatalogCourseImportRunner;
import com.packplan.catalog.CatalogImportRunner;
import com.packplan.catalog.CourseRuleRepository;
import com.packplan.catalog.model.CourseRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static com.packplan.eligibility.EnrollmentEvidence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in read-only check; requires the current sample rules to have been imported. */
@EnabledIfSystemProperty(named = "packplan.live-eligibility", matches = "true")
class EligibilityIT {
    @Test
    void evaluatesPersistedRulesWithImportsDisabled() throws Exception {
        try (var context = new SpringApplicationBuilder(PackPlanApplication.class)
                .web(WebApplicationType.NONE)
                .run("--packplan.catalog.import-programs=false", "--packplan.catalog.import-courses=false",
                        "--packplan.catalog.import-draft-rules=false")) {
            assertThat(context.getBeansOfType(CatalogImportRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(CatalogCourseImportRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(CatalogDraftRuleImportRunner.class)).isEmpty();
            var repository = context.getBean(CourseRuleRepository.class);
            var mapper = context.getBean(ObjectMapper.class);
            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(EligibilityController.class)).build();
            for (var number : new String[] {"116", "216", "217"}) {
                CourseRule expected;
                try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-" + number + "-rule.json")) {
                    expected = mapper.readValue(input, CourseRule.class);
                }
                assertThat(repository.findByCourseId(expected.courseId())).contains(expected);
                var evidence = number.equals("116")
                        ? new EnrollmentEvidence(List.of(), Set.of("e-115-2026-2027", "ma-141-2026-2027"))
                        : new EnrollmentEvidence(List.of(new Attempt("csc-116-2026-2027", Grade.C, true)),
                                Set.of("csc-" + (number.equals("216") ? "217" : "216") + "-2026-2027"));
                mvc.perform(post("/api/catalog/courses/{id}/eligibility", expected.courseId())
                                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(evidence)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.status").value(number.equals("116") ? "MANUAL_REVIEW" : "SATISFIED"));
                assertThat(repository.findByCourseId(expected.courseId())).contains(expected);
            }
        }
    }
}
