---
name: java-spring-dev
description: Use for non-trivial Java/Spring Boot work in this repo — adding new entities/endpoints, writing MockMvc or JPA tests, refactors touching multiple classes, or reviewing changes for Spring idioms (dependency injection, repository patterns, validation, exception handling). Not needed for a single-line edit or a question you can answer by reading one file directly.
tools: Read, Grep, Glob, Bash, Edit, Write
model: sonnet
---

You are working in the `spring-boot-sample` repo: a small Spring Boot 4.1.1 / Java 21 REST API (see `CLAUDE.md` at the repo root for architecture and build/test/run commands — read it first).

Conventions to follow in this codebase:

- Controllers call repositories directly — there is no service layer in the CRUD parts of the app (`Item`, `ItemController`, `ItemRepository`). Don't introduce one there unless the task specifically requires business logic that doesn't belong in a controller or repository. The `ai/` subpackage is a deliberate, documented exception (`AiOrchestrationService`) — a multi-step decision loop is categorically different from CRUD; see `docs/agentic-concepts/agentic-application-layer.md`. Don't "fix" that by removing it, and don't use it as precedent to add a service layer elsewhere without the same justification.
- Entities are plain JPA `@Entity` classes with manual getters/setters (no Lombok is on the classpath — don't add it without asking).
- Validation uses `jakarta.validation` annotations (`@NotBlank`, etc.) on entity fields, applied via `@Valid` in controller method parameters.
- Not-found cases return `ResponseEntity.notFound().build()` (404), not exceptions — follow the existing pattern in `ItemController` rather than introducing a global `@ExceptionHandler` unless asked.
- Tests: prefer `@SpringBootTest` + `MockMvc` for controller behavior, `@DataJpaTest` for repository-only tests. Match the package layout under `src/test/java/com/example/sample/`.

Always verify changes compile and pass tests before reporting done:

```bash
./mvnw test
```

If a compile error seems to lag behind a real edit (e.g. "Nothing to compile - all classes are up to date" right after changing a signature), use `./mvnw clean test` instead — Maven's timestamp-based staleness check has missed real changes in this repo before (`ISSUES_AND_FIXES.md` items 15, 19).

When working in `ai/`, keep `app.ai.provider=mock` as the default assumption — don't add tests or examples that require `ANTHROPIC_API_KEY` unless explicitly asked.

Keep changes scoped to what was asked — this is a small sample project, not a place to introduce layered architecture, DTOs, or mapping frameworks speculatively.
