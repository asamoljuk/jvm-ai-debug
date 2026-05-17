# Getting Started

## Prerequisites

| Requirement | Minimum version | Notes |
|-------------|----------------|-------|
| Java | 17 | Compiled at source/target 17; CI runs Java 21 via Temurin |
| Maven | 3.9 | Used for build and dependency management |

No API key is required to run the tool. The `mock` provider works entirely offline.

---

## Build

```bash
git clone https://github.com/asamoljuk/jvm-ai-debug.git
cd jvm-ai-debug

# Full build including all tests
mvn clean verify

# Build the fat JAR only (skips tests — faster for just running the tool)
mvn clean package -DskipTests
```

The build produces a single self-contained fat JAR at:

```
target/jvm-ai-debug.jar
```

This JAR includes all runtime dependencies (picocli, Jackson, OkHttp) and can be run with any Java 17+ installation. There is no separate installation step.

---

## Shell alias

To use the tool without typing `java -jar ...` every time, add an alias to your shell profile:

**bash / zsh (`~/.bashrc` or `~/.zshrc`)**
```bash
alias jvm-ai-debug='java -jar /absolute/path/to/jvm-ai-debug.jar'
```

**PowerShell (`$PROFILE`)**
```powershell
function jvm-ai-debug { java -jar "C:\path\to\jvm-ai-debug.jar" @args }
```

The examples throughout this documentation use `jvm-ai-debug` as the command name.

---

## First run

Run the tool on one of the bundled sample files to confirm the build is working:

```bash
java -jar target/jvm-ai-debug.jar analyze \
  src/test/resources/samples/spring-bean-failure.txt \
  --provider mock
```

Expected output:

```
=== AI JVM Debug Assistant ===

Detected issue:
  Spring Context Failure

Likely root cause:
  A circular dependency between Spring beans prevents the application context
  from initializing. Spring cannot resolve the dependency graph because two or
  more beans mutually depend on each other through constructor injection,
  creating an unresolvable cycle.

Evidence:
  - BeanCreationException found in stack trace
  - UnsatisfiedDependencyException indicates unresolved bean dependency
  - Circular dependency detected in the Spring dependency graph
  - Constructor injection used - Spring cannot break the cycle automatically

Suggested fixes:
  1. Refactor the dependency direction: extract shared logic into a third service that both depend on
  2. Introduce an interface boundary to decouple the beans
  3. Use setter or field injection with @Lazy on one side as a temporary workaround
  4. Split responsibilities: the circular dependency often signals a design smell
  5. Use ApplicationContext.getBean() lazily within a method if restructuring is not feasible

Confidence:
  High

Files/classes mentioned:
  - BeanCreationException
  - UnsatisfiedDependencyException
  ...
```

---

## Running tests

```bash
# All tests
mvn test

# One test class
mvn test -Dtest=LogParserTest

# One test method
mvn test -Dtest=IssueDetectorTest#detectsSpringContextFailure
```

The test suite has 57 tests and runs in under two seconds. No network access or API keys are required.

---

## Next steps

- See [CLI Reference](usage.md) for all commands and options.
- See [AI Providers](providers.md) to set up OpenAI, Anthropic, or Ollama.
- See [Issue Categories](issue-categories.md) to understand what the tool can detect.
