package com.packplan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CatalogImportRunnerTest {
    @Test
    void invokesTheImportService() {
        var importService = mock(CatalogImportService.class);
        var runner = new CatalogImportRunner(importService);

        runner.run(new DefaultApplicationArguments());

        verify(importService).importPrograms();
    }
}
