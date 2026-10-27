package com.packplan.catalog;

import com.packplan.catalog.model.CatalogPrograms;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CatalogRepositoryTest {
    @Test
    void readsSelectedCreditValuesWithoutInventingMissingMetadata() {
        var driver = mock(Driver.class);
        var session = mock(Session.class);
        var result = mock(Result.class);
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.run(anyString(), anyMap())).thenReturn(result);
        var complete = mock(org.neo4j.driver.Record.class);
        var incomplete = mock(org.neo4j.driver.Record.class);
        when(complete.get("id")).thenReturn(org.neo4j.driver.Values.value("ma-141-2026-2027"));
        when(complete.get("credits")).thenReturn(org.neo4j.driver.Values.value(4));
        when(complete.get("reviewStatus")).thenReturn(org.neo4j.driver.Values.value("REVIEWED"));
        when(incomplete.get("id")).thenReturn(org.neo4j.driver.Values.value("missing-2026-2027"));
        when(incomplete.get("credits")).thenReturn(org.neo4j.driver.Values.NULL);
        when(incomplete.get("reviewStatus")).thenReturn(org.neo4j.driver.Values.NULL);
        when(result.list(any(java.util.function.Function.class))).thenAnswer(invocation -> {
            java.util.function.Function<org.neo4j.driver.Record, Object> mapper = invocation.getArgument(0);
            return java.util.List.of(mapper.apply(complete), mapper.apply(incomplete));
        });
        var ids = java.util.List.of("ma-141-2026-2027", "missing-2026-2027");
        assertThat(new CatalogRepository(driver).findCourseCredits(ids)).containsExactly(
                new CatalogRepository.CourseCredits(ids.getFirst(), 4, ReviewStatus.REVIEWED),
                new CatalogRepository.CourseCredits(ids.getLast(), null, null));
        verify(session).run(org.mockito.ArgumentMatchers.contains("course.id IN $ids"), eq(Map.of("ids", ids)));
        verify(session).close();
        verifyNoMoreInteractions(session);
    }
    
    @Test
    void savesAReviewedProgramWithExpectedParameters() {
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

        var repository = new CatalogRepository(driver);
        var program = new CatalogPrograms.Program(
                "computer-science-bs-2026-2027",
                "Computer Science",
                "BS",
                121,
                "https://catalog.ncsu.edu/undergraduate/engineering/computer-science/computer-science-bs/",
                ReviewStatus.REVIEWED
        );

        repository.saveProgram("2026-2027", program);

        @SuppressWarnings("unchecked")
        var parameters = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(transaction).run(anyString(), parameters.capture());
        assertThat(parameters.getValue()).containsEntry("id", program.id());
        assertThat(parameters.getValue()).containsEntry("name", "Computer Science");
        assertThat(parameters.getValue()).containsEntry("degree", "BS");
        assertThat(parameters.getValue()).containsEntry("catalogYear", "2026-2027");
        assertThat(parameters.getValue()).containsEntry("totalCredits", 121);
        assertThat(parameters.getValue()).containsEntry("reviewStatus", "REVIEWED");
        verify(result).consume();
        verify(session).close();
    }
}
