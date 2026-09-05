# spring-boot-sample

A sample Spring Boot REST API — built as an end-to-end learning project covering scaffolding, testing, containerizing, and CI, alongside a project-scoped Claude Code setup (skills, subagents, `CLAUDE.md`).

## Stack

- Spring Boot 4.1.1 / Java 21
- Spring Data JPA + H2 (in-memory)
- Maven (via the included wrapper — no local Maven install needed)
- Docker (multi-stage build)
- GitHub Actions CI

## Prerequisites

- JDK 17+ (developed against Oracle JDK 21)
- Docker, if you want to build/run the container image

## Getting started

```bash
./mvnw clean package -DskipTests    # build
./mvnw test                         # run tests
./mvnw spring-boot:run               # run (foreground)
# or
java -jar target/spring-boot-sample-0.0.1-SNAPSHOT.jar
```

The app listens on `http://localhost:8080`.

## API

| Method | Path | Description |
|---|---|---|
| GET | `/api/hello` | Sanity-check endpoint |
| GET | `/api/items` | List all items |
| GET | `/api/items/{id}` | Get one item |
| POST | `/api/items` | Create an item (`{"name": "...", "description": "..."}`) |
| PUT | `/api/items/{id}` | Update an item |
| DELETE | `/api/items/{id}` | Delete an item |

```bash
curl http://localhost:8080/api/hello
curl -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"Widget","description":"A sample widget"}'
curl http://localhost:8080/api/items
```

Data lives in an in-memory H2 database and resets on every restart. Browse it live at `/h2-console` (JDBC URL `jdbc:h2:mem:sampledb`, user `sa`, no password).

## Docker

```bash
docker build -t spring-boot-sample .
docker run --rm -p 8080:8080 spring-boot-sample
```

## CI

`.github/workflows/ci.yml` runs on every push/PR to `main`: builds, runs the test suite, and builds the Docker image.

## Claude Code setup

This repo carries project-scoped Claude Code configuration:

- [`CLAUDE.md`](CLAUDE.md) — architecture and commands for future Claude Code sessions.
- [`.claude/skills/spring-boot-sample-dev/SKILL.md`](.claude/skills/spring-boot-sample-dev/SKILL.md) — build/test/run/Docker recipes as an invocable skill.
- [`.claude/agents/java-spring-dev.md`](.claude/agents/java-spring-dev.md) — a subagent scoped to this repo's Java/Spring conventions.

See `CLAUDE_PLAYGROUND.md` in the parent workspace folder for how this fits into a broader user/workspace/project layering.

## Troubleshooting

See [`ISSUES_AND_FIXES.md`](ISSUES_AND_FIXES.md) for problems hit while setting this project up (Spring Boot version compatibility, Spring Boot 4's Jackson 3 migration, Docker/WSL2, GitHub repo access) and how each was resolved.
