# SUMA Tracking Tool

Enterprise ITSM platform: tickets and SLA management for client projects, workforce attendance and break monitoring with
in-app inactivity prompts, automated monthly attendance reports to Project Leads, and login security / IP activity monitoring.

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, TypeScript, React Router, Tailwind CSS, shadcn/ui-style components (Radix), Motion, Recharts, React Hook Form + Zod, Axios |
| Backend | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA / Hibernate, Bean Validation, springdoc OpenAPI, ShedLock |
| Database | PostgreSQL 16, Flyway migrations |
| Tests | JUnit 5, Mockito, Spring Boot Test + MockMvc, GreenMail, Vitest + React Testing Library, Playwright |
| Delivery | Docker, Docker Compose, GitHub Actions |

## Quick start (local development)

Prerequisites: Java 21, Maven 3.9, Node 22, PostgreSQL 16.

```bash
# 1. database
createuser -P suma            # password: suma (dev only)
createdb -O suma suma
createdb -O suma suma_test    # for integration tests

# 2. backend (http://localhost:8080, Swagger at /swagger-ui.html)
cd backend
SUMA_DEMO_DATA=true mvn spring-boot:run      # demo data is optional

# 3. frontend (http://localhost:5173, proxies /api to the backend)
cd frontend && npm install && npm run dev
```

Sign in as `admin` / `Admin@12345` (development default — set `SUMA_ADMIN_PASSWORD` everywhere else).
With demo data, other accounts use password `Password@123`: `manager`, `tl.arjun`, `tl.sara`, `lead.kiran` (Project Lead), `l1.neha`, `l2.vikram`, `l3.omar`.

## Docker Compose

```bash
cp .env.example .env    # then replace every secret
docker compose up --build
```

App on http://localhost:8081, captured emails (Mailpit) on http://localhost:8025. See `docs/deployment/DEPLOYMENT.md`.

## Tests

```bash
cd backend && mvn verify                 # unit + integration tests (needs the suma_test database)
cd frontend && npm test && npm run build
cd e2e && npm install && npx playwright test --trace off   # needs backend (demo data, SUMA_LOGIN_RATE_MAX=1000) + vite dev server running
```

## Documentation

| Document | Contents |
|---|---|
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | Architecture, the 14 modules, assumptions, open business rules, roadmap |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | Tables, keys, indexes, duplicate-prevention rules, retention |
| [API_DOCUMENTATION.md](API_DOCUMENTATION.md) | Every REST endpoint with the permission it requires |
| [ROLE_PERMISSION_MATRIX.md](ROLE_PERMISSION_MATRIX.md) | Roles, permissions, Project Lead designation, data isolation |
| [SKILLS_AND_MCP.md](SKILLS_AND_MCP.md) | Tools, skills and MCP servers used to build this |
| [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) | Task-level status and test status |
| [OVERNIGHT_DEVELOPMENT_REPORT.md](OVERNIGHT_DEVELOPMENT_REPORT.md) | Handover: what was built and verified, limitations, next steps |
| [docs/deployment/DEPLOYMENT.md](docs/deployment/DEPLOYMENT.md) | Configuration reference and production checklist |
