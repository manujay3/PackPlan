# Multi-term planning

Open **Plan terms** to create a draft with two to six terms. The vertical order
is the planning order; term labels are free text and are not checked against an
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

The draft and results stay in memory only. Editing any term clears prior
results. Term labels stay on the page and are not sent to the API. Assumed
outcomes are sent as completed-course evidence to later term checks; they are
not saved in browser storage.
Course offerings, timetable conflicts, academic calendar dates, degree
requirements, graduation feasibility, and registration are not checked. The
catalog sample is incomplete, so manual review results and unavailable rule
data remain visible.
