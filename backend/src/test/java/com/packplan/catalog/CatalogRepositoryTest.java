package com.packplan.catalog;

import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.CatalogPrograms;
import com.packplan.catalog.model.CatalogRequirements;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void savesAReviewedCourseWithExpectedParameters() {
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
        var course = new CatalogCourses.Course(
                "csc-216-2026-2027",
                "CSC 216",
                "CSC",
                "216",
                "Software Development Fundamentals",
                3,
                ReviewStatus.REVIEWED
        );

        repository.saveCourse(
                "2026-2027",
                "https://catalog.ncsu.edu/course-descriptions/csc/",
                "d3a77e2e7f150f54d70f2865e657a74884460f7385c147029d71ca91ae5d7455",
                course
        );

        @SuppressWarnings("unchecked")
        var parameters = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(transaction).run(anyString(), parameters.capture());
        assertThat(parameters.getValue()).containsEntry("id", course.id());
        assertThat(parameters.getValue()).containsEntry("code", "CSC 216");
        assertThat(parameters.getValue()).containsEntry("credits", 3);
        assertThat(parameters.getValue()).containsEntry("catalogYear", "2026-2027");
        assertThat(parameters.getValue()).containsEntry("sourceSha256",
                "d3a77e2e7f150f54d70f2865e657a74884460f7385c147029d71ca91ae5d7455");
        assertThat(parameters.getValue()).containsEntry("reviewStatus", "REVIEWED");
        verify(result).consume();
        verify(session).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesAReviewedAllOfRequirementWithExpectedCourseRules() {
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
        var requirement = softwareFundamentalsRequirement(CatalogRequirements.RuleType.ALL_OF);

        repository.saveRequirement(
                "2026-2027",
                "computer-science-bs-2026-2027",
                "https://catalog.ncsu.edu/undergraduate/engineering/computer-science/computer-science-bs/",
                "ecbcce1c3dce6811c57431e9e86eec7f3131f32f76f59d3dc3b32b832e4da0f9",
                requirement
        );

        @SuppressWarnings("unchecked")
        var parameters = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(transaction).run(anyString(), parameters.capture());
        assertThat(parameters.getValue()).containsEntry("programId", "computer-science-bs-2026-2027");
        assertThat(parameters.getValue()).containsEntry("requirementId", requirement.id());
        assertThat(parameters.getValue()).containsEntry("requiredCredits", 4);
        assertThat(parameters.getValue()).containsEntry("ruleType", "ALL_OF");
        assertThat((List<Map<String, String>>) parameters.getValue().get("courses"))
                .containsExactly(
                        Map.of("courseId", "csc-216-2026-2027", "minimumGrade", "C"),
                        Map.of("courseId", "csc-217-2026-2027", "minimumGrade", "C")
                );
        verify(result).consume();
        verify(session).close();
    }

    @Test
    void rejectsUnsupportedRuleBeforeOpeningADatabaseSession() {
        var driver = mock(Driver.class);
        var repository = new CatalogRepository(driver);
        var requirement = softwareFundamentalsRequirement(CatalogRequirements.RuleType.ANY_OF);

        assertThatThrownBy(() -> repository.saveRequirement(
                "2026-2027",
                "computer-science-bs-2026-2027",
                "https://catalog.ncsu.edu/undergraduate/engineering/computer-science/computer-science-bs/",
                "ecbcce1c3dce6811c57431e9e86eec7f3131f32f76f59d3dc3b32b832e4da0f9",
                requirement
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only an ALL_OF rule containing courses is currently supported");

        verifyNoInteractions(driver);
    }

    private CatalogRequirements.Requirement softwareFundamentalsRequirement(
            CatalogRequirements.RuleType rootType
    ) {
        var rule = new CatalogRequirements.Rule(
                "cs-software-fundamentals-2026-2027-rule",
                rootType,
                List.of(
                        new CatalogRequirements.Rule(
                                null,
                                CatalogRequirements.RuleType.COURSE,
                                null,
                                "csc-216-2026-2027",
                                "C"
                        ),
                        new CatalogRequirements.Rule(
                                null,
                                CatalogRequirements.RuleType.COURSE,
                                null,
                                "csc-217-2026-2027",
                                "C"
                        )
                ),
                null,
                null
        );
        return new CatalogRequirements.Requirement(
                "cs-software-fundamentals-2026-2027",
                "Software Development Fundamentals and Lab",
                "CSC 216 & CSC 217 (4 credits). A grade of C or higher is required.",
                4,
                ReviewStatus.REVIEWED,
                true,
                rule
        );
    }
}
