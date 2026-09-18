package com.packplan.catalog;

import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogControllerTest {
    @Test
    void listsProgramsWithoutCachingTheResponse() throws Exception {
        var repository = mock(CatalogRepository.class);
        when(repository.findPrograms()).thenReturn(List.of(
                new CatalogRepository.CatalogProgram(
                        "computer-science-bs-2026-2027",
                        "Computer Science",
                        "BS",
                        "2026-2027",
                        121,
                        "https://catalog.ncsu.edu/undergraduate/engineering/computer-science/computer-science-bs/",
                        ReviewStatus.REVIEWED
                )
        ));
        var mvc = MockMvcBuilders.standaloneSetup(new CatalogController(repository)).build();

        mvc.perform(get("/api/catalog/programs"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value("computer-science-bs-2026-2027"))
                .andExpect(jsonPath("$[0].catalogYear").value("2026-2027"))
                .andExpect(jsonPath("$[0].totalCredits").value(121))
                .andExpect(jsonPath("$[0].reviewStatus").value("REVIEWED"));
    }
}
