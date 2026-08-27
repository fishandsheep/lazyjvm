# Repository Guidelines

## Project Structure & Module Organization

LazyJVM is a Java 21 Maven multi-module project:

- `domain/` contains shared diagnostic models and metric history logic.
- `jvm-adapter/` handles local JVM discovery, JMX sampling, `jcmd`, and report export.
- `tui/` contains JLine terminal rendering, input handling, layout, and interaction tests.
- `distribution/` contains the Picocli entry point and shaded/jlink packaging.
- Each module keeps production code in `src/main/java` and JUnit tests in `src/test/java`.
- `README.md`, `PRODUCT.md`, and `DESIGN.md` document usage and terminal UX. Build output in `target/` is generated and ignored.

## Build, Test, and Development Commands

Use JDK 21 and Maven 3.9 or newer on Linux or macOS.

```bash
mvn test                         # Run all JUnit tests
mvn verify                       # Compile, test, and run Maven verification
mvn package -Pruntime-image      # Build the shaded JAR and jlink runtime image
java -jar distribution/target/lazyjvm-0.1.0-SNAPSHOT-all.jar
```

For focused work, use `mvn -pl tui -am test` (replace `tui` with another module). CI runs `mvn --batch-mode --no-transfer-progress verify -Pruntime-image`.

## Coding Style & Naming Conventions

Follow existing Java style: four-space indentation, braces on the same line, UTF-8 source, and short methods with explicit types where they improve clarity. Use `PascalCase` for classes, `camelCase` for methods and variables, `UPPER_SNAKE_CASE` for constants, and `dev.lazyjvm.<area>` packages. Keep classes and APIs narrowly scoped; update `module-info.java` when adding exported packages or dependencies. No formatter or linter is configured, so inspect the diff and preserve terminal-cell width, Unicode/ASCII fallback, and no-color behavior in TUI changes.

## Testing Guidelines

Tests use JUnit Jupiter 5.8.1. Name test classes `*Test.java` and test methods with descriptive lower camel case, such as `preservesMissingSamplesAsVisibleGaps`. Add regression tests for behavior changes; no coverage threshold is configured. Run `mvn test` before submitting.

## Commit & Pull Request Guidelines

No local Git history is available to infer a repository-specific convention. Use concise imperative subjects and focused commits, for example `Fix sampler pause handling`. Pull requests should explain behavior and affected modules, list validation commands, link an issue when applicable, and include terminal screenshots or recordings for visible TUI changes. Mention platform-specific behavior or limitations.

## Security & Configuration Tips

LazyJVM attaches only to local JVMs in the same OS user and PID namespace. It does not use `sudo` or a shell. Treat captured command output, JVM arguments, and screenshots as potentially sensitive; redact them before sharing.
