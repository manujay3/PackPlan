package com.packplan.eligibility;

import com.packplan.catalog.CourseRuleRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

import static com.packplan.eligibility.EligibilityResult.Status.*;

@Service
public class SemesterEvaluator {
    private final CourseRuleRepository repository;
    private final EligibilityEvaluator evaluator;

    public SemesterEvaluator(CourseRuleRepository repository, EligibilityEvaluator evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    public Result evaluate(SemesterEvidence evidence) {
        var results = evidence.proposedCourses().stream().map(id -> {
            var others = new HashSet<>(evidence.proposedCourses());
            others.remove(id);
            var courseEvidence = new EnrollmentEvidence(evidence.completedCourses(), others);
            return repository.findByCourseId(id)
                    .map(rule -> evaluator.evaluate(rule, courseEvidence))
                    .orElseGet(() -> evaluator.missingRule(id));
        }).toList();
        var status = results.stream().anyMatch(result -> result.status() == BLOCKED) ? BLOCKED
                : results.stream().anyMatch(result -> result.status() == MANUAL_REVIEW) ? MANUAL_REVIEW : SATISFIED;
        return new Result(status, results);
    }

    public record Result(EligibilityResult.Status status, List<EligibilityResult> courses) {
        public Result { courses = List.copyOf(courses); }
    }
}
