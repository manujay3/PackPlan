package com.packplan.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;

class CatalogImportRunnerTest {
    @Test
    void importsProgramsThenCoursesThenRequirements() {
        var importService = mock(CatalogImportService.class);
        var runner = new CatalogImportRunner(importService);

        runner.run(new DefaultApplicationArguments());

        var imports = inOrder(importService);
        imports.verify(importService).importPrograms();
        imports.verify(importService).importCourses();
        imports.verify(importService).importRequirements();
    }
}
