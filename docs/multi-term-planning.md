# Multi-term planning

Open **Plan terms** to create a draft with one to six terms. It starts with
Spring 2027, and you can add terms before or after it. The vertical order is
the planning order; term labels are free text and are not checked against an
academic calendar. Every term needs at least one course and a positive whole
number credit cap. A course can only be placed in one planned term.

The checker calls the existing semester eligibility endpoint once per term,
in order. Each request includes the student's manually entered prior attempts
and courses selected for that term. Same-term course relationships are checked
as concurrent enrollment by the semester evaluator. Previous-term courses are
not silently counted as passed. The user can explicitly choose to carry every
course in a term forward as completed, with earned credit and a chosen grade.
That scenario assumption is used in later term checks even when the earlier
term has blockers or needs review, and the interface says so beside the choice.

The unsaved draft and checked results stay in memory only. Editing any term
clears prior results. Term labels stay on the page and are not sent to the API.
Assumed outcomes are sent as completed-course evidence to later term checks.

## Saved plans

Save two named snapshots in this browser. A snapshot includes the course list
and ordering, term labels and caps, prior attempts, and pass assumptions. Load
restores the inputs but does not restore results. Compare sends both snapshots
through fresh sequential API checks. Comparison results are memory-only and
are cleared if either saved slot changes. Deleting the last plan removes only
the plan storage key. These saves remain on this browser and are not connected
to a student account.

## Verification — October 27, 2026

The production build passed. Sixteen storage and sequencing unit checks passed;
the full desktop/mobile browser suite passed 40 checks and skipped eight
opt-in live-backend cases. The added Spring 2027 live-browser case could not
run because Docker Desktop reported it could not start and Neo4j was unavailable
on port 7687. Browser tests use mock API responses and do not establish that
live prerequisite results are currently available.
Course offerings, timetable conflicts, academic calendar dates, degree
requirements, graduation feasibility, and registration are not checked. The
catalog sample is incomplete, so manual review results and unavailable rule
data remain visible.
