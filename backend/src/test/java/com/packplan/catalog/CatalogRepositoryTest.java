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
