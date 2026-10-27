package com.packplan.catalog;

import com.packplan.catalog.model.CatalogPrograms;
import com.packplan.catalog.model.CatalogCourses;
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
                course.retrievedAt = $retrievedAt,
                course.reviewStatus = $reviewStatus
            """;
    private static final String FIND_COURSES = """
            MATCH (course:Course)
            RETURN course.id AS id, course.code AS code,
                   course.subject AS subject, course.number AS number,
                   course.title AS title, course.credits AS credits,
                   course.catalogYear AS catalogYear, course.sourceUrl AS sourceUrl,
                   course.sourceSha256 AS sourceSha256, course.reviewStatus AS reviewStatus,
                   course.retrievedAt AS retrievedAt
            ORDER BY course.id
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

    public void saveCourse(CatalogCourses catalog, CatalogCourses.Course course) {
        var source = catalog.sourceFor(course);
        var parameters = new java.util.HashMap<String, Object>(Map.<String, Object>of(
                "id", course.id(),
                "code", course.code(),
                "subject", course.subject(),
                "number", course.number(),
                "title", course.title(),
                "credits", course.credits(),
                "catalogYear", catalog.catalogYear(),
                "sourceUrl", source.sourceUrl(),
                "sourceSha256", source.sourceSha256(),
                "reviewStatus", course.reviewStatus().name()
        ));
        parameters.put("retrievedAt", source.retrievedAt());
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.executeWrite(transaction -> {
                transaction.run(SAVE_COURSE, parameters).consume();
                return null;
            });
        }
    }

    public List<CatalogCourse> findCourses() {
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            return session.run(FIND_COURSES).list(record -> new CatalogCourse(
                    record.get("id").asString(),
                    record.get("code").asString(),
                    record.get("subject").asString(),
                    record.get("number").asString(),
                    record.get("title").asString(),
                    record.get("credits").asInt(),
                    record.get("catalogYear").asString(),
                    record.get("sourceUrl").asString(),
                    record.get("sourceSha256").asString(),
                    ReviewStatus.valueOf(record.get("reviewStatus").asString()),
                    record.get("retrievedAt").asString(null)
            ));
        }
    }

    /** Reads only the metadata needed for a proposed semester's credit check. */
    public List<CourseCredits> findCourseCredits(List<String> courseIds) {
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            return session.run("""
                    MATCH (course:Course) WHERE course.id IN $ids
                    RETURN course.id AS id, course.credits AS credits, course.reviewStatus AS reviewStatus
                    """, Map.of("ids", courseIds)).list(record -> new CourseCredits(
                    record.get("id").asString(),
                    record.get("credits").isNull() ? null : record.get("credits").asInt(),
                    record.get("reviewStatus").isNull() ? null
                            : ReviewStatus.valueOf(record.get("reviewStatus").asString())));
        }
    }

    public record CourseCredits(String courseId, Integer credits, ReviewStatus reviewStatus) {}

    public record CatalogCourse(
            String id, String code, String subject, String number, String title,
            int credits, String catalogYear, String sourceUrl, String sourceSha256,
            ReviewStatus reviewStatus, String retrievedAt
    ) {
        public CatalogCourse(String id, String code, String subject, String number, String title,
                             int credits, String catalogYear, String sourceUrl, String sourceSha256,
                             ReviewStatus reviewStatus) {
            this(id, code, subject, number, title, credits, catalogYear, sourceUrl, sourceSha256,
                    reviewStatus, null);
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
