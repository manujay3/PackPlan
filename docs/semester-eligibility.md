# Semester enrollment rules

Open `/semester` or choose **Check a semester**. Enter prior completed attempts
and select every proposed course for one target term. The screen reuses the
course evidence form and condition explanations. Editing evidence or selections
clears the result; leaving or reloading clears the current draft. You can
explicitly save two alternatives in this browser and load them again.

`POST /api/semesters/eligibility` accepts:

```json
{
  "completedCourses": [
    {"courseId":"csc-116-2026-2027","grade":"C","creditEarned":true}
  ],
  "proposedCourses": ["csc-216-2026-2027", "csc-217-2026-2027"],
  "creditCap": 15
}
```

Both lists and a positive whole-number `creditCap` are required. The cap is a
user-chosen planning limit; the screen starts with no cap selected. Older
requests without `creditCap` now return HTTP 400. Proposed courses must be nonempty, unique, nonblank
IDs. Invalid evidence returns HTTP 400 before database access. The response
uses HTTP 200 and `Cache-Control: no-store`, with an overall `status` and a
`courses` list of explained course results in request order, plus `creditLoad`.

For each proposed course, the evaluator uses the shared prior attempts and
all other proposed courses as same-term enrollment. It never includes a course
as its own concurrent evidence, promotes proposed enrollment to earned credit,
or recursively expands the rule graph. The CSC 216/217 pair therefore evaluates
without a loop. The [single-course grade and timing behavior](eligibility.md)
also applies here.

The semester is `BLOCKED` if any course or the credit load is blocked; otherwise
it is `MANUAL_REVIEW` if either requires review; otherwise it is `SATISFIED`.
Missing stored rules, including unknown course IDs, require manual review.
Unsupported and unreviewed rules cannot silently pass. A database error fails
the request rather than returning missing-rule or success results.

Individual course results describe their own stored conditions assuming the
selected classmates enroll. For example, a course can have a satisfied local
corequisite condition while a selected classmate is blocked by its own
prerequisite. The overall semester still reports blocked, and every course's
explanation remains visible. Individual results are not independent approvals
to register. Request reads are not an atomic catalog-version snapshot.

## Credit load

The server reads credit metadata for the proposed IDs from Neo4j. Only unique,
reviewed records with nonnegative whole-number credits contribute to the known
subtotal. Completed attempts are not included in the term load. Missing,
duplicate, unreviewed, or missing/negative credit values remain unresolved.
Malformed database value types fail the request rather than silently passing.

`creditLoad` contains `status`, `creditCap`, `knownCredits`, `totalCredits`,
`unresolvedCourses`, and `reason`. A complete total equal to the cap is within
the limit; a subtotal greater than the cap blocks even if other credits are
unknown. Otherwise unresolved values require manual review. `totalCredits` is
null when incomplete, so a known subtotal of zero never claims a complete zero
load. Totals use 64-bit arithmetic. This fixed-credit sample does not model
variable-credit choices or institution-specific overload approvals.

The form displays a catalog preview; the result shows the server's credit
check. Changing the cap invalidates the previous result and cancels a pending
check, just like changing courses or grades. Caps are required, positive JSON
integers; fractions, strings, booleans, missing/null values, and values outside
the API integer range are rejected. The API does not persist the cap; explicit
browser saves include it with the other evidence.

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
offerings/timetables, audit degree requirements, resolve
transfer equivalencies, or establish graduation eligibility. The credit total
preview on screen is informational; the server enforces the selected cap.
Exact catalog IDs and prior earned-credit claims
come from the caller. The sample remains incomplete and `catalogReady` stays
false.

## Verification — October 26, 2026

The initial semester implementation was verified as follows; the later cap
verification is recorded below.

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

## Credit-cap verification — October 26, 2026

All 131 backend tests passed, including cap boundaries (3, 4, and 5 for the
4-credit pair), invalid/missing caps, nullable metadata reads, missing and
unreviewed credit values, duplicates, known overloads with unknown totals,
64-bit totals, and database failure. The course-rule checks remain unchanged.

