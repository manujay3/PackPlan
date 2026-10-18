package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;

@Component
@Order(20)
@ConditionalOnProperty(name = "packplan.catalog.import-draft-rules", havingValue = "true")
public class CatalogDraftRuleImportRunner implements ApplicationRunner {
    private final ObjectMapper mapper;
    private final CourseRuleImportService importService;

    public CatalogDraftRuleImportRunner(ObjectMapper mapper, CourseRuleImportService importService) {
        this.mapper = mapper;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        CatalogCourses catalog;
        try (var input = new ClassPathResource("catalog/courses.json").getInputStream()) {
            catalog = mapper.readValue(input, CatalogCourses.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read course catalog for draft rule import", exception);
        }
        importService.importRule(new ClassPathResource("catalog/drafts/csc-216-rule.json"), catalog);
    }
}
