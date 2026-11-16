# Notes for coding agents and contributors

The build spec is the source of truth:
https://claude.ai/artifact/GeJAQciH8bNXchwL5ikjHs

## Rules

- Keep dependencies minimal and well known.
- Never store prompts or replies, in the database or in logs.
- Never log or return provider keys.
- Don't add features beyond the spec without asking.
- Every database migration must run unchanged on both SQLite and PostgreSQL.
  Both are tested in CI (`SqliteStartupIT`, `PostgresStartupIT`).
- The admin port (9090) is never exposed publicly.

## Build steps

Work through the steps in the README in order. Each ends with a check that
must pass before moving on. Tests must never call a real AI provider.

## Commands

```bash
mvn verify                                       # unit + integration tests (needs Docker)
mvn spring-boot:run                              # run locally (needs Valkey on localhost:6379)
docker compose -f deploy/docker-compose.yml up --build
```
