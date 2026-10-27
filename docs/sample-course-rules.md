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
The sample catalog now includes all five dependency courses, and the rule
contains an AND of two OR groups with year-specific course references. Each
alternative permits prior or concurrent completion and records no specified
minimum grade. The catalog's seat-reservation note remains an `unsupported` condition. It
must not be interpreted as excluding every non-CS major.

The record remains `NEEDS_REVIEW` because section/program seat reservations
still require manual review. Do not copy the minor's admission
grade thresholds into this course's enrollment rule.

## Dependency metadata review — October 26, 2026

All three subject pages identify the 2026–2027 catalog. Each was retrieved
at `2026-10-27T01:45:42Z` (October 26 in New York). Reviewed metadata:

| Course | Title | Credits | Source |
| --- | --- | --- | --- |
| E 115 | Introduction to Computing Environments | 1 | [Engineering](https://catalog.ncsu.edu/course-descriptions/e/) |
| COS 100 | Science of Change | 2 | [College of Sciences](https://catalog.ncsu.edu/course-descriptions/cos/) |
| MA 121 | Elements of Calculus | 3 | [Mathematics](https://catalog.ncsu.edu/course-descriptions/ma/) |
| MA 131 | Calculus for Life and Management Sciences A | 3 | [Mathematics](https://catalog.ncsu.edu/course-descriptions/ma/) |
| MA 141 | Calculus I | 4 | [Mathematics](https://catalog.ncsu.edu/course-descriptions/ma/) |

SHA-256 of downloaded response bytes:

- Engineering: `5e0d246bc07b44be75d01bc7a2ee52bdb0f0d457f55bf8f39654b44315adb3bb`
- College of Sciences: `46d64c066104cd2563b32dd8e639b0108a2c5f6547c3f93bb4caa14aae109cb8`
- Mathematics: `2465fb329fdb08a788b73a3e4ce9a4785d610d1b063f186dffbbb284671b9615`

The course JSON now supports an optional per-course `source` object with URL,
SHA-256, and retrieval time. An explicit source requires all three fields.
Older records inherit the existing root URL/checksum; their retrieval time is
unknown and remains null. The repository resolves each course's source before
writing it, and the course API exposes the resolved URL/checksum plus the
additive `retrievedAt` field. Existing records without this database property
remain readable.

This review covers identity, title, and credit hours only. It does not review
the five courses' own enrollment rules, placement exams, credit exclusions,
or program restrictions. Those courses still return 404 from the rule endpoint
until their rules are modeled. Their inclusion as CSC 116 alternatives does
not establish that a student can enroll in them.

## Import and API behavior

The existing `--packplan.catalog.import-draft-rules=true` opt-in now imports
all three sample rules, including CSC 216. Its legacy name and resource paths
are unchanged. Use `--packplan.catalog.import-courses=true` as well when the
three owning courses have not been imported. Normal startup writes nothing.
Imports are sequential; an error fails startup but does not roll back earlier
rules already saved during that startup.

`GET /api/catalog/courses/{courseId}/rule` returns each stored record with its
review status, source evidence, and expression. In particular CSC 116 returns
HTTP 200 with `NEEDS_REVIEW`, both OR groups, and the unsupported seat check; a missing stored rule
returns 404. Review status and unsupported checks remain separate from catalog
readiness, which is still false. The [eligibility evaluator](eligibility.md)
checks these stored rules while preserving manual-review conditions.

## Initial three-rule verification — October 26, 2026

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

## Eight-course verification — October 26, 2026

All 53 backend tests passed after adding the five dependencies and replacing
CSC 116's corequisite placeholder. Tests check exact AND/OR structure, timing,
unspecified grade thresholds, retained manual seat review, rejection of every
missing alternative, explicit-source validation, legacy provenance fallback,
and metadata persistence/API responses across all five new courses.

The extended live integration check passed against local Neo4j. After each
of two imports and a startup with imports disabled, it verified all eight
courses' metadata and source evidence, one course node per ID, all three
rules and their MVC responses, and one rule/link per owning course. It also
checked the course-list MVC response against the real repository. The local
database now holds the eight-course sample and structured CSC 116 rule.
The same sequential-import and MockMvc limits apply; application images were
not rebuilt, and Neo4j remains running. Catalog readiness remains false.
