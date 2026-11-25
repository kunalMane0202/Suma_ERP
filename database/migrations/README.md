Flyway migrations live with the backend so they ship inside the application jar:
`backend/src/main/resources/db/migration` (V1 core identity … V7 reference data). They run automatically at startup.
See DATABASE_SCHEMA.md at the repository root for the resulting schema.
