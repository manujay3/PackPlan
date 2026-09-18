package com.packplan.catalog;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CatalogController {
    private final CatalogRepository repository;

    public CatalogController(CatalogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/catalog/programs")
    public ResponseEntity<List<CatalogRepository.CatalogProgram>> programs() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(repository.findPrograms());
    }
}
