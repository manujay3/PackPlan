# Catalog source inventory — not yet verified

The workspace already contained `*-source.html` snapshots and two helper scripts. Both program snapshots display **University Catalog 2026–2027**. Retrieval timestamps and original checksums were not recorded, so these files must not yet be treated as reviewed curriculum data.

| Intended program | Curriculum target | Official source | Status |
| --- | --- | --- | --- |
| Computer Science BS | 2026–2027 | https://catalog.ncsu.edu/undergraduate/engineering/computer-science/computer-science-bs/ | Raw snapshot; review/import pending |
| Economics BA | 2026–2027 | https://catalog.ncsu.edu/undergraduate/management/economics/economics-ba/ | Raw snapshot; review/import pending |

Course pages use `https://catalog.ncsu.edu/course-descriptions/{subject}/`, including `csc`, `ec`, `ma`, and `st`.

The existing `scripts/fetch-catalog.mjs` downloads the current catalog, which may change year. It is not a year-pinned or verified importer. Running it overwrites local snapshots. `scripts/inspect-catalog.mjs` is a rough HTML inspection helper: for example, it reduces credit ranges to a single number, and does not evaluate prerequisite logic. Do not feed its output directly into graduation checks.

Before importing, record the actual curriculum year, exact source URL, retrieval time, source checksum, original requirement text, normalized expression, review status, and unsupported conditions. Check program totals, electives, general education, minimum grades, AND/OR groups, and corequisites against the source. If the source year differs, stop and obtain the intended archive rather than relabeling it.

No course rules are included in the milestone 1 API. No requirements or prerequisites have been invented. Typical offering terms will be labeled assumptions, not guarantees of registration availability.
