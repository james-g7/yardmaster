# Yardmaster

Yardmaster is an open-source AI gateway. It sits between your projects and AI
providers, routes each call to the right model and key, keeps every key inside
its usage rules, and never lets provider keys leave it.

Callers talk to it with OpenAI- or Anthropic-compatible APIs and a signed JWT.
Behind it you set up **labels** (what a caller asks for), **priority pools** of
models and keys, and **usage rules** for each key. Credits and balances are
handled by a separate Credits Service over gRPC.

> **Status:** early. Step 1 (foundations) is in place: the service starts,
> runs its migrations on SQLite or PostgreSQL, and reports ready when the
> database and Valkey answer. No proxying yet.

## Run it

```bash
docker compose -f deploy/docker-compose.yml up --build
curl localhost:8080/readyz
```

This runs Yardmaster with SQLite on a volume, plus Valkey. Only the public API
port (8080) is published; the admin port (9090) is private.

For several replicas behind a load balancer, set
`SPRING_PROFILES_ACTIVE=postgres` and the `POSTGRES_*` variables so they share
one database.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `YARDMASTER_PUBLIC_PORT` | `8080` | Caller API and health checks |
| `YARDMASTER_ADMIN_PORT` | `9090` | Admin panel, admin API and metrics; never public |
| `YARDMASTER_DATA_DIR` | `./data` | Where the SQLite file lives |
| `VALKEY_HOST` / `VALKEY_PORT` / `VALKEY_PASSWORD` | `localhost` / `6379` / none | Shared counters |
| `SPRING_PROFILES_ACTIVE` | none | `postgres` to use PostgreSQL |
| `POSTGRES_HOST` / `_PORT` / `_DB` / `_USER` / `_PASSWORD` | `localhost` / `5432` / `yardmaster` / `yardmaster` / none | PostgreSQL connection |

## Stack

Java 21, Spring Boot, Maven, Flyway, SQLite or PostgreSQL, Valkey,
Thymeleaf for the admin panel, gRPC with mutual TLS to the credits service,
one container image with Docker Compose and a Helm chart.

## Layout

```
pom.xml
src/main/java/yardmaster/     gateway code
src/main/resources/
  db/migration/               Flyway migrations (SQLite + PostgreSQL)
  templates/                  Thymeleaf admin pages
  presets/                    rule preset YAML files
proto/credits.proto           credits service contract
stand-in-credits/             stand-in credits service (step 8)
deploy/docker-compose.yml
deploy/helm/yardmaster/       Helm chart (step 11)
```

## Build steps

Each step ends with a check that must pass before moving on.

- [x] **1. Foundations:** Maven, Spring Boot, Flyway on SQLite and PostgreSQL, Valkey, CI, Docker Compose. *Done when* the empty service starts in Docker.
- [ ] **2. Basic proxy:** OpenAI-compatible chat to one provider with streaming; encrypted keys; usage records. *Done when* an OpenAI SDK gets a streamed reply.
- [ ] **3. Signed tokens:** verification and trusted issuers. *Done when* forged, edited or expired tokens are refused.
- [ ] **4. Labels and pools:** priority pools, per-request choice, ability filtering, label rules, cool-down, 503 and fallback. *Done when* overflow moves to pool 2 and back.
- [ ] **5. Usage rules and caller limits:** Valkey counters, presets, per-label concurrency. *Done when* a load test across two replicas never breaks a key's rules.
- [ ] **6. Providers and formats:** Anthropic, Google and local adapters; the Anthropic endpoint; tools, images, thinking, caching, structured output, token counting. *Done when* Claude Code and an OpenAI SDK both work through one label.
- [ ] **7. Preferences, tags and embeddings.** *Done when* a required preference is honoured or fails with 503.
- [ ] **8. Credits hook:** gRPC contract, stand-in, holds, Release, mutual TLS. *Done when* every call goes through Authorise and Record, and a refusal stops it.
- [ ] **9. Admin panel and API:** pages, admins with roles, MFA, audit log. *Done when* a label with two pools is set up entirely in the browser.
- [ ] **10. Operations:** metrics, tracing, webhooks, config export and import. *Done when* a key failure triggers a webhook and a config round-trips cleanly.
- [ ] **11. Packaging:** final Compose file, Helm chart, setup docs. *Done when* a fresh machine runs Yardmaster with one command.
- [ ] **12. First real test:** an agent playground with free-tier keys and a local model. *Success:* every agent is served and no key breaks its rules.

## Branches and pull requests

Work happens on milestone branches named after the build step, such as
`m2-basic-proxy`, which merge into `main` when the step's check passes.

`main` and every milestone branch (`m[0-9]*`) are protected:

- Changes arrive only through pull requests; no direct pushes, force pushes
  or deletion, and no admin bypass.
- CI must pass: `build`, `proto` and `image`.
- The Claude review must approve. It runs on every pull request and either
  approves or requests changes. New commits dismiss an earlier approval, so
  each push is reviewed again.

## Design docs

- [Build spec](https://claude.ai/artifact/GeJAQciH8bNXchwL5ikjHs)
- [Concept](https://claude.ai/artifact/EVcw5e6PxcZdz6mth3xPvD)
- [Credits Service](https://claude.ai/artifact/8BVkeX8cu3j5hiucXxWK9K)

## License

MIT
