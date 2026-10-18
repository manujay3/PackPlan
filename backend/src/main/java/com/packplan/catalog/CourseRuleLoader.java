package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.CourseRule;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Reads a rule for inspection; reference validation does not promote its review status. */
public final class CourseRuleLoader {
    private final ObjectMapper objectMapper;
    private final CourseRuleValidator validator = new CourseRuleValidator();

    public CourseRuleLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CourseRule load(Resource source, CatalogCourses catalog) {
        try (var input = source.getInputStream()) {
            var rule = objectMapper.readValue(input, CourseRule.class);
            validator.validateReferences(rule, catalog);
            return rule;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read course rule: " + source.getDescription(), exception);
        }
    }
}
