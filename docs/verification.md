# Milestone 1 verification

Verification began September 15, 2026 (America/New_York) and was updated September 16, 2026.

## Passed

- `npm.cmd run build`: Vue/Vite production bundle built locally (Node 24).
- `backend/mvnw.cmd test` with the installed Java 21: one integration test passed, zero failures/errors/skips. Wrapper downloaded Maven successfully without a system Maven installation.
- `docker compose up --build -d`: both images built and both services started. Backend Docker build ran `mvn verify`; frontend Docker build ran `npm ci` and the production build.
- Direct `GET http://localhost:8080/api/status`: returned the expected application status, milestone 1, and `catalogReady: false`.
- Proxied `GET http://localhost:5173/api/status`: returned the same JSON through Nginx to Spring Boot.
- `GET http://localhost:5173/about`: HTTP 200, confirming the server supports Vue Router deep-link fallback.
- Stopping the backend produced HTTP 502 from the frontend API proxy; restarting it restored the expected API response.

The containers are left running for the user to inspect. Stop with `docker compose down`.

## Still needs verification

- Vite development server hot reload and its proxy in a live local development session; production Nginx proxy was exercised.
- A fresh-machine setup beyond this Windows/Docker Desktop environment.

## Not implemented or tested yet

Curriculum import/review, prerequisite alternatives, minimum grades, corequisites, credits, course moves, unschedulable plans, local-storage profiles, alternative plans, graph rendering, Redis, and AWS deployment. Their acceptance tests belong to the milestones that introduce those features.

Sandbox file access initially prevented the local Vite build, and network restrictions prevented dependency downloads; rerunning with approved external access succeeded. These were environment restrictions, not source-code build failures.

## September 16: standalone Neo4j container

- Added and started Neo4j Community `2026.08.1` with localhost-only HTTP/Bolt ports and a named data volume.
- Authenticated `cypher-shell` query `RETURN 1 AS connected;` returned `1`.
- `GET http://localhost:7474/browser/` returned HTTP 200.
- Interactive browser login and persistence across container replacement have not been exercised. No course data was created, and Spring Boot is not connected yet.

## September 16: application setup completed

This supersedes the standalone-container connection status above.

- Connected Spring Boot's managed Neo4j driver using environment-configurable URI and credentials.
- Docker builds passed, including three backend tests: API contract, successful database query/session cleanup, and database failure isolation with a safe HTTP 503 response. Database controller tests mock the driver; live checks below exercise the actual database.
- The PowerShell `dev.ps1 up` helper built the stack and waited until all three services were healthy.
- `dev.ps1 check` passed through Nginx to Spring Boot and performed a real authenticated Neo4j query.
- Stopped Neo4j: database status returned HTTP 503 while application status remained UP. Restarted Neo4j and confirmed the end-to-end check passed again.
- Built the final frontend changes successfully. The page now checks API and database independently and disables retries until both checks finish.
- Interactive browser verification passed: the overview displayed both **Backend connected** and **Neo4j connected**.
- Navigating to **About the project** and refreshing kept the About page visible, confirming client navigation and direct-route fallback in a browser.
- Basic narrow-screen verification passed in browser device emulation: content and controls remained usable without an observed layout problem.
- Basic keyboard verification passed: the user could tab through the navigation and retry control with a visible focus indicator.
- Stopping the backend and retrying displayed **Connection unavailable** and **Database status unknown**. Restarting the backend and retrying restored both connected states.
- Still unverified: local Vite hot reload and a fresh-machine setup. Catalog, planning, Redis, and AWS remain unimplemented.
