package com.packplan.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;
import com.packplan.catalog.model.ReviewStatus;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Repository;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

@Repository
public class CourseRuleRepository {
    private static final String SAVE = """
            MATCH (course:Course {id: $courseId, catalogYear: $catalogYear})
            MERGE (rule:CourseRule {id: $courseId})
            SET rule.catalogYear = $catalogYear, rule.sourceUrl = $sourceUrl,
                rule.sourceSha256 = $sourceSha256, rule.retrievedAt = $retrievedAt,
                rule.originalText = $originalText, rule.reviewStatus = $reviewStatus,
                rule.expressionJson = $expressionJson
            MERGE (course)-[:HAS_RULE]->(rule)
            RETURN rule.id AS id
            """;
    private static final String FIND = """
            MATCH (course:Course {id: $courseId})-[:HAS_RULE]->(rule:CourseRule {id: $courseId})
            WHERE course.catalogYear = rule.catalogYear
            RETURN course.id AS courseId, rule.catalogYear AS catalogYear,
                   rule.sourceUrl AS sourceUrl, rule.sourceSha256 AS sourceSha256,
                   rule.retrievedAt AS retrievedAt, rule.originalText AS originalText,
                   rule.reviewStatus AS reviewStatus, rule.expressionJson AS expressionJson
            """;

    private final Driver driver;
    private final ObjectMapper mapper;

    public CourseRuleRepository(Driver driver, ObjectMapper mapper) {
        this.driver = driver;
        this.mapper = mapper;
    }

    /** Stores review status as supplied; callers must validate references before saving. */
    public void save(CourseRule rule) {
        String expressionJson;
        try {
            expressionJson = mapper.writerFor(RequirementExpression.class).writeValueAsString(rule.expression());
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Could not serialize course rule: " + rule.courseId(), exception);
        }
        var parameters = Map.<String, Object>of(
                "courseId", rule.courseId(), "catalogYear", rule.catalogYear(),
                "sourceUrl", rule.sourceUrl(), "sourceSha256", rule.sourceSha256(),
                "retrievedAt", rule.retrievedAt(), "originalText", rule.originalText(),
                "reviewStatus", rule.reviewStatus().name(), "expressionJson", expressionJson);
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.executeWrite(transaction -> {
                var result = transaction.run(SAVE, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("Owning course missing or catalog year mismatched: " + rule.courseId());
                }
                result.single();
                result.consume();
                return null;
            });
        }
    }

    public Optional<CourseRule> findByCourseId(String courseId) {
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            var result = session.run(FIND, Map.of("courseId", courseId));
            if (!result.hasNext()) return Optional.empty();
            var record = result.single();
            try {
                return Optional.of(new CourseRule(
                        record.get("courseId").asString(), record.get("catalogYear").asString(),
                        record.get("sourceUrl").asString(), record.get("sourceSha256").asString(),
                        record.get("retrievedAt").asString(), record.get("originalText").asString(),
                        ReviewStatus.valueOf(record.get("reviewStatus").asString()),
                        mapper.readValue(record.get("expressionJson").asString(), RequirementExpression.class)));
            } catch (JsonProcessingException exception) {
                throw new UncheckedIOException("Could not deserialize course rule: " + courseId, exception);
            }
        }
    }
}
