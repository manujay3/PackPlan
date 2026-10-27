package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CourseRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CourseRuleControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean CourseRuleRepository repository;

    @Test
    void returnsStoredRuleWithProvenanceAndStructuredConditions() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-216-rule.json")) {
            json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var rule = mapper.readValue(json, CourseRule.class);
        when(repository.findByCourseId(rule.courseId())).thenReturn(Optional.of(rule));

        mvc.perform(get("/api/catalog/courses/{courseId}/rule", rule.courseId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(json, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(repository).findByCourseId(rule.courseId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingRuleReturnsUncached404InsteadOfAnEmptyPassingRule() throws Exception {
        // Also covers an unknown course: neither case implies no prerequisites.
        for (var courseId : new String[] {"csc-116-2026-2027", "unknown-2026-2027"}) {
            when(repository.findByCourseId(courseId)).thenReturn(Optional.empty());
            mvc.perform(get("/api/catalog/courses/{courseId}/rule", courseId))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(""));
            verify(repository).findByCourseId(courseId);
        }
        verifyNoMoreInteractions(repository);
    }
}