The production build and all 28 desktop/mobile browser tests passed. New cases
cover required/whole-number caps, cap-edit invalidation, overloads despite
satisfied course rules, the exact cap boundary, and explicitly unknown totals.
The updated mobile screen was visually inspected. These tests mock API data;
four live-test instances are skipped by the ordinary browser command.

Both opt-in live desktop checks passed through Vite, Spring Boot, and Neo4j.
The real 4-credit CSC 216/217 pair blocks with a cap of 3 and satisfies the
credit check with a cap of 4. Missing rules and strict prerequisites still
produce review/blocking results with a larger cap. Imports were disabled;
no evidence or catalog changes were saved. Vite, the updated backend, and
Neo4j remain running; application images were not rebuilt.

## Saved alternatives

The **Save and compare semesters** section holds two named slots, A and B.
**Save current as A/B** copies a valid draft's proposed course IDs, completed
attempts (including grades and earned-credit flags), and credit cap. **Replace
A/B** overwrites only that slot with the current valid draft. Each save shows
its local save time, exact catalog year where available, course selection,
prior attempts, and cap. Different selections are listed as courses unique to
each alternative.

Each draft may also have an optional term label, such as “Fall 2026,” and
per-course offering assumptions. These labels and checkboxes are user supplied
planning notes; the application does not verify the term or course availability.
They are displayed with the saved plan and restored when it is loaded. They are
not sent to the eligibility API and do not affect its result. Existing saves
without these fields load with an unlabeled term and no offering assumptions.

**Load A/B** replaces the current form inputs and clears any previous result;
it does not restore an eligibility verdict. Courses absent from the current
catalog remain visible with their exact IDs, rather than being dropped or
mapped to another year. Such selections show an unknown credit preview and
can be unchecked. Completed attempts with unavailable IDs are also retained.

**Compare saved alternatives** sends each saved evidence snapshot to the
current semester API. It shows separate credit loads, overall results, and
expandable course-condition explanations. A failed request stays an error for
that alternative, even if the other succeeds. Retrying rechecks both. Results
and checked times stay in memory only. Changing, deleting, or loading a save,
or receiving a save change from another tab, cancels and clears the comparison.
Editing the current unsaved form does not alter the saved comparison evidence.

The storage key is `packplan.semester-alternatives.v1`, with a versioned pair
of slots. Saved records contain inputs, planning notes, and save time, never
API results.
Data stays in this browser/origin, including the entered grades; it is not an
account, cloud sync, or server-side profile. **Delete A/B** removes a slot;
deleting the last slot removes the feature key. Other browser data is untouched.
Navigating away does not auto-save the current draft.

Saved evidence is validated on read. Incompatible or corrupt data is left
untouched and reported; **Clear saved alternatives** explicitly removes that
feature key. Storage failures do not claim a successful save or discard the
current form. Writes read the latest saved pair first and other-tab changes
refresh the saved section without replacing the current draft. Simultaneous
tab writes still use the browser's last-write-wins behavior. Comparisons are
independent API reads, not a shared transactional catalog snapshot.

## Saved-alternative verification — October 26, 2026

The production build, seven storage unit tests (`npm run test:unit`), and all
38 desktop/mobile browser checks passed. Storage tests cover evidence-only
snapshots, replacement, retaining the other slot, quota/read errors, corrupt
data, invalid evidence, unavailable course IDs, and deletion isolation. Browser
checks add reload/load persistence, fresh comparison payloads, separate results,
partial failure/retry, corrupt-data recovery, failed-write preservation,
unavailable-course retention, and cancellation after another-tab changes.
The final alternative-specific checks also passed at both viewport sizes.

All three opt-in live browser checks passed through Vite, Spring Boot, and
Neo4j. The new check saves the real CSC 216/217 pair with caps of 4 and 3,
compares satisfied versus blocked results, reloads without cached verdicts,
loads and rechecks a saved draft, and deletes both saves. Browser checks use
isolated test storage and leave no demo saves there. No backend code changed
for this feature and no catalog data was written. The local application remains
running; container images were not rebuilt. Desktop and mobile comparison
layouts were visually inspected. The ordinary browser command now skips eight
live instances (four real-backend scenarios at each viewport).
