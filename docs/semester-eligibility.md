# Semester enrollment rules

Open `/semester` or choose **Check a semester**. Enter prior completed attempts
and select every proposed course for one target term. The screen reuses the
course evidence form and condition explanations. Editing evidence or selections
clears the result; leaving or reloading clears all entries. Nothing is saved.

`POST /api/semesters/eligibility` accepts:

```json
{
  "completedCourses": [
    {"courseId":"csc-116-2026-2027","grade":"C","creditEarned":true}
  ],
  "proposedCourses": ["csc-216-2026-2027", "csc-217-2026-2027"]
}
```

Both lists are required. Proposed courses must be nonempty, unique, nonblank
IDs. Invalid evidence returns HTTP 400 before database access. The response
uses HTTP 200 and `Cache-Control: no-store`, with an overall `status` and a
`courses` list of explained course results in request order.

For each proposed course, the evaluator uses the shared prior attempts and
all other proposed courses as same-term enrollment. It never includes a course
as its own concurrent evidence, promotes proposed enrollment to earned credit,
or recursively expands the rule graph. The CSC 216/217 pair therefore evaluates
without a loop. The [single-course grade and timing behavior](eligibility.md)
also applies here.

The semester is `BLOCKED` if any course is blocked; otherwise it is
`MANUAL_REVIEW` if any course requires review; otherwise it is `SATISFIED`.
Missing stored rules, including unknown course IDs, require manual review.
Unsupported and unreviewed rules cannot silently pass. A database error fails
the request rather than returning missing-rule or success results.

Individual course results describe their own stored conditions assuming the
selected classmates enroll. For example, a course can have a satisfied local
corequisite condition while a selected classmate is blocked by its own
prerequisite. The overall semester still reports blocked, and every course's
explanation remains visible. Individual results are not independent approvals
to register. Request reads are not an atomic catalog-version snapshot.

## Examples and scope

- Earned CSC 116 credit at C or better plus proposed CSC 216 and CSC 217 meets
  both reviewed stored rules.
- Remove CSC 217 without supplying prior earned lab credit: CSC 216 blocks the
  semester.
- Add MA 141: its missing enrollment rule makes an otherwise satisfied semester
  require manual review.
- Propose CSC 116 alongside CSC 216/217 without prior qualifying CSC 116 credit:
  the strict prerequisite still blocks CSC 216/217.
- Meet CSC 116's two corequisite groups: its unsupported seat-reservation
  condition still requires review.

This checks the stored enrollment rules for one caller-asserted target term.
It does not choose courses, schedule future terms, verify term dates, check
offerings/timetables, apply credit caps, audit degree requirements, resolve
transfer equivalencies, or establish graduation eligibility. The credit total
on screen is informational. Exact catalog IDs and prior earned-credit claims
come from the caller. The sample remains incomplete and `catalogReady` stays
false.

## Verification — October 26, 2026

All 117 ordinary backend tests passed. Semester tests exercise the real
controller/evaluator with a mocked rule repository, including paired courses,
missing corequisites, missing/unsupported rules, same-term strict prerequisites,
self-reference exclusion, output order, read-only repository interactions,
invalid input, and database failure.

The production frontend build and 24 desktop/mobile browser checks passed,
including the existing course flow and four new semester scenarios per viewport.
They check evidence payloads, empty selection, credit display, per-course
results, edit invalidation, mode isolation, direct refresh, mobile overflow,
and incomplete/inconsistent responses. These tests mock API responses. Desktop
and mobile semester screenshots were visually inspected; mobile uses Chrome
emulation rather than a physical device.

`npm run test:e2e:live` passed two opt-in desktop browser checks through Vite's
actual proxy, the running Spring Boot backend, and existing local Neo4j. The
semester check exercises the qualifying pair, removing the lab, adding a course
with a missing rule, and removing prerequisite credit. All imports were disabled
when starting the backend. No student evidence or catalog changes were saved.
Application images were not rebuilt; local Vite, backend, and Neo4j remain
running. The ordinary browser command skips the live checks; see
[browser commands](eligibility.md#browser-verification--october-26-2026).
