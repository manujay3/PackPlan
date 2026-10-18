package com.packplan.catalog;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "packplan.catalog.import-courses", havingValue = "true")
public class CatalogCourseImportRunner implements ApplicationRunner {
    private final CatalogImportService importService;

    public CatalogCourseImportRunner(CatalogImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        importService.importCourses();
    }
}
