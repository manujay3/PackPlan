# PackPlan

A degree and semester planning project for NC State students. **Application setup is implemented: Vue communicates with Spring Boot, which connects to Neo4j.** Curriculum import and planning logic are next. This is an independent portfolio project, not an official advising or graduation audit tool.

## Run with Docker (PowerShell)

Start Docker Desktop using Linux containers. From PowerShell:

The helper finds Docker even when it is missing from `PATH`, builds the application, and waits for health checks:

```powershell
Set-Location C:\Users\manuj\Repos\PackPlan
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev.ps1 up
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev.ps1 check
```

Other helper actions: `status`, `logs`, and `down`. The execution-policy option applies only to this process. To use Docker directly instead:

```powershell
Set-Location C:\Users\manuj\Repos\PackPlan
docker compose up --build -d
docker compose logs backend
```

Open http://localhost:5173. Allow the backend a few seconds to start, then click **Check connection again** if necessary. Initial builds download dependencies and run the backend test.

```powershell
# Request through the frontend's reverse proxy:
Invoke-RestMethod http://localhost:5173/api/status
# Direct backend request:
Invoke-RestMethod http://localhost:8080/api/status
# Stop the application:
docker compose down
```

Expected API response:

```json
{"application":"PackPlan","status":"UP","milestone":1,"catalogReady":false}
```

Ports bind only to the local machine. These containers are for local development and demonstration.

## Local development with hot reload

### Neo4j configuration

Neo4j Community Edition is a separate Compose service. Spring Boot connects with the official Java driver, auto-configured by Spring Boot. No courses have been imported. `/api/status` checks the API itself; `/api/database/status` runs a read-only query and returns `{"status":"UP"}` or HTTP 503 with `{"status":"DOWN"}`. Driver details are not sent to clients. The overview displays these two checks separately.

Reviewed 2026-2027 catalog data currently includes Computer Science BS and Economics BA program metadata plus one small Computer Science requirement slice: CSC 216 and CSC 217, including their credits and minimum grade C. Prerequisites, corequisites, and all remaining degree requirements are not imported yet. Normal backend startup does not modify catalog data. To explicitly import or update the reviewed nodes and relationships during local development, start Neo4j and run:

```powershell
Set-Location C:\Users\manuj\Repos\PackPlan\backend
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--packplan.catalog.import-reviewed-data=true'
```

After startup completes, stop the process with Ctrl+C. Repeating the import updates the same graph rather than creating duplicates. With the backend running normally, `GET http://localhost:8080/api/catalog/programs` returns the imported programs. `catalogReady` remains `false` because the full program requirements have not been modeled or reviewed.

