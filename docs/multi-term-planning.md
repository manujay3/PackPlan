# Multi-term planning

Open **Plan terms** to create a draft with one to six terms. It starts with
Spring 2027, and you can add terms before or after it. The vertical order is
the planning order; term labels are free text and are not checked against an
academic calendar. Every term needs at least one course and a positive whole
number credit cap. A course can only be placed in one planned term.

The checker calls the existing semester eligibility endpoint once per term, in
order. Each request includes the student's manually entered prior attempts and
courses selected for that term. Same-term course relationships are checked as
concurrent enrollment by the semester evaluator. Previous-term courses are not
silently counted as passed. The user can explicitly choose to carry every
course in a term forward as completed, with earned credit and a chosen grade.
That scenario assumption is used in later term checks even when the earlier
term has blockers or needs review, and the interface says so beside the choice.

For Spring 2027, **Find sections** queries NC State's public Class Search for
the selected course code. PackPlan shows the live section number and class
number, component, current seat status, instructor, dates, location, notes, and
meeting pattern. Choose one section per component. Exact day/time overlaps
within overlapping date ranges are flagged. Each response includes its source
link and retrieval time. Section availability can change, and PackPlan does not
check enrollment restrictions or travel time between campuses.
For manually entered courses, a numeric credit value from current Class Search
contributes to the displayed known-credit subtotal. Unknown credits remain
listed; this does not substitute for catalog-backed eligibility or degree-audit
checks.

The working draft and checked results stay in memory until you choose **Save
progress** or save a plan alternative. The separate progress save accepts
incomplete attempts, including earned-credit fields that still need review; it
stays in this browser, can be loaded or deleted, and is not uploaded. Editing
any term clears prior results. Term labels stay on the page and are not sent to
the API. Assumed outcomes are sent as completed-course evidence to later term
checks.

## Saved plans

Save two named snapshots in this browser. A snapshot includes the course list
and ordering, selected section records, term labels and caps, prior attempts,
and pass assumptions. Section records retain their retrieval time. Load
restores the inputs but does not restore rule-check results. Compare sends both
snapshots through fresh sequential rule checks; section data is not refreshed
by comparison. Comparison results are memory-only and are cleared if either
saved slot changes. Deleting the last plan removes only the plan storage key.
These saves remain on this browser and are not connected to a student account.

## Verification — October 27, 2026

The production build passed. All 31 frontend unit checks passed. The full
desktop/mobile browser suite passed 60 checks and skipped 10 opt-in live cases:
eight eligibility checks requiring the sample database and the live schedule
case on both device projects. The full backend suite passed 133 tests on Java
21; after the final meeting-time formatting adjustment, both focused schedule
parser tests passed again. Focused browser checks also passed for PDF import,
transcript columns, incomplete draft recovery, and Degree Audit course selection.
Degree requirements, graduation feasibility, enrollment restrictions, travel
time between campuses, and registration are not checked. The live section
status is only a snapshot from retrieval time. The catalog sample is incomplete,
so manual review results and unavailable rule data remain visible.

The schedule API and desktop planner were also run against the published NC
State Spring 2027 search: CSC 116 returned seven lecture sections, including
meeting patterns, dates, seat status, instructors, and course notes. Selecting
sections and detecting a real meeting-time conflict was verified in the browser
on October 27, 2026. Browser tests use mock API responses unless explicitly run
with the live schedule flag. The database-backed catalog and eligibility checks
remain unavailable while Neo4j is not running on port 7687.

## Course-history import

The planner accepts copied HTML tables, pasted text, CSV/TSV files, and
text-based PDF files up to 10 MB and 50 pages. PDF text is extracted locally
into an editable text area;
review or fix the table rows and headers, then preview them before import.
Scanned PDFs need OCR before PackPlan can read their text. In the browser,
choose the course and grade columns, and optionally an earned-credit yes/no
column. Course codes are matched to the current catalog when possible;
otherwise, the exact code is retained for later review. Recognized grades are
mapped to the planner's grade options. Numeric credit-hour values are not
treated as proof that credit was earned; blank or unclear values must be
confirmed per attempt before a plan can be checked or saved. Unrecognized
grades can be mapped manually during preview. Rows without a recognizable
course code are excluded. Repeated attempts remain separate. Text and files are
read locally and are not uploaded.
NC State's `IN` and `LA` symbols map to the planner's incomplete outcome, and
`CR` maps to credit; earned-credit confirmation is still separate. Other
nonstandard symbols remain for manual mapping, following the [NC State grading
regulation](https://policies.ncsu.edu/regulation/reg-02-50-03/).

NC State directs students to **MyPack Portal → Student Homepage → Academic
Records → View Unofficial Transcript** for the online transcript view. Copy
the course-history table with its header row into PackPlan's paste field; HTML
clipboard tables are converted locally to editable tab-separated text. Then
review the detected columns and every attempt before importing. Transcript
units are not treated as proof that credit was earned. The Degree Audit is a
separate planning view that shows requirement completion and planned courses;
use it to choose courses, and use transcript history for prior-attempt evidence.
See NC State's [transcript access guidance](https://studentservices.ncsu.edu/academics/grading/deans-list/)
and [Degree Audit guide](https://studentservices.ncsu.edu/academics/degrees/degree-planning/perform-a-degree-audit/).

## Degree Audit course choices

Use **Courses to consider** to paste copied requirement text or upload a text
file or text-based PDF. PackPlan extracts distinct course-code-shaped strings
with their source row, but does not interpret audit completion status or tell
you which course to take. Review and select the choices you want, then add them
to a chosen term. Existing planned courses are disabled to prevent duplicates.
Scanned PDFs need OCR. This path has been verified with copied-text fixtures,
not an actual MyPack audit export.

Planned courses can also be added by code when they are missing from the
bundled catalog. Find sections can provide the current listed credits, but
stored eligibility rules and degree requirements remain unavailable and need
manual review.

PDF extraction is tested with a generated text-based transcript fixture, not an
actual MyPack PDF. Full-page MyPack markup is not imported; copied HTML tables
are converted locally for review. The bundled sample catalog
is used if the catalog API is empty or unavailable, allowing local drafts and saves; eligibility checks still require
the imported catalog and a running Neo4j database. A redacted MyPack example is
needed to validate a direct MyPack-specific importer.
