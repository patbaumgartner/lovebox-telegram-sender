# Contributing to Lovebox Telegram Sender

Thanks for considering a contribution! This document explains how to get a development
environment running and what to look out for when submitting changes.

## Development Setup

1. **JDK 25** — with [SDKMAN!](https://sdkman.io) simply run:

   ```bash
   sdk env install
   ```

2. **Build & test**:

   ```bash
   ./mvnw verify
   ```

3. **Run locally** — put your personal configuration into
   `src/main/resources/application-local.properties` (gitignored) and start with the
   `local` profile:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

   Tip: set `lovebox.enabled=false` for a dry run that never calls the Lovebox API —
   message sending is simulated, only Telegram is contacted.

## Code Style

The build enforces the [Spring Java Format](https://github.com/spring-io/spring-javaformat).
Format your changes before committing:

```bash
./mvnw spring-javaformat:apply
```

CI validates formatting with `spring-javaformat:validate` and fails on violations.

## Tests

- Unit tests live under [src/test/java](src/test/java) and use JUnit 5, Mockito, and AssertJ.
- Please cover new behavior with tests; `./mvnw verify` also produces a JaCoCo coverage
  report under `target/site/jacoco/`.

## Native Image Considerations

This application ships as a GraalVM native image, which makes some otherwise-idiomatic
Spring patterns fail **silently at runtime**. When touching startup, configuration, or
anything reflective, keep these rules in mind (details in the class-level Javadoc):

- **No lambda `ApplicationListener`s** for startup work — they are not invoked in the
  native image. Use named `ApplicationRunner` beans (see `TelegramBotsRegistrar`).
- **Register reflection/JNI/resource hints** for anything loaded reflectively
  (see `TelegramBotsRuntimeHints`, `NativeHintsConfiguration`, `ApplicationRuntimeHints`).
- **Test natively when possible**: `./mvnw -Pnative spring-boot:build-image` builds the
  container the same way CI does.

## Commit Messages

The project follows [Conventional Commits](https://www.conventionalcommits.org/), e.g.:

```text
feat(telegram): announce hearts to all known chats
fix(native): register JPEG JNI fields so photo messages are processed
docs: clarify Synology deployment
```

## Pull Requests

1. Fork and create a feature branch from `main`.
2. Make your change, add tests, run `./mvnw spring-javaformat:apply verify`.
3. Open a pull request with a clear description of the motivation and behavior change.

## Reporting Issues

Use the [issue tracker](https://github.com/patbaumgartner/lovebox-telegram-sender/issues).
For security issues, please follow [SECURITY.md](SECURITY.md) instead of opening a
public issue.
