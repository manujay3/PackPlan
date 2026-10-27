# Enrollment rule evaluation

`POST /api/catalog/courses/{courseId}/eligibility` reads a persisted rule and
evaluates it against supplied evidence. It writes no student or catalog data.
Successful evaluations use HTTP 200 and `Cache-Control: no-store`. Invalid
evidence returns HTTP 400 before a rule lookup. Neo4j must be available;
database failures are not interpreted as missing rules or satisfied conditions.

Example for `csc-216-2026-2027`:

```json
{
  "completedCourses": [
    {"courseId": "csc-116-2026-2027", "grade": "C", "creditEarned": true}
  ],
  "concurrentCourses": ["csc-217-2026-2027"]
}
```

Both arrays are required and may be empty. Every completed attempt requires
a nonblank course ID, a named grade, and an explicit `creditEarned` boolean.
Attempts may repeat the same course. Evidence uses exact year-specific course
IDs; the evaluator does not map historical or transfer course identities.
The caller asserts completed attempts precede the target term. Concurrent IDs
must describe enrollment in that same term, not courses expected to pass in an
earlier term. No term dates or chronology are inferred or verified.

The response contains `courseId`, `status`, `reviewStatus`, `reason`, and a
`condition` tree. Each condition includes its type, course ID where applicable,
status, reason, and children. For the example, the stored reviewed CSC 216 rule
returns `SATISFIED` with two satisfied course conditions.

## Status behavior

- `SATISFIED`: supplied evidence meets this stored condition/rule.
- `BLOCKED`: a required condition has no qualifying evidence.
- `MANUAL_REVIEW`: evidence, rule review, or an unsupported condition prevents
  a determination.

AND groups are blocked if any child is blocked; otherwise they require review
if any child requires review. OR groups are satisfied if any alternative is
satisfied; otherwise they require review if any alternative requires review.
Every child remains in the result, including unknown alternatives that were
not needed to satisfy an OR group.

An `UNREVIEWED` or `NEEDS_REVIEW` rule cannot return `SATISFIED`. It returns
`MANUAL_REVIEW` unless there is already a known blocker, in which case it
returns `BLOCKED` while retaining its review status and condition explanations.
Missing rules, including unknown course IDs, return HTTP 200 with
`MANUAL_REVIEW`, null `reviewStatus`/`condition`, and an explanation. This differs
from the rule retrieval endpoint's 404 and does not imply no prerequisites.

## Grades, repeats, and timing

Named letter grades run from `A_PLUS` through `D_MINUS` and `F`. Additional
outcomes are `SATISFACTORY`, `UNSATISFACTORY`, `CREDIT`, `TRANSFER`, `WITHDRAWN`,
`INCOMPLETE`, and `UNKNOWN`. Numeric enum indexes and unknown names are rejected.
The evaluator requires explicit earned credit and never infers credit solely
from a letter grade. Claiming earned credit for F, unsatisfactory, withdrawn,
or incomplete outcomes is invalid input.

- With a specified minimum grade, qualifying letter grades are compared to
  that threshold. Earned credit with a non-letter grade requires manual review.
- `NOT_SPECIFIED` accepts explicit prior earned credit, including satisfactory
  or credit outcomes. Failed/withdrawn attempts never qualify. Transfer and
  unknown grades still require manual review even with asserted credit.
- Any qualifying prior attempt suffices, regardless of input order. This is
  sample prerequisite evaluation, not GPA calculation, grade replacement,
  duplicate credit accounting, or an institutional repeat-policy audit.
- `COMPLETED_BEFORE` accepts only qualifying completed evidence. Current or
  concurrent enrollment does not count as passing a prerequisite.
- `COMPLETED_BEFORE_OR_CONCURRENT` also accepts concurrent enrollment.
- `CONCURRENT` requires enrollment in the target term; prior credit alone does
  not meet this strict timing value.
- Concurrent evidence with a specified final-grade threshold requires manual
  review: the evaluator does not assume a future final grade.

## Sample behavior and limits

With earned CSC 116 credit at C or better, same-term CSC 216/217 enrollment can
satisfy each paired course's own stored rule. The evaluator checks expression
trees without recursively expanding course rules, so the legitimate concurrent
pair does not create an evaluation loop.

CSC 116 requires both its computing-environment and calculus alternative
groups. Meeting those conditions still returns `MANUAL_REVIEW` because seat
reservations remain unsupported and the rule is `NEEDS_REVIEW`.

The five dependency courses have reviewed metadata but no stored enrollment
rules. Evaluating one returns manual review. A satisfied course rule does not
verify the rules of its concurrent courses; evaluate those courses separately.
No complete-plan validation, degree requirements, section availability,
placement tests, overrides, transfer equivalencies, or graduation audit is
implemented. `catalogReady` remains false. Evidence is caller-supplied and is
not verified against a transcript or saved.

## Live verification command

With the current sample imported into local Neo4j, use Java 21 and run:

```sh
mvn -f backend/pom.xml -Dtest=EligibilityIT -Dpackplan.live-eligibility=true test
```

This opt-in check disables all imports and exercises Spring MVC through
MockMvc with the real repository. It checks the paired-course results, CSC
116's manual review, cache headers, and unchanged stored rules. It does not
launch a real HTTP listener or exercise the frontend proxy. Ordinary backend
tests use synthetic evidence or sample rules and do not require Neo4j.

## Verification — October 26, 2026

All 106 checks passed with Java 21: 105 ordinary backend tests and one
explicit live eligibility integration test. Coverage includes grade boundaries,
earned credit, repeated attempts, all combinations of AND/OR outcomes,
unsupported conditions, review-status gating, missing rules, exact catalog
identities, prior/concurrent timing, invalid request evidence, and API JSON.
The existing status tests and eligibility API checks retain `catalogReady: false`.

The live check read the current three persisted rules with all imports
disabled, verified expected API outcomes through MockMvc, and confirmed the
stored rules were unchanged. No student data was saved. Neo4j remains running;
application images were not rebuilt. Frontend input and results screens are
not implemented yet.
