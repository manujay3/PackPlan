package com.packplan.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.CourseRuleRepository;
import com.packplan.catalog.CatalogRepository;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;
import com.packplan.catalog.model.ReviewStatus;
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

import static com.packplan.eligibility.EnrollmentEvidence.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SemesterControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean CourseRuleRepository repository;
    @MockitoBean CatalogRepository catalog;

    private void samples() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/courses.json")) {
            var sample = mapper.readValue(input, com.packplan.catalog.model.CatalogCourses.class);
            when(catalog.findCourseCredits(anyList())).thenAnswer(invocation -> {
                List<String> ids = invocation.getArgument(0);
                return sample.courses().stream().filter(course -> ids.contains(course.id()))
                        .map(course -> new CatalogRepository.CourseCredits(course.id(), course.credits(), course.reviewStatus())).toList();
            });
        }
        for (var number : List.of("116", "216", "217")) {
            try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-" + number + "-rule.json")) {
                var rule = mapper.readValue(input, CourseRule.class);
                when(repository.findByCourseId(rule.courseId())).thenReturn(Optional.of(rule));
            }
        }
    }

    private SemesterEvidence evidence(String... ids) {
        return new SemesterEvidence(List.of(new Attempt("csc-116-2026-2027", Grade.C, true)), List.of(ids), 15);
    }

    private org.springframework.test.web.servlet.ResultActions check(SemesterEvidence evidence) throws Exception {
        return mvc.perform(post("/api/semesters/eligibility").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(evidence)));
    }

    @Test
    void pairedCoursesSatisfyTogetherAndPreserveRequestOrderWithoutWrites() throws Exception {
        samples();
        check(evidence("csc-217-2026-2027", "csc-216-2026-2027"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("SATISFIED"))
                .andExpect(jsonPath("$.courses[0].courseId").value("csc-217-2026-2027"))
                .andExpect(jsonPath("$.courses[1].courseId").value("csc-216-2026-2027"))
                .andExpect(jsonPath("$.courses[0].status").value("SATISFIED"))
                .andExpect(jsonPath("$.courses[1].condition.children[1].status").value("SATISFIED"));
        verify(repository).findByCourseId("csc-216-2026-2027");
        verify(repository).findByCourseId("csc-217-2026-2027");
        verifyNoMoreInteractions(repository);
        verify(catalog).findCourseCredits(List.of("csc-217-2026-2027", "csc-216-2026-2027"));
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void removingTheLabBlocksTheLecture() throws Exception {
        samples();
        check(evidence("csc-216-2026-2027")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.courses[0].condition.children[1].status").value("BLOCKED"));
    }

    @Test
    void missingRulesAndUnsupportedChecksCannotProduceASatisfiedSemester() throws Exception {
        samples();
        check(evidence("csc-216-2026-2027", "csc-217-2026-2027", "ma-141-2026-2027"))
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.courses[2].condition").isEmpty());
        check(new SemesterEvidence(List.of(), List.of("csc-116-2026-2027", "e-115-2026-2027", "ma-141-2026-2027"), 15))
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.courses[0].condition.children[2].status").value("MANUAL_REVIEW"));
    }

    @Test
    void sameTermPrerequisitesAreNotPromotedToEarnedCreditAndBlockersDominateReview() throws Exception {
        samples();
        check(new SemesterEvidence(List.of(), List.of("csc-116-2026-2027", "csc-216-2026-2027",
                "csc-217-2026-2027", "e-115-2026-2027", "ma-141-2026-2027"), 15))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.courses[0].status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.courses[1].condition.children[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.courses[2].status").value("BLOCKED"));
    }

    @Test
    void aCourseCannotCountItselfAsItsOwnConcurrentEvidence() throws Exception {
        var id = "self-2026-2027";
        when(repository.findByCourseId(id)).thenReturn(Optional.of(new CourseRule(id, "2026-2027",
                "https://example.com", "a".repeat(64), "2026-10-27T00:00:00Z", "Synthetic self requirement",
                ReviewStatus.REVIEWED, new RequirementExpression.Course(id,
                RequirementExpression.Timing.CONCURRENT, RequirementExpression.MinimumGrade.NOT_SPECIFIED))));
        check(evidence(id)).andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void databaseFailureIsNotReportedAsAnUnknownOrSatisfiedSemester() {
        when(repository.findByCourseId(anyString())).thenThrow(new IllegalStateException("Database unavailable"));
        assertThatThrownBy(() -> check(evidence("csc-216-2026-2027")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"completedCourses\":[],\"proposedCourses\":[]}",
            "{\"completedCourses\":[],\"proposedCourses\":[\"a\",\"a\"]}",
            "{\"completedCourses\":[],\"proposedCourses\":[\" \"]}",
            "{\"completedCourses\":[],\"proposedCourses\":[null]}",
            "{\"completedCourses\":null,\"proposedCourses\":[\"a\"]}"})
    void invalidSemesterEvidenceIsRejectedBeforeDatabaseAccess(String json) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(json);
        body.put("creditCap", 15);
        mvc.perform(post("/api/semesters/eligibility").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, catalog);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"5,SATISFIED", "4,SATISFIED", "3,BLOCKED"})
    void checksReviewedCatalogCreditsAtAndAroundTheCap(int cap, String expected) throws Exception {
        samples();
        var input = evidence("csc-216-2026-2027", "csc-217-2026-2027");
        check(new SemesterEvidence(input.completedCourses(), input.proposedCourses(), cap))
                .andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.creditLoad.status").value(expected))
                .andExpect(jsonPath("$.creditLoad.totalCredits").value(4))
                .andExpect(jsonPath("$.creditLoad.knownCredits").value(4))
                .andExpect(jsonPath("$.creditLoad.creditCap").value(cap))
                .andExpect(jsonPath("$.courses[0].status").value("SATISFIED"));
    }

    @Test
    void missingOrUnreviewedCreditsStayUnknownAndKnownOverloadsStillBlock() throws Exception {
        samples();
        for (var uncertain : List.of(
                new CatalogRepository.CourseCredits("csc-217-2026-2027", null, ReviewStatus.REVIEWED),
                new CatalogRepository.CourseCredits("csc-217-2026-2027", 1, ReviewStatus.NEEDS_REVIEW),
                new CatalogRepository.CourseCredits("csc-217-2026-2027", -1, ReviewStatus.REVIEWED))) {
            when(catalog.findCourseCredits(anyList())).thenReturn(List.of(
                    new CatalogRepository.CourseCredits("csc-216-2026-2027", 3, ReviewStatus.REVIEWED), uncertain));
            var input = evidence("csc-216-2026-2027", "csc-217-2026-2027");
            check(input).andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                    .andExpect(jsonPath("$.creditLoad.totalCredits").isEmpty())
                    .andExpect(jsonPath("$.creditLoad.knownCredits").value(3))
                    .andExpect(jsonPath("$.creditLoad.unresolvedCourses[0]").value("csc-217-2026-2027"));
            check(new SemesterEvidence(input.completedCourses(), input.proposedCourses(), 2))
                    .andExpect(jsonPath("$.status").value("BLOCKED"))
                    .andExpect(jsonPath("$.creditLoad.totalCredits").isEmpty());
        }
        when(catalog.findCourseCredits(anyList())).thenReturn(List.of());
        check(evidence("csc-216-2026-2027", "csc-217-2026-2027"))
                .andExpect(jsonPath("$.creditLoad.status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.creditLoad.knownCredits").value(0))
                .andExpect(jsonPath("$.creditLoad.totalCredits").isEmpty());
    }

    @Test
    void duplicateMetadataIsNotDoubleCountedAndTotalsDoNotOverflowIntegers() throws Exception {
        samples();
        var large = new CatalogRepository.CourseCredits("csc-216-2026-2027", Integer.MAX_VALUE, ReviewStatus.REVIEWED);
        when(catalog.findCourseCredits(anyList())).thenReturn(List.of(large, large));
        check(evidence("csc-216-2026-2027"))
                .andExpect(jsonPath("$.creditLoad.totalCredits").isEmpty());
        when(catalog.findCourseCredits(anyList())).thenReturn(List.of(large,
                new CatalogRepository.CourseCredits("csc-217-2026-2027", Integer.MAX_VALUE, ReviewStatus.REVIEWED)));
        check(evidence("csc-216-2026-2027", "csc-217-2026-2027"))
                .andExpect(jsonPath("$.creditLoad.totalCredits").value(4294967294L))
                .andExpect(jsonPath("$.creditLoad.status").value("BLOCKED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "1.5", "\"4\"", "true", "2147483648"})
    void capMustBeAPositiveJsonInteger(String cap) throws Exception {
        mvc.perform(post("/api/semesters/eligibility").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedCourses\":[],\"proposedCourses\":[\"a\"],\"creditCap\":" + cap + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void missingCapIsRejectedAndCreditLookupFailureDoesNotPass() throws Exception {
        mvc.perform(post("/api/semesters/eligibility").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedCourses\":[],\"proposedCourses\":[\"a\"]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, catalog);
        samples();
        when(catalog.findCourseCredits(anyList())).thenThrow(new IllegalStateException("Database unavailable"));
        assertThatThrownBy(() -> check(evidence("csc-216-2026-2027", "csc-217-2026-2027")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
