# CSC 216 rule review

Rule (legacy draft resource path): `backend/src/main/resources/catalog/drafts/csc-216-rule.json`.

The official [CSC course page](https://catalog.ncsu.edu/course-descriptions/csc/)
was retrieved at 2026-10-18T02:58:19Z and identified the 2026-2027 catalog.
The JSON checksum is SHA-256 of the downloaded HTML response bytes. Original
rule text preserves the wording with HTML removed and nonbreaking spaces
normalized to ordinary spaces.

The reviewed rule requires CSC 116 completed beforehand with at least C, AND CSC 217
completed beforehand or taken concurrently. No minimum grade is specified
for CSC 217 in this course entry; this does not imply failing grades qualify.

## Timing review resolved — October 21, 2026

The [Fall 2026 CSC 216 syllabus](https://courses.csc.ncsu.edu/csc216/syllabi/F26_CSC216_Syllabus/)
explicitly exempts students who already have CSC 217 credit from enrolling in
the lab again. Its lab section also confirms that students who have passed
CSC 217 need not repeat it. This course-specific evidence resolves the timing
question for the 2026-2027 sample: CSC 217 may be completed before CSC 216 or
taken concurrently. The normalized expression is unchanged; its review status
is now `REVIEWED`.

The [CSC advising FAQ](https://my.csc.ncsu.edu/undergrad-advising/advising-faq/)
also permits prior completion or concurrent enrollment for corequisites.
The [registrar requirement-groups guide](https://registrar.ncsu.edu/wp-content/uploads/sites/486/2025/07/Requirement-Groups.pdf)
gives a generic concurrent-only description; the current course syllabus
provides the explicit prior-credit exception for CSC 216/217. All three sources
and the [2026-2027 CSC catalog entry](https://catalog.ncsu.edu/course-descriptions/csc/)
were checked on October 21, 2026. The rule retains the original catalog retrieval
time, checksum, and wording; these fields describe that original source,
not the supplementary review sources linked here.

`NOT_SPECIFIED` for the CSC 217 minimum grade means the enrollment entry does
not state a threshold. Prior completion must represent earned credit, not a
failed or withdrawn attempt. The advising FAQ separately lists a C minimum in
CSC 217 for graduation; that program requirement is not modeled by this
course enrollment rule. Transfer equivalencies, credit-by-exam, overrides,
and other exceptions remain outside this sample.

CSC 116 metadata is now included in the bundled course sample: Introduction
to Computing - Java, 3 credits, in the 2026-2027 catalog. Its identity, title,
and credits were checked against the CSC source page and the local snapshot,
whose checksum matches the bundled source checksum. This metadata review
does not cover CSC 116's own enrollment rules, which remain unmodeled.
The CSC 216 rule's course references now exist in the bundled sample.

`CourseRuleLoader.load(resource, catalog)` reads a draft and validates its
course references before returning it, preserving its review status. Tests
exercise this path with the bundled CSC 216 draft and incomplete catalogs.

`CourseRuleImportService.importRule(resource, catalog)` explicitly loads,
validates, and saves one rule through `CourseRuleRepository`, preserving its
review status. Invalid JSON or missing course references prevent the save.
The supplied catalog is the reference-validation input; it is not imported
by this method. The repository requires the owning course and matching year
to already exist in Neo4j. Referenced course existence in the live database
is not checked by this service.

## Explicit local import

`packplan.catalog.import-draft-rules` defaults to false. Enable it explicitly
to import the CSC 216 rule with its `REVIEWED` status. The legacy flag and
resource path retain their draft names for compatibility. The equivalent
environment variable is `PACKPLAN_CATALOG_IMPORT_DRAFT_RULES`.

With Neo4j running, from the backend directory in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--packplan.catalog.import-courses=true --packplan.catalog.import-draft-rules=true'
```

When both flags are enabled, course imports run before draft rule imports.
The draft flag alone does not import courses; the owning course must already
exist with the matching catalog year. Stop and restart without import flags
afterward, and unset any import environment variables. Import errors fail
startup instead of silently skipping a rule.

`GET /api/catalog/courses/{courseId}/rule` exposes the persisted rule, including
its nested expression, review status, and source provenance. It returns HTTP
404 with an empty body when no rule is stored for that course ID, including
unknown IDs. Both responses use `Cache-Control: no-store`. A missing rule does
not imply unrestricted enrollment. Reading this endpoint does not import data,
establish enrollment eligibility, or change catalog readiness.

## Live verification

On October 17, the explicit import was verified against local Neo4j using
`CourseRuleImportIT`. The test starts Spring without a web server, imports
courses and the draft twice, then starts with imports disabled. Each pass
reads the complete rule through the repository and compares it to the JSON,
including its expression, provenance, and `NEEDS_REVIEW` status. Direct queries
confirmed exactly one rule node and one `HAS_RULE` relationship after each pass.
The draft and three sample courses now exist in the local database.

To repeat this database-writing check explicitly from `backend` with Java 21:

```powershell
.\mvnw.cmd -B '-Dtest=CourseRuleImportIT' '-Dpackplan.live-import=true' test
```

Ordinary `mvn test` excludes the `IT` class; it also requires the explicit
system property above. This checks sequential imports, not concurrent import
uniqueness. Existing Docker application services were left running and
confirmed healthy; their images were not rebuilt for this check.

## Reviewed rule verification — October 26, 2026

All 36 backend tests passed using Java 21 on macOS. Mockito's agent attachment
required running the tests outside the filesystem sandbox.

With the local Compose Neo4j service healthy, `CourseRuleImportIT` passed with
`-Dtest=CourseRuleImportIT -Dpackplan.live-import=true`. It imported the sample
courses and reviewed rule twice, then started with imports disabled. Each pass
verified the complete rule against the bundled JSON, including `REVIEWED`
status, and confirmed exactly one rule node and one `HAS_RULE` relationship.
The local database now stores the reviewed rule. Neo4j was left running;
application images were not rebuilt. This verifies sequential imports only.

Catalog readiness remains false: other course rules and program requirements
are incomplete. Persisted rules are now exposed through the catalog API;
eligibility evaluation remains unimplemented.

The API addition passed all 38 backend tests on October 26. Its Spring MVC
tests compare the entire JSON response to the sample rule and check uncached
404 responses for a known course without a rule and an unknown course ID.
The live `CourseRuleImportIT` also passed after being extended to exercise the
controller through MockMvc with the real repository after each import/startup
pass. It verifies the complete response and cache header against the persisted
rule. This checks MVC handling with live Neo4j, not a running HTTP server or
the frontend proxy. Application images were not rebuilt.
