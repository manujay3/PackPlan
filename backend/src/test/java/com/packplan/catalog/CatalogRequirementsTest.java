package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogRequirements;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRequirementsTest {
    @Test
    void readsReviewedAllOfRequirementFromJson() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog/requirements.json")) {
            assertThat(input).isNotNull();

            var catalog = new ObjectMapper().readValue(input, CatalogRequirements.class);
            var requirement = catalog.requirements().getFirst();

            assertThat(catalog.catalogYear()).isEqualTo("2026-2027");
            assertThat(catalog.programId()).isEqualTo("computer-science-bs-2026-2027");
            assertThat(requirement.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
            assertThat(requirement.requiredCredits()).isEqualTo(4);
            assertThat(requirement.evaluable()).isTrue();
            assertThat(requirement.rule().type()).isEqualTo(CatalogRequirements.RuleType.ALL_OF);
            assertThat(requirement.rule().children())
                    .extracting(CatalogRequirements.Rule::courseId)
                    .containsExactly("csc-216-2026-2027", "csc-217-2026-2027");
            assertThat(requirement.rule().children())
                    .allMatch(rule -> rule.type() == CatalogRequirements.RuleType.COURSE)
                    .allMatch(rule -> "C".equals(rule.minimumGrade()));
        }
    }
}
