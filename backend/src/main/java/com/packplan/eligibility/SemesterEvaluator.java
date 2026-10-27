package com.packplan.eligibility;

import com.packplan.catalog.CourseRuleRepository;
import com.packplan.catalog.CatalogRepository;
import com.packplan.catalog.model.ReviewStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

import static com.packplan.eligibility.EligibilityResult.Status.*;

@Service
public class SemesterEvaluator {
    private final CourseRuleRepository repository;
    private final EligibilityEvaluator evaluator;
    private final CatalogRepository catalog;

    public SemesterEvaluator(CourseRuleRepository repository, EligibilityEvaluator evaluator, CatalogRepository catalog) {
        this.repository = repository;
        this.evaluator = evaluator;
        this.catalog = catalog;
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
        var creditLoad = creditLoad(evidence);
        var status = creditLoad.status() == BLOCKED || results.stream().anyMatch(result -> result.status() == BLOCKED) ? BLOCKED
                : creditLoad.status() == MANUAL_REVIEW || results.stream().anyMatch(result -> result.status() == MANUAL_REVIEW)
                    ? MANUAL_REVIEW : SATISFIED;
        return new Result(status, results, creditLoad);
    }

    private CreditLoad creditLoad(SemesterEvidence evidence) {
        var records = catalog.findCourseCredits(evidence.proposedCourses());
        long known = 0;
        var unresolved = new java.util.ArrayList<String>();
        for (var id : evidence.proposedCourses()) {
            var matches = records.stream().filter(course -> course.courseId().equals(id)).toList();
            if (matches.size() != 1 || matches.getFirst().credits() == null || matches.getFirst().credits() < 0
                    || matches.getFirst().reviewStatus() != ReviewStatus.REVIEWED) {
                unresolved.add(id);
            } else known += matches.getFirst().credits();
        }
        var status = known > evidence.creditCap() ? BLOCKED : unresolved.isEmpty() ? SATISFIED : MANUAL_REVIEW;
        var reason = known > evidence.creditCap() ? "Known credits exceed your cap by " + (known - evidence.creditCap()) + "."
                : unresolved.isEmpty() ? "Proposed credits are within your cap."
                : "Some courses lack unique, reviewed credit values; the complete total is unknown.";
        return new CreditLoad(status, evidence.creditCap(), known, unresolved.isEmpty() ? known : null, unresolved, reason);
    }

    public record CreditLoad(EligibilityResult.Status status, int creditCap, long knownCredits, Long totalCredits,
                             List<String> unresolvedCourses, String reason) {
        public CreditLoad { unresolvedCourses = List.copyOf(unresolvedCourses); }
    }

    public record Result(EligibilityResult.Status status, List<EligibilityResult> courses, CreditLoad creditLoad) {
        public Result { courses = List.copyOf(courses); }
    }
}
