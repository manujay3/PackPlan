package com.packplan.catalog;

import com.packplan.catalog.model.CatalogPrograms;
import com.packplan.catalog.model.ReviewStatus;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    private static final String FIND_PROGRAMS = """
            MATCH (program:Program)
            RETURN program.id AS id,
                   program.name AS name,
                   program.degree AS degree,
                   program.catalogYear AS catalogYear,
                   program.totalCredits AS totalCredits,
                   program.sourceUrl AS sourceUrl,
                   program.reviewStatus AS reviewStatus
            ORDER BY program.id
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

    public List<CatalogProgram> findPrograms() {
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            return session.run(FIND_PROGRAMS).list(record -> new CatalogProgram(
                    record.get("id").asString(),
                    record.get("name").asString(),
                    record.get("degree").asString(),
                    record.get("catalogYear").asString(),
                    record.get("totalCredits").asInt(),
                    record.get("sourceUrl").asString(),
                    ReviewStatus.valueOf(record.get("reviewStatus").asString())
            ));
        }
    }

    public record CatalogProgram(
            String id,
            String name,
            String degree,
            String catalogYear,
            int totalCredits,
            String sourceUrl,
            ReviewStatus reviewStatus
    ) {}
}
