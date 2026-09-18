package com.packplan;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class CatalogRepository {
    private static final String SAVE_PROGRAM = """
            MERGE (program:Program {id: $id})
            SET program.name = $name,
                program.degree = $degree,
                program.catalogYear = $catalogYear,
                program.totalCredits = $totalCredits,
                program.sourceUrl = $sourceUrl,
                program.reviewStatus = $reviewStatus
            """;

    private final Driver driver;

    public CatalogRepository(Driver driver) {
        this.driver = driver;
    }

    public void saveProgram(String catalogYear, CatalogPrograms.Program program) {
        var parameters = Map.<String, Object>of(
                "id", program.id(),
                "name", program.name(),
                "degree", program.degree(),
                "catalogYear", catalogYear,
                "totalCredits", program.totalCredits(),
                "sourceUrl", program.sourceUrl(),
                "reviewStatus", program.reviewStatus().name()
        );

        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.executeWrite(transaction -> {
                transaction.run(SAVE_PROGRAM, parameters).consume();
                return null;
            });
        }
    }
}
