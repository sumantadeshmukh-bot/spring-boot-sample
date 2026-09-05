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
- `ItemRepository` — `JpaRepository<Item, Long>`, no custom query methods.
- `ItemController` — full CRUD at `/api/items` (GET list, GET by id, POST, PUT, DELETE), using `@Valid` for request validation and `ResponseEntity` for 404/204 handling on not-found/delete.
- `HelloController` — trivial `GET /api/hello` sanity endpoint, unrelated to the CRUD resource.

**Persistence**: H2 in-memory database (`jdbc:h2:mem:sampledb`), schema auto-created via `spring.jpa.hibernate.ddl-auto=update` — data does not persist across restarts. The H2 web console is enabled at `/h2-console` (user `sa`, no password). All of this is configured in `src/main/resources/application.properties`; there's no `application-*.yml` profile split.

`ItemControllerTest` covers the CRUD flow end-to-end via `@SpringBootTest` + `MockMvc`; `SpringBootSampleApplicationTests` just checks the Spring context loads.

**Spring Boot 4 package renames** (easy to trip on, since most examples/docs online still show the old paths): this app is on Boot 4.1, which moved to Jackson 3 and reorganized several test-support classes — `ObjectMapper` is `tools.jackson.databind.ObjectMapper` (not `com.fasterxml.jackson.databind`), and `AutoConfigureMockMvc` is `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (not `org.springframework.boot.test.autoconfigure.web.servlet`). Check `mvnw dependency:tree` before assuming a class's package if a compile fails with "package does not exist" — this is a Boot 4 module-split issue, not a missing dependency.
