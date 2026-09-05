# spring-boot-sample

A sample Spring Boot REST API — built as an end-to-end learning project covering scaffolding, testing, containerizing, and CI, alongside a full agentic-AI foundation: Claude Code tooling (skills, subagents, hooks, MCP, `CLAUDE.md`), a hand-rolled Claude API tool-calling layer inside the app itself, and a standalone Claude Agent SDK example.

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
| GET | `/api/items/search?name={query}` | Case-insensitive substring search by name |
| POST | `/api/items` | Create an item (`{"name": "...", "description": "..."}`) |
| PUT | `/api/items/{id}` | Update an item |
| DELETE | `/api/items/{id}` | Delete an item |
| POST | `/api/ai/ask` | Natural-language query (`{"query": "find widget"}`) — a mocked-by-default LLM tool-calling loop; see below |

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

## AI features (the app itself, not just the tooling)

`POST /api/ai/ask` runs a real tool-calling agent loop against the `Item` data — guardrailed (prompt-injection checks, per-client rate limiting), traced (structured `ai_trace` logging), and eval-tested:

```bash
curl -X POST http://localhost:8080/api/ai/ask -H "Content-Type: application/json" -d '{"query":"find widget"}'
```

Runs fully mocked by default (`app.ai.provider=mock` — no API key, no cost, deterministic, safe for CI). Set `app.ai.provider=anthropic` + `ANTHROPIC_API_KEY` to use the real Anthropic API instead. See [`docs/agentic-concepts/agentic-application-layer.md`](docs/agentic-concepts/agentic-application-layer.md).

A separate, standalone example in [`agent-sdk-example/`](agent-sdk-example/) uses the **Claude Agent SDK** (a different thing from the Claude API — see [`docs/agentic-concepts/agent-sdk.md`](docs/agentic-concepts/agent-sdk.md)) to build a Node.js agent with a custom tool that calls this app's own `/api/items` endpoint — verified working live, with real cost.

## Claude Code setup

This repo doubles as a working foundation for the full agentic-AI ecosystem — see **[`docs/agentic-concepts/`](docs/agentic-concepts/README.md)** for the complete write-up, covering all four of Anthropic's certification domains (Claude Code, Claude API, Claude Agent SDK, MCP — see [`certification-alignment.md`](docs/agentic-concepts/certification-alignment.md)): `CLAUDE.md` at four levels, skills (including a deliberate name-collision demo), two subagents run through real sequential and parallel review cycles, two tested hooks, a working MCP server connection, a scaffolded plugin example, the app-level AI feature above, and the Agent SDK example.

Quick pointers:

- [`CLAUDE.md`](CLAUDE.md) — architecture and commands for future Claude Code sessions.
- [`.claude/skills/spring-boot-sample-dev/SKILL.md`](.claude/skills/spring-boot-sample-dev/SKILL.md) — build/test/run/Docker recipes as an invocable skill.
- [`.claude/agents/java-spring-dev.md`](.claude/agents/java-spring-dev.md) / [`spring-code-reviewer.md`](.claude/agents/spring-code-reviewer.md) — implementer + read-only reviewer subagents.
- [`.claude/settings.json`](.claude/settings.json) + [`.claude/hooks/`](.claude/hooks/) — two `PostToolUse` hooks (edit logging, compile-on-save).
- [`.mcp.json`](.mcp.json) — a project-scoped filesystem MCP server.

See `CLAUDE_PLAYGROUND.md` in the parent workspace folder for how this fits into a broader user/workspace/project layering.

## Troubleshooting

See [`ISSUES_AND_FIXES.md`](ISSUES_AND_FIXES.md) for problems hit while setting this project up (Spring Boot version compatibility, Spring Boot 4's Jackson 3 migration, Docker/WSL2, GitHub repo access) and how each was resolved.

## Open work

Tracked as actual [GitHub Issues](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues), not just prose in a log file:

- [#1](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/1) Verify Docker build/run locally (WSL2 not yet enabled)
- [#2](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/2) Verify `AnthropicLlmClient` against the real Anthropic API
- [#3](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/3) `ToolSpec` is Anthropic-shaped, not provider-neutral
- [#4](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/4) CI: migrate off deprecated `actions/checkout@v4`/`setup-java@v4`
