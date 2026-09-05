# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

This project uses the Maven wrapper (no local Maven install required) — always invoke `mvnw.cmd` (Windows) / `./mvnw` (Unix) rather than a bare `mvn`.

```bash
# Build (compile + package into target/*.jar)
./mvnw clean package

# Build without running tests
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=SpringBootSampleApplicationTests

# Run a single test method
./mvnw test -Dtest=SpringBootSampleApplicationTests#contextLoads

# Run the app locally (foreground, Ctrl+C to stop)
./mvnw spring-boot:run

# Run the built jar directly
java -jar target/spring-boot-sample-0.0.1-SNAPSHOT.jar
```

Requires JDK 17+ (developed against JDK 21) with `JAVA_HOME` set. The app listens on port 8080 by default.

## Architecture

Single-module Spring Boot 4.1.1 app (`com.example.sample` package, Java 21). It's a minimal CRUD REST API, not layered into separate service/DTO tiers — controllers talk directly to Spring Data JPA repositories.

- `SpringBootSampleApplication` — standard `@SpringBootApplication` entry point, no custom config.
- `Item` — the sole JPA entity (`id`, `name` [`@NotBlank`], `description`).
- `ItemRepository` — `JpaRepository<Item, Long>` plus one derived query method, `findByNameContainingIgnoreCase`.
- `ItemController` — full CRUD at `/api/items` (GET list, GET by id, POST, PUT, DELETE) plus `GET /api/items/search?name={query}` (case-insensitive substring match; blank/missing query returns an empty list without hitting the database), using `@Valid` for request validation and `ResponseEntity` for 404/204 handling on not-found/delete.
- `HelloController` — trivial `GET /api/hello` sanity endpoint, unrelated to the CRUD resource.

**Persistence**: H2 in-memory database (`jdbc:h2:mem:sampledb`), schema auto-created via `spring.jpa.hibernate.ddl-auto=update` — data does not persist across restarts. The H2 web console is enabled at `/h2-console` (user `sa`, no password). All of this is configured in `src/main/resources/application.properties`; there's no `application-*.yml` profile split.

`ItemControllerTest` covers the CRUD flow end-to-end via `@SpringBootTest` + `MockMvc`; `SpringBootSampleApplicationTests` just checks the Spring context loads.

**`ai/` subpackage** (`com.example.sample.ai`) — a self-contained agentic tool-calling layer, intentionally structured differently from the rest of the app (it has a real service class, `AiOrchestrationService`, breaking the "no service layer" rule above on purpose — see `docs/agentic-concepts/agentic-application-layer.md` for why). `POST /api/ai/ask` takes a free-text query; an `LlmClient` (mocked by default via `app.ai.provider=mock`, or real via `anthropic` + `ANTHROPIC_API_KEY`) decides a step at a time (up to `MAX_STEPS=4`) which of 4 whitelisted tools to call against `ItemRepository`, then summarizes the full sequence — the step-at-a-time design (`decideNextStep`, not a single-shot `decideTool`) is what makes **tool composition** possible (e.g. "delete the item named Widget" needs `search_items` to resolve the id before `delete_item` can act). `delete_item` never deletes directly — it queues a **confirmation** (`ConfirmationStore`, token-based, 5-min TTL) that `POST /api/ai/confirm` must separately approve. Has its own guardrails (`PromptInjectionGuard`, per-client `RateLimiter`), structured tracing (`AiTrace`, logged as `ai_trace`/`ai_security_reject`/`ai_rate_limit_reject`/`ai_confirmed_action`), and an eval-style test suite (`MockLlmClientEvalTest`) distinct from ordinary unit tests. `AnthropicLlmClient` additionally demonstrates prompt caching (`cache_control`), a structured system prompt, response prefill, transient-vs-permanent error retry/backoff, and the Message Batches API (`POST /api/ai/batch`, 501 in mock mode). This package went through two real review cycles after being written — see `ISSUES_AND_FIXES.md` items 12–15 and `docs/agentic-concepts/agentic-application-layer.md` for what they caught.

**Spring Boot 4 package renames** (easy to trip on, since most examples/docs online still show the old paths): this app is on Boot 4.1, which moved to Jackson 3 and reorganized several test-support classes — `ObjectMapper` is `tools.jackson.databind.ObjectMapper` (not `com.fasterxml.jackson.databind`), and `AutoConfigureMockMvc` is `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (not `org.springframework.boot.test.autoconfigure.web.servlet`). Check `mvnw dependency:tree` before assuming a class's package if a compile fails with "package does not exist" — this is a Boot 4 module-split issue, not a missing dependency.

## Claude Code feature setup

This repo doubles as a working demo of Claude Code's agentic features — see `docs/agentic-concepts/` for the full write-up (skills, subagents, multi-agent flow, hooks, MCP, plugins, and the `CLAUDE.md` hierarchy itself). Two things worth knowing before editing anything under `.claude/`:

- Two `PostToolUse` hooks are configured in `.claude/settings.json` (`.claude/hooks/log-edit.js` and `compile-check.js`) — the second one runs a real `mvnw compile` after every `.java` edit. If that becomes disruptive, comment out its entry in `settings.json`.
- `.mcp.json` declares two project-scoped MCP servers: `filesystem-demo` (tools) and `item-inventory` (resources/prompts, in `mcp-resources-prompts-example/`). A fresh session in this directory will prompt to trust them before their capabilities become available.

Also see `src/main/java/com/example/sample/CLAUDE.md` (subdirectory-level) and `E:\Projects\CLAUDE.md` (parent-directory-level) for the rest of the `CLAUDE.md` hierarchy demonstrated in this workspace.

`agent-sdk-example/` at the repo root is a **separate Node.js project**, not part of the Maven build — a standalone Claude Agent SDK example, unrelated to the Spring app's own build/test lifecycle except that its one custom tool calls this app's live `/api/items` endpoint. See its own README and `docs/agentic-concepts/agent-sdk.md`.
