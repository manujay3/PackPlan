package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogPrograms;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogProgramsTest {
    @Test
    void readsReviewedProgramsFromJson() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/programs.json")) {
            assertThat(input).isNotNull();

            var catalog = new ObjectMapper().readValue(input, CatalogPrograms.class);

            assertThat(catalog.catalogYear()).isEqualTo("2026-2027");
            assertThat(catalog.programs()).hasSize(2);
            assertThat(catalog.programs())
                    .extracting(CatalogPrograms.Program::id)
                    .containsExactly(
                            "computer-science-bs-2026-2027",
                            "economics-ba-2026-2027"
                    );
            assertThat(catalog.programs())
                    .allMatch(program -> program.reviewStatus() == ReviewStatus.REVIEWED);
        }
    }
}
