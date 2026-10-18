package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.*;

import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CourseRuleRepositoryTest {
    private final Driver driver = mock(Driver.class);
    private final Session session = mock(Session.class);
    private final TransactionContext transaction = mock(TransactionContext.class);
    private final Result result = mock(Result.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CourseRuleRepository repository = new CourseRuleRepository(driver, mapper);

    CourseRuleRepositoryTest() {
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.executeWrite(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(transaction);
        });
        when(transaction.run(anyString(), anyMap())).thenReturn(result);
        when(session.run(anyString(), anyMap())).thenReturn(result);
    }

    private CourseRule draft() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/drafts/csc-216-rule.json")) {
            return mapper.readValue(input, CourseRule.class);
        }
    }

    @Test
    void preservesExpressionAndProvenanceAcrossSaveAndReadMapping() throws Exception {
        var draft = draft();
        when(result.hasNext()).thenReturn(true);
        repository.save(draft);
        @SuppressWarnings("unchecked")
        var parameters = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        var query = ArgumentCaptor.forClass(String.class);
        verify(transaction).run(query.capture(), parameters.capture());
        assertThat(query.getValue()).contains("MATCH (course:Course", "MERGE (rule:CourseRule {id: $courseId})",
                "MERGE (course)-[:HAS_RULE]->(rule)");
        var stored = parameters.getValue();
        assertThat(mapper.readValue((String) stored.get("expressionJson"), RequirementExpression.class))
                .isEqualTo(draft.expression());
        var record = mock(org.neo4j.driver.Record.class);
        stored.forEach((key, value) -> when(record.get(key)).thenReturn(Values.value(value)));
        when(result.single()).thenReturn(record);
        assertThat(repository.findByCourseId(draft.courseId())).contains(draft);
        verify(session).run(contains("HAS_RULE"), eq(Map.of("courseId", draft.courseId())));
        verify(result).consume();
        verify(session, times(2)).close();
    }

    @Test
    void missingOwnerFailsSaveAndClosesSession() throws Exception {
        var draft = draft();
        assertThatThrownBy(() -> repository.save(draft))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Owning course missing");
        verify(session).close();
    }

    @Test
    void absentRuleReturnsEmpty() {
        assertThat(repository.findByCourseId("csc-216-2026-2027")).isEmpty();
        verify(session).close();
    }

    @Test
    void corruptStoredExpressionFailsInsteadOfReturningAnEmptyRule() throws Exception {
        var draft = draft();
        var record = mock(org.neo4j.driver.Record.class);
        var properties = Map.of("courseId", draft.courseId(), "catalogYear", draft.catalogYear(),
                "sourceUrl", draft.sourceUrl(), "sourceSha256", draft.sourceSha256(),
                "retrievedAt", draft.retrievedAt(), "originalText", draft.originalText(),
                "reviewStatus", draft.reviewStatus().name(), "expressionJson", "{invalid");
        properties.forEach((key, value) -> when(record.get(key)).thenReturn(Values.value(value)));
        when(result.hasNext()).thenReturn(true);
        when(result.single()).thenReturn(record);
        assertThatThrownBy(() -> repository.findByCourseId(draft.courseId()))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining(draft.courseId());
        verify(session).close();
    }
}
