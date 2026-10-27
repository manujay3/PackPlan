package com.packplan.catalog;

import com.packplan.catalog.model.CourseRule;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseRuleController {
    private final CourseRuleRepository repository;

    public CourseRuleController(CourseRuleRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/catalog/courses/{courseId}/rule")
    public ResponseEntity<CourseRule> rule(@PathVariable String courseId) {
        return repository.findByCourseId(courseId)
                .map(rule -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rule))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build());
    }
}
