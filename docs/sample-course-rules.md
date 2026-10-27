# CSC 116 and CSC 217 enrollment rules

## Source review — October 26, 2026

The official [CSC course catalog](https://catalog.ncsu.edu/course-descriptions/csc/)
identifies the 2026–2027 catalog. The response was retrieved at
`2026-10-27T01:42:05Z` (October 26 in New York). Its SHA-256 is
`d3a77e2e7f150f54d70f2865e657a74884460f7385c147029d71ca91ae5d7455`,
matching the earlier source used for the sample course metadata and CSC 216
rule. The new records retain this retrieval time, checksum, URL, catalog year,
and enrollment wording with HTML removed and whitespace normalized. The
supplementary sources below support interpretation; they are not the source
described by these provenance fields.

## CSC 217 — REVIEWED

The catalog requires CSC 116 with C or better and CSC 216 as a corequisite.
The expression is an AND of CSC 116 completed beforehand with minimum C and
CSC 216 completed beforehand or concurrently with no specified minimum grade.
The [CSC advising FAQ](https://my.csc.ncsu.edu/undergrad-advising/advising-faq/)
defines corequisites as permitting prior completion or concurrent enrollment.
The [minor advising pathways](https://my.csc.ncsu.edu/undergrad-advising/computer-programming-minor/)
explicitly say the lecture and lab are paired on the first attempt, and a
passed lecture or lab need not be repeated if the other was not passed. These
sources support prior completion in either direction, including CSC 216 before
a subsequent CSC 217 attempt. They were checked on October 26.

`NOT_SPECIFIED` records the absence of a catalog enrollment grade threshold
for CSC 216; it does not mean failed or withdrawn attempts earn credit.
Program graduation thresholds and registration exceptions are outside this
enrollment rule. The paired rules form a legitimate concurrent dependency,
not two prerequisites that must each be completed first.

## CSC 116 — NEEDS_REVIEW

The catalog lists alternatives in computing environments and calculus. The
minor advising pathways identify the grouping as (E 115 or COS 100) AND
(MA 121 or MA 131 or MA 141), with prior or concurrent completion permitted.
The sample catalog contains none of these five course records. Rather than
create dangling references or remove the condition, the rule preserves the
entire corequisite clause as an `unsupported` expression with an explanation.
The catalog's seat-reservation note is a second `unsupported` condition. It
must not be interpreted as excluding every non-CS major.

The record remains `NEEDS_REVIEW`. Next, import reviewed metadata for the five
dependencies with their own source provenance, replace the unsupported
corequisite clause with nested AND/OR course expressions, and retain manual
review for section/program seat reservations. Do not copy the minor's admission
grade thresholds into this course's enrollment rule.

## Import and API behavior

The existing `--packplan.catalog.import-draft-rules=true` opt-in now imports
all three sample rules, including CSC 216. Its legacy name and resource paths
are unchanged. Use `--packplan.catalog.import-courses=true` as well when the
three owning courses have not been imported. Normal startup writes nothing.
Imports are sequential; an error fails startup but does not roll back earlier
rules already saved during that startup.

`GET /api/catalog/courses/{courseId}/rule` returns each stored record with its
review status, source evidence, and expression. In particular CSC 116 returns
HTTP 200 with `NEEDS_REVIEW` and both unsupported checks; a missing stored rule
returns 404. Review status and unsupported checks remain separate from catalog
readiness, which is still false. No eligibility evaluator exists yet.

## Verification — October 26, 2026

All 42 backend tests passed with Java 21. The new tests check CSC 217's exact
grade/timing expression, CSC 116's unsupported clauses and retained review
status, the three-rule opt-in import order, and full JSON API responses for
all three records. The API tests use a mocked repository.

The explicit `CourseRuleImportIT` also passed against local Neo4j. It imported
all three rules twice, then started with imports disabled. On every pass it
compared each stored rule to its bundled JSON, checked its complete MVC API
response, and confirmed exactly one rule node and one `HAS_RULE` link per
course. The local database now contains all three records. This checks
sequential imports and MockMvc with a live repository, not concurrent imports,
a running HTTP server, or the frontend proxy. Application images were not
rebuilt; Neo4j remains running.
