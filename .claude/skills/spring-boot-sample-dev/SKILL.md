---
name: spring-boot-sample-dev
description: Build, test, and run the spring-boot-sample Spring Boot app using its Maven wrapper. Use whenever the user asks to build, test, or run this specific project (not general Java/Maven questions).
---

# spring-boot-sample dev workflow

This project always uses the Maven wrapper — never a bare `mvn` — so the build works even if Maven isn't installed globally. Run these from the repo root (`E:\Projects\spring-boot-sample`), using `mvnw.cmd` on Windows or `./mvnw` on Unix shells.

## Build

```bash
./mvnw clean package -DskipTests
```

Produces `target/spring-boot-sample-0.0.1-SNAPSHOT.jar`.

## Test

```bash
./mvnw test                                  # full suite
./mvnw test -Dtest=ClassName                 # single class
./mvnw test -Dtest=ClassName#methodName      # single method
```

## Run

```bash
./mvnw spring-boot:run           # foreground, for interactive dev
java -jar target/spring-boot-sample-0.0.1-SNAPSHOT.jar   # run the built jar
```

The app listens on port 8080. Quick smoke test once running:

```bash
curl http://localhost:8080/api/hello
curl http://localhost:8080/api/items
```

## Docker

A multi-stage `Dockerfile` builds and runs the jar (build stage uses `eclipse-temurin:21-jdk` + the Maven wrapper, run stage uses `eclipse-temurin:21-jre`).

```bash
docker build -t spring-boot-sample .
docker run --rm -p 8080:8080 spring-boot-sample
```

## CI

`.github/workflows/ci.yml` runs on every push/PR to `main`: sets up JDK 21 (Temurin), runs `./mvnw clean verify`, then builds the Docker image. If a test or the Docker build fails in CI, reproduce locally first with the commands above before touching the workflow file.

## Notes

- Requires `JAVA_HOME` pointed at a JDK 17+ install (this machine uses Oracle JDK 21 at `C:\Program Files\Java\jdk-21.0.12`).
- H2 is in-memory — data resets every restart. Console at `/h2-console` if the user wants to inspect the schema/data interactively.
- Spring Boot 4's Jackson/test-package renames tripped up the first test written here — see `CLAUDE.md` for the specifics if a test fails to compile with a "package does not exist" error on an otherwise-standard Jackson/MockMvc import.
