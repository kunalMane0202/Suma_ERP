# Skills, MCP servers and tools used

This records what was actually available in the build environment and how it was used. Nothing here is assumed to be
installed in other environments.

## Environment inspected at start

| Capability | Available | Used for |
|---|---|---|
| Java 21, Maven 3.9 | yes | backend build and tests |
| Node 22, npm | yes | frontend build, Vitest, Playwright runner |
| PostgreSQL 16 (local cluster) | yes (started manually) | development DB `suma` and test DB `suma_test` |
| Docker CLI + daemon | yes (daemon started manually) | building and running runtime images, validating nginx proxying and IP handling |
| Playwright library + Chromium (pre-installed) | yes | screenshots for visual review, layout overflow checks, E2E suite (`@playwright/test` 1.63 pinned to the pre-installed browser build) |
| GitHub MCP tools | listed (deferred) | not needed: work is committed and pushed with git to the session branch; no PR was requested |
| Playwright MCP server | **not available** | replaced by the Playwright library driven from scripts |
| Database MCP for PostgreSQL | **not available** | `psql` used directly |

## Skills

| Skill | Used | How |
|---|---|---|
| `frontend-design` | yes | Read before building the UI. Applied its process: a compact token plan (palette, type, layout, one signature element) reviewed against the brief before coding; sentence-case copy; motion limited to page transitions, counters, dialogs, nav indicator and the shift-timeline reveal; reduced-motion respected; responsive to 390 px verified. |
| `security-review`, `code-review`, `simplify` | not yet run | Recommended next step on the full diff (see report). |
| `dataviz` | no | Charts follow a single restrained palette defined in `frontend/src/utils/chart.ts`. |
| Supabase / Lovable / Expo / Unity / game skills | not applicable | Out of scope for this stack; not used. |

## Verification tooling written in this project

- `backend` JUnit/Mockito/Spring Boot Test suites (`mvn verify`): unit tests for IP resolution, attendance arithmetic (including DST and month boundaries), SLA business time, rule conditions, rate limiting, CIDR validation, export safety; integration tests against PostgreSQL with MockMvc and GreenMail.
- `frontend` Vitest + React Testing Library: form validation, theme default, role-based navigation, timeline accessibility.
- `e2e` Playwright: the 20 required end-to-end flows plus dark mode and the ticket lifecycle.
- Ad-hoc Playwright scripts (kept out of the repo) to capture screenshots of every page for each role, in light/dark and at phone width, and to detect horizontal overflow.
