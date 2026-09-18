package com.packplan;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "packplan.catalog.import-programs", havingValue = "true")
public class CatalogImportRunner implements ApplicationRunner {
    private final CatalogImportService importService;

    public CatalogImportRunner(CatalogImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        importService.importPrograms();
    }
}
