package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.CourseRule;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Explicit single-rule import; constructing this service does not write catalog data. */
@Service
public class CourseRuleImportService {
    private final CourseRuleLoader loader;
    private final CourseRuleRepository repository;

    public CourseRuleImportService(ObjectMapper mapper, CourseRuleRepository repository) {
        this.loader = new CourseRuleLoader(mapper);
        this.repository = repository;
    }

    /** The supplied catalog is used for reference validation, not imported by this method. */
    public CourseRule importRule(Resource source, CatalogCourses catalog) {
        var rule = loader.load(source, catalog);
        repository.save(rule);
        return rule;
    }
}
