package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogPrograms;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CatalogImportServiceTest {
    @Test
    void sendsBothReviewedProgramsToTheRepository() {
        var repository = mock(CatalogRepository.class);
        var service = new CatalogImportService(new ObjectMapper(), repository);

        service.importPrograms();

        var program = ArgumentCaptor.forClass(CatalogPrograms.Program.class);
        verify(repository, times(2)).saveProgram(
                org.mockito.ArgumentMatchers.eq("2026-2027"),
                program.capture()
        );
        assertThat(program.getAllValues())
                .extracting(CatalogPrograms.Program::id)
                .containsExactly(
                        "computer-science-bs-2026-2027",
                        "economics-ba-2026-2027"
                );
    }
}
