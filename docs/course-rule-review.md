# CSC 216 draft rule review

Draft: `backend/src/main/resources/catalog/drafts/csc-216-rule.json`.

The official [CSC course page](https://catalog.ncsu.edu/course-descriptions/csc/)
was retrieved at 2026-10-18T02:58:19Z and identified the 2026-2027 catalog.
The JSON checksum is SHA-256 of the downloaded HTML response bytes. Original
rule text preserves the wording with HTML removed and nonbreaking spaces
normalized to ordinary spaces.

The draft requires CSC 116 completed beforehand with at least C, AND CSC 217
completed beforehand or taken concurrently. No minimum grade is specified
for CSC 217 in this course entry; this does not imply failing grades qualify.

## Why NEEDS_REVIEW

The [catalog definition](https://catalog.ncsu.edu/course-descriptions/) allows
previous completion of corequisites. This is the basis for the draft timing.
The [registrar requirement-groups guide](https://registrar.ncsu.edu/wp-content/uploads/sites/486/2025/07/Requirement-Groups.pdf)
instead describes corequisites as concurrent. Confirm how this distinction
applies to CSC 216/217 before marking the rule reviewed.

CSC 116 metadata is now included in the bundled course sample: Introduction
to Computing - Java, 3 credits, in the 2026-2027 catalog. Its identity, title,
and credits were checked against the CSC source page and the local snapshot,
whose checksum matches the bundled source checksum. This metadata review
does not cover CSC 116's own enrollment rules, which remain unmodeled.
The CSC 216 draft's course references now exist in the bundled sample;
the corequisite timing review above remains unresolved.

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
to import the CSC 216 draft with its `NEEDS_REVIEW` status. The equivalent
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

No API exposes the rule yet. It does not establish enrollment eligibility
or change catalog readiness.

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
