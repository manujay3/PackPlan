package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CourseRule;
import com.packplan.catalog.model.RequirementExpression;
import com.packplan.catalog.model.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.packplan.catalog.model.RequirementExpression.*;
import static org.assertj.core.api.Assertions.*;

class RequirementExpressionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preservesNestedAlternativesTimingGradesAndUnsupportedTextThroughJson() throws Exception {
        // Synthetic examples, not reviewed academic requirements.
        var expression = new AllOf(List.of(
                new AnyOf(List.of(
                        new Course("sample-a-2026-2027", Timing.COMPLETED_BEFORE, MinimumGrade.C),
                        new Course("sample-b-2026-2027", Timing.COMPLETED_BEFORE, MinimumGrade.B_MINUS))),
                new Course("sample-lab-2026-2027", Timing.CONCURRENT, MinimumGrade.NOT_SPECIFIED),
                new Course("sample-c-2026-2027", Timing.COMPLETED_BEFORE_OR_CONCURRENT, MinimumGrade.C_MINUS),
                new Unsupported("Permission of instructor", "Requires manual approval")));
        var rule = new CourseRule("sample-target-2026-2027", "2026-2027",
                "https://example.com/catalog", "a".repeat(64), "2026-10-17T12:00:00Z",
                "Synthetic rule text", ReviewStatus.UNREVIEWED, expression);

        var json = mapper.writeValueAsString(rule);
        assertThat(json).contains("\"type\":\"allOf\"", "\"type\":\"anyOf\"", "\"type\":\"unsupported\"");
        assertThat(mapper.readValue(json, CourseRule.class)).isEqualTo(rule);
    }

    @Test
    void rejectsMalformedOrUnknownRulesInsteadOfTreatingThemAsSatisfied() {
        assertThatThrownBy(() -> new AllOf(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnyOf(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Course("", Timing.CONCURRENT, MinimumGrade.C))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Course("sample", null, MinimumGrade.C))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Unsupported("", "manual"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"type\":\"unknown\"}", RequirementExpression.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void groupsCannotBeChangedThroughTheirInputLists() {
        var children = new ArrayList<RequirementExpression>();
        children.add(new Unsupported("Example", "Manual review"));
        var group = new AllOf(children);
        children.clear();
        assertThat(group.children()).hasSize(1);
        assertThatThrownBy(() -> group.children().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
