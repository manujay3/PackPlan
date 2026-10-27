package com.packplan.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.CourseRuleRepository;
import com.packplan.catalog.model.CourseRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.packplan.eligibility.EnrollmentEvidence.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EligibilityControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean CourseRuleRepository repository;

    private CourseRule stored(String number) throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-" + number + "-rule.json")) {
            var rule = mapper.readValue(input, CourseRule.class);
            when(repository.findByCourseId(rule.courseId())).thenReturn(Optional.of(rule));
            return rule;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"216", "217"})
    void pairedLectureAndLabCanSatisfyTheirStoredRulesInTheSameTerm(String number) throws Exception {
        var rule = stored(number);
        var other = number.equals("216") ? "217" : "216";
        var evidence = new EnrollmentEvidence(List.of(new Attempt("csc-116-2026-2027", Grade.C, true)),
                Set.of("csc-" + other + "-2026-2027"));
        mvc.perform(post("/api/catalog/courses/{id}/eligibility", rule.courseId())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(evidence)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.courseId").value(rule.courseId()))
                .andExpect(jsonPath("$.status").value("SATISFIED"))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.condition.type").value("allOf"))
                .andExpect(jsonPath("$.condition.children[0].status").value("SATISFIED"))
                .andExpect(jsonPath("$.condition.children[1].status").value("SATISFIED"));
        verify(repository).findByCourseId(rule.courseId());
        verifyNoMoreInteractions(repository);
        mvc.perform(get("/api/status")).andExpect(jsonPath("$.catalogReady").value(false));
    }

    @Test
    void currentEnrollmentInStrictPrerequisiteDoesNotCountAsCompleted() throws Exception {
        var rule = stored("216");
        mvc.perform(post("/api/catalog/courses/{id}/eligibility", rule.courseId())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"completedCourses":[],"concurrentCourses":["csc-116-2026-2027","csc-217-2026-2027"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.condition.children[0].status").value("BLOCKED"));
    }

    @Test
    void seatReservationsRemainVisibleEvenWhenAllCourseConditionsAreSatisfied() throws Exception {
        var rule = stored("116");
        mvc.perform(post("/api/catalog/courses/{id}/eligibility", rule.courseId())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"completedCourses":[],"concurrentCourses":["e-115-2026-2027","ma-141-2026-2027"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.reviewStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.condition.children[2].type").value("unsupported"))
                .andExpect(jsonPath("$.condition.children[2].status").value("MANUAL_REVIEW"));
    }

    @Test
    void missingStoredRuleProducesExplicitUncachedManualReview() throws Exception {
        when(repository.findByCourseId("ma-141-2026-2027")).thenReturn(Optional.empty());
        mvc.perform(post("/api/catalog/courses/ma-141-2026-2027/eligibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedCourses\":[],\"concurrentCourses\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.condition").isEmpty())
                .andExpect(jsonPath("$.reviewStatus").isEmpty());
        verify(repository).findByCourseId("ma-141-2026-2027");
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"completedCourses\":[],\"concurrentCourses\":null}",
            "{\"completedCourses\":[null],\"concurrentCourses\":[]}",
            "{\"completedCourses\":[],\"concurrentCourses\":[\"\"]}",
            "{\"completedCourses\":[{\"courseId\":\"csc-116-2026-2027\",\"grade\":0,\"creditEarned\":true}],\"concurrentCourses\":[]}",
            "{\"completedCourses\":[{\"courseId\":\"csc-116-2026-2027\",\"grade\":\"A\"}],\"concurrentCourses\":[]}",
            "{\"completedCourses\":[{\"courseId\":\"csc-116-2026-2027\",\"grade\":\"F\",\"creditEarned\":true}],\"concurrentCourses\":[]}",
            "{\"completedCourses\":[{\"courseId\":\"csc-116-2026-2027\",\"grade\":\"not-a-grade\",\"creditEarned\":true}],\"concurrentCourses\":[]}"
    })
    void rejectsInvalidEvidenceBeforeReadingTheDatabase(String json) throws Exception {
        mvc.perform(post("/api/catalog/courses/csc-216-2026-2027/eligibility")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
}
