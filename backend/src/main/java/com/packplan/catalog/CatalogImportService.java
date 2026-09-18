package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogPrograms;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;

@Service
public class CatalogImportService {
    private final ObjectMapper objectMapper;
    private final CatalogRepository repository;

    public CatalogImportService(ObjectMapper objectMapper, CatalogRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    public void importPrograms() {
        try (var input = new ClassPathResource("catalog/programs.json").getInputStream()) {
            var catalog = objectMapper.readValue(input, CatalogPrograms.class);

            for (var program : catalog.programs()) {
                repository.saveProgram(catalog.catalogYear(), program);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the reviewed program catalog", exception);
        }
    }
}
