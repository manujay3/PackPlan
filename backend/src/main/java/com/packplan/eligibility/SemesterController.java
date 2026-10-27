package com.packplan.eligibility;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SemesterController {
    private final SemesterEvaluator evaluator;

    public SemesterController(SemesterEvaluator evaluator) { this.evaluator = evaluator; }

    @PostMapping("/api/semesters/eligibility")
    public ResponseEntity<SemesterEvaluator.Result> eligibility(@RequestBody SemesterEvidence evidence) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(evaluator.evaluate(evidence));
    }
}
