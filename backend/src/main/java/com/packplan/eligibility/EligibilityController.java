package com.packplan.eligibility;

import com.packplan.catalog.CourseRuleRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class EligibilityController {
    private final CourseRuleRepository repository;
    private final EligibilityEvaluator evaluator;

    public EligibilityController(CourseRuleRepository repository, EligibilityEvaluator evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    @PostMapping("/api/catalog/courses/{courseId}/eligibility")
    public ResponseEntity<EligibilityResult> eligibility(@PathVariable String courseId,
                                                        @RequestBody EnrollmentEvidence evidence) {
        var result = repository.findByCourseId(courseId)
                .map(rule -> evaluator.evaluate(rule, evidence))
                .orElseGet(() -> evaluator.missingRule(courseId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
