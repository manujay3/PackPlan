package com.packplan.catalog;

import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.CatalogPrograms;
import com.packplan.catalog.model.CatalogRequirements;
import com.packplan.catalog.model.ReviewStatus;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
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
    private static final String SAVE_COURSE = """
            MERGE (course:Course {id: $id})
            SET course.code = $code,
                course.subject = $subject,
                course.number = $number,
                course.title = $title,
                course.credits = $credits,
                course.catalogYear = $catalogYear,
                course.sourceUrl = $sourceUrl,
                course.sourceSha256 = $sourceSha256,
                course.reviewStatus = $reviewStatus
            """;
    private static final String SAVE_REQUIREMENT = """
            MATCH (program:Program {id: $programId})
            MERGE (requirement:Requirement {id: $requirementId})
            SET requirement.name = $name,
                requirement.originalText = $originalText,
                requirement.requiredCredits = $requiredCredits,
                requirement.catalogYear = $catalogYear,
                requirement.sourceUrl = $sourceUrl,
                requirement.sourceSha256 = $sourceSha256,
                requirement.reviewStatus = $reviewStatus,
                requirement.evaluable = $evaluable
            MERGE (program)-[:HAS_REQUIREMENT]->(requirement)
            MERGE (rule:RuleGroup {id: $ruleId})
            SET rule.type = $ruleType
            MERGE (requirement)-[:HAS_RULE]->(rule)
            WITH rule
            UNWIND $courses AS item
            MATCH (course:Course {id: item.courseId})
            MERGE (rule)-[included:INCLUDES]->(course)
            SET included.minimumGrade = item.minimumGrade
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

    public void saveCourse(String catalogYear, String sourceUrl, String sourceSha256, CatalogCourses.Course course) {
        var parameters = Map.<String, Object>of(
                "id", course.id(),
                "code", course.code(),
                "subject", course.subject(),
                "number", course.number(),
                "title", course.title(),
                "credits", course.credits(),
                "catalogYear", catalogYear,
                "sourceUrl", sourceUrl,
                "sourceSha256", sourceSha256,
                "reviewStatus", course.reviewStatus().name()
        );

        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.executeWrite(transaction -> {
                transaction.run(SAVE_COURSE, parameters).consume();
                return null;
            });
        }
    }

    public void saveRequirement(
            String catalogYear,
            String programId,
            String sourceUrl,
            String sourceSha256,
            CatalogRequirements.Requirement requirement
    ) {
        var rule = requirement.rule();
        if (rule.type() != CatalogRequirements.RuleType.ALL_OF
                || rule.children() == null
                || rule.children().isEmpty()
                || rule.children().stream().anyMatch(child ->
                        child.type() != CatalogRequirements.RuleType.COURSE)) {
            throw new IllegalArgumentException("Only an ALL_OF rule containing courses is currently supported");
        }

        var courses = rule.children().stream().map(child -> Map.of(
                "courseId", child.courseId(),
                "minimumGrade", child.minimumGrade()
        )).toList();

        var parameters = new HashMap<String, Object>();
        parameters.put("programId", programId);
        parameters.put("requirementId", requirement.id());
        parameters.put("name", requirement.name());
        parameters.put("originalText", requirement.originalText());
        parameters.put("requiredCredits", requirement.requiredCredits());
        parameters.put("catalogYear", catalogYear);
        parameters.put("sourceUrl", sourceUrl);
        parameters.put("sourceSha256", sourceSha256);
        parameters.put("reviewStatus", requirement.reviewStatus().name());
        parameters.put("evaluable", requirement.evaluable());
        parameters.put("ruleId", rule.id());
        parameters.put("ruleType", rule.type().name());
        parameters.put("courses", courses);

        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.executeWrite(transaction -> {
                transaction.run(SAVE_REQUIREMENT, parameters).consume();
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
