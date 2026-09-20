package com.packplan.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.packplan.catalog.model.CatalogCourses;
import com.packplan.catalog.model.CatalogPrograms;
import com.packplan.catalog.model.CatalogRequirements;
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

    @Test
    void sendsBothReviewedCoursesToTheRepository() {
        var repository = mock(CatalogRepository.class);
        var service = new CatalogImportService(new ObjectMapper(), repository);

        service.importCourses();

        var course = ArgumentCaptor.forClass(CatalogCourses.Course.class);
        verify(repository, times(2)).saveCourse(
                org.mockito.ArgumentMatchers.eq("2026-2027"),
                org.mockito.ArgumentMatchers.eq("https://catalog.ncsu.edu/course-descriptions/csc/"),
                org.mockito.ArgumentMatchers.eq(
                        "d3a77e2e7f150f54d70f2865e657a74884460f7385c147029d71ca91ae5d7455"
                ),
                course.capture()
        );
        assertThat(course.getAllValues())
                .extracting(CatalogCourses.Course::id)
                .containsExactly("csc-216-2026-2027", "csc-217-2026-2027");
    }

    @Test
    void sendsTheReviewedRequirementToTheRepository() {
        var repository = mock(CatalogRepository.class);
        var service = new CatalogImportService(new ObjectMapper(), repository);

        service.importRequirements();

        var requirement = ArgumentCaptor.forClass(CatalogRequirements.Requirement.class);
        verify(repository).saveRequirement(
                org.mockito.ArgumentMatchers.eq("2026-2027"),
                org.mockito.ArgumentMatchers.eq("computer-science-bs-2026-2027"),
                org.mockito.ArgumentMatchers.eq(
                        "https://catalog.ncsu.edu/undergraduate/engineering/computer-science/computer-science-bs/"
                ),
                org.mockito.ArgumentMatchers.eq(
                        "ecbcce1c3dce6811c57431e9e86eec7f3131f32f76f59d3dc3b32b832e4da0f9"
                ),
                requirement.capture()
        );
        assertThat(requirement.getValue().id()).isEqualTo("cs-software-fundamentals-2026-2027");
        assertThat(requirement.getValue().rule().type())
                .isEqualTo(CatalogRequirements.RuleType.ALL_OF);
    }
}