Compose waits for Neo4j authentication/query readiness, then backend database readiness, then starts the frontend. Health checks continue after startup; an unhealthy service is reported but is not automatically restarted. Configuration follows [Spring Boot's Neo4j documentation](https://docs.spring.io/spring-boot/3.5/reference/data/nosql.html#data.nosql.neo4j).

`backend/src/main/resources/application.properties` defaults to local demo settings. Set `NEO4J_URI`, `NEO4J_USERNAME`, and `NEO4J_PASSWORD` in the backend terminal to override them. Compose supplies `bolt://neo4j:7687` because containers use service names rather than your machine's `localhost`. Optional `.env` settings for Compose are illustrated in `.env.example`; a local Java process does not automatically load that file. Keep the default password for the already-created demo volume unless you also change the database password.

If PowerShell cannot find `docker`, add its installed directory for this terminal:

```powershell
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"
Set-Location C:\Users\manuj\Repos\PackPlan
docker compose up -d neo4j
docker compose logs neo4j
```

Open http://localhost:7474/browser/ and connect with:

- Connection URL: `bolt://localhost:7687`
- Username: `neo4j`
- Password: `packplan-local` (local demo only)

Run this in Neo4j Browser:

```cypher
RETURN 1 AS connected;
```

A result of `1` confirms a working database connection without creating any data. Port 7474 serves the browser; port 7687 handles database connections. Both ports are limited to this machine. The named volume `neo4j_data` keeps the database across container replacement and ordinary `docker compose down`. Do not add `--volumes` when stopping if you want to preserve that data.

The image version is pinned so normal startup does not silently upgrade the database. Initial authentication and volume configuration follow the [official Neo4j Docker instructions](https://neo4j.com/docs/operations-manual/current/docker/introduction/). Changing `NEO4J_AUTH` later does not change the password of an existing database.

### Frontend and backend

Stop the Compose application first so ports 8080 and 5173 are free. Java 21 is installed on this machine, but `java` initially resolves to Java 8. Set Java for the backend terminal:

Keep the database running for local development: run `docker compose stop frontend backend` followed by `docker compose up -d neo4j`.

```powershell
Set-Location C:\Users\manuj\Repos\PackPlan\backend
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd spring-boot:run
```

Use your actual JDK 21 installation path on another machine. Maven does not need a separate installation: the official Apache Maven wrapper downloads Maven 3.9.11 on first use. Internet access is required for initial dependency downloads.

In a second PowerShell terminal:

```powershell
Set-Location C:\Users\manuj\Repos\PackPlan\frontend
npm.cmd ci
npm.cmd run dev
```

Open http://localhost:5173. `npm.cmd` avoids PowerShell script execution-policy problems. Node 22.12+ or Node 24 is suitable; development here uses Node 24. Vite proxies `/api` to `localhost:8080`. Stop each local server with Ctrl+C.

## What to understand first

Follow one request through these files:

1. `frontend/src/views/HomeView.vue`: `fetch('/api/status')`, loading/success/error state, eight-second timeout, retry button.
2. `frontend/vite.config.js`: forwards development API requests to Java. In Docker, `frontend/nginx.conf` does the same and serves built assets, including Vue Router deep links.
3. `backend/src/main/java/com/packplan/StatusController.java`: maps the request to a typed Java record that Spring serializes as JSON. Responses disable caching so the connection check is live.

The browser uses one origin, so no permissive CORS configuration is needed. Vue Router is present for additional screens. There is no state-management framework yet because a single page needs only local reactive state. Java 21 and Spring Boot 3.5 are compatible ([Spring requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)); frontend setup follows [Vite](https://vite.dev/guide/).

## Reproducible milestone 1 demo

1. Start Compose and open the overview. Confirm **Backend connected**.
2. Open **About the project** and refresh that page to check direct routing.
3. Run `docker compose stop backend`, then click **Check connection again**. Expect **Connection unavailable** with instructions to retry.
4. Run `docker compose start backend`, wait for startup, and retry. Expect **Backend connected**.
5. Explain the request path and why a connected API does not imply a verified catalog.

## Tests

```powershell
Set-Location C:\Users\manuj\Repos\PackPlan\backend
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B test
Set-Location ..\frontend
npm.cmd run build
```

`StatusControllerTest` starts the Spring application context and checks the API contract, including `catalogReady: false` and cache prevention. `DatabaseStatusControllerTest` uses a mocked driver to test successful query/session cleanup and safe HTTP 503 responses without taking down the API. These tests need no live database. Docker builds run all tests; the helper's `check` action additionally exercises the real frontend proxy, Java API, and Neo4j. Frontend build checks compilation; it does not establish browser behavior. See `docs/verification.md` for checks actually performed.

## Curriculum scope and data

**Currently supported programs: none.** Target demonstration curriculum: **2026–2027 Computer Science BS and Economics BA**. The preexisting HTML snapshots identify that catalog year; academic rules have not yet been reviewed or imported. Source inventory and limitations are in `data/README.md`.

The intended model stores curriculum requirements by program and year rather than hardcoding major-specific logic. Adding a major should primarily mean importing reviewed requirements. Rules outside the evaluator's capabilities must remain visible as unresolved checks, not silently pass. A partial dataset cannot verify graduation eligibility.

## Milestones and acceptance checks

1. **Foundation (implemented):** Java 21/Spring Boot, Vue 3/JavaScript/Vue Router, connection feedback, Docker Compose.
2. **Catalog:** connect Neo4j; import a reviewed sample for both programs with year, source URL, original rule text, review status, and explicit unsupported rules. Preserve AND/OR expression structure rather than flattening prerequisites into edges.
3. **Requirements:** enter completed courses/grades, current enrollment, target graduation, and credit cap. Check mandatory courses, eligible elective choices, remaining credits, grade thresholds, and corequisites. Test AND/OR alternatives, repeated-course handling, and concurrent requirements.
4. **Planning:** schedule from reviewed rules and explicitly labeled offering assumptions. Current enrollment counts only in future scenarios that assume passing. Explain blockers; report unschedulable courses without an endless loop. Test credit caps, unavailable offerings, cycles, and impossible deadlines.
5. **Editing and comparison:** move/add/remove courses, revalidate the entire plan, update completion estimates, save two alternatives in local storage, and render readable prerequisites. Test moves that break prerequisites or corequisites and those that exceed a term's credit cap.
6. **Redis and AWS:** cache stable catalog queries with versioned keys/invalidation after imports; provide deployment setup after the main flow works. AWS deployment is not configured in milestone 1.

Electives will be selected by students from eligible options. There is no advanced elective optimizer planned for the MVP. Academic approval, transfer equivalency, exceptions, residency, and any unmodeled rules must be identified as needing manual review.

## Repository

- `backend/`: Java API, Maven wrapper, integration test, Dockerfile.
- `frontend/`: Vue UI, router, Vite development proxy, Nginx container.
- `data/`: existing raw catalog downloads and source documentation.
- `scripts/`: existing catalog download/inspection helpers; not a verified importer.
- `docs/`: milestone verification notes.
- `compose.yaml`: frontend, backend, and connected Neo4j with readiness checks; Redis is pending.

The project uses Git on the `main` branch. `.gitignore` excludes local agent state, dependency folders, build output, local environment files, editor upgrade artifacts, and unreviewed HTML downloads.
