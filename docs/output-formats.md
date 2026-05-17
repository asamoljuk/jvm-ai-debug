# Output Formats

The tool supports two output formats selected with `--format` / `-f`.

---

## Text format (default)

Selected with `--format text` or by omitting `--format`.

The analysis is printed to **stdout** as human-readable text. Lines in the root cause section are word-wrapped at 80 characters with 2-space indentation.

### Structure

```
=== AI JVM Debug Assistant ===

Detected issue:
  <Category name in Title Case>

Likely root cause:
  <Root cause text, word-wrapped at 80 chars>

Evidence:
  - <evidence point 1>
  - <evidence point 2>
  ...

Suggested fixes:
  1. <fix 1>
  2. <fix 2>
  ...

Confidence:
  <High | Medium | Low>

Files/classes mentioned:
  - <ClassName1>
  - <ClassName2>
  ...
```

Sections with no data are omitted:
- `Evidence` is omitted if the evidence list is null or empty
- `Suggested fixes` is omitted if the fixes list is null or empty
- `Files/classes mentioned` is omitted if the class list is null or empty

### Full example

Input (`src/test/resources/samples/spring-bean-failure.txt`):

```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'userService'
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: ...
Caused by: java.lang.IllegalStateException: Circular dependency detected between UserService and NotificationService
```

Command:

```bash
jvm-ai-debug analyze src/test/resources/samples/spring-bean-failure.txt --provider mock
```

Output:

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
  - BeanCurrentlyInCreationException
  - AbstractAutowireCapableBeanFactory
  - AbstractBeanFactory
  - UserService
  - NotificationService
  - IllegalStateException
  - ConstructorResolver
  - DefaultSingletonBeanRegistry
```

### Category name formatting

The `detectedIssue` value (an enum name like `SPRING_CONTEXT_FAILURE`) is displayed in Title Case with underscores replaced by spaces:

| Enum value | Displayed as |
|-----------|-------------|
| `SPRING_CONTEXT_FAILURE` | `Spring Context Failure` |
| `NULL_POINTER_EXCEPTION` | `Null Pointer Exception` |
| `MAVEN_BUILD_FAILURE` | `Maven Build Failure` |
| `JVM_MEMORY_ERROR` | `Jvm Memory Error` |
| `UNKNOWN` | `Unknown` |

---

## JSON format

Selected with `--format json`.

The analysis is serialized as pretty-printed JSON to **stdout**. This format is designed for CI pipeline integration, scripting with `jq`, or downstream processing.

### Schema

```json
{
  "detectedIssue": "string — enum name (e.g. SPRING_CONTEXT_FAILURE)",
  "title": "string — human-readable title",
  "likelyRootCause": "string — detailed root cause explanation",
  "evidence": ["string", "..."],
  "suggestedFixes": ["string", "..."],
  "confidence": "HIGH | MEDIUM | LOW",
  "mentionedClasses": ["string", "..."]
}
```

All fields use camelCase. Fields that are null in the response object are serialized as JSON `null`.

### Full example

Command:

```bash
jvm-ai-debug analyze src/test/resources/samples/spring-bean-failure.txt \
  --provider mock --format json
```

Output:

```json
{
  "detectedIssue" : "SPRING_CONTEXT_FAILURE",
  "title" : "Spring application context failed to start",
  "likelyRootCause" : "A circular dependency between Spring beans prevents the application context from initializing. Spring cannot resolve the dependency graph because two or more beans mutually depend on each other through constructor injection, creating an unresolvable cycle.",
  "evidence" : [ "BeanCreationException found in stack trace", "UnsatisfiedDependencyException indicates unresolved bean dependency", "Circular dependency detected in the Spring dependency graph", "Constructor injection used - Spring cannot break the cycle automatically" ],
  "suggestedFixes" : [ "Refactor the dependency direction: extract shared logic into a third service that both depend on", "Introduce an interface boundary to decouple the beans", "Use setter or field injection with @Lazy on one side as a temporary workaround", "Split responsibilities: the circular dependency often signals a design smell", "Use ApplicationContext.getBean() lazily within a method if restructuring is not feasible" ],
  "confidence" : "HIGH",
  "mentionedClasses" : [ "BeanCreationException", "UnsatisfiedDependencyException", "BeanCurrentlyInCreationException", "AbstractAutowireCapableBeanFactory", "AbstractBeanFactory", "UserService", "NotificationService", "IllegalStateException", "ConstructorResolver", "DefaultSingletonBeanRegistry" ]
}
```

### Scripting with jq

```bash
# Extract only the suggested fixes
jvm-ai-debug analyze stacktrace.txt --format json | jq '.suggestedFixes[]'

# Print confidence level
jvm-ai-debug analyze stacktrace.txt --format json | jq -r '.confidence'

# Check if confidence is HIGH in a shell script
CONFIDENCE=$(jvm-ai-debug analyze stacktrace.txt --format json | jq -r '.confidence')
if [ "$CONFIDENCE" = "HIGH" ]; then
  echo "High-confidence diagnosis available"
fi

# Extract mentioned classes as a comma-separated list
jvm-ai-debug analyze stacktrace.txt --format json \
  | jq -r '.mentionedClasses | join(", ")'

# Save analysis and check the detected category
jvm-ai-debug analyze build.log --format json > analysis.json
CATEGORY=$(jq -r '.detectedIssue' analysis.json)
echo "Detected: $CATEGORY"
```

---

## Verbose diagnostic output

The `--verbose` / `-v` flag writes additional diagnostic information to **stderr** (not stdout). This means it does not pollute piped or redirected JSON output.

Verbose output includes:

```
[verbose] Provider: mock
[verbose] Input file: stacktrace.txt
[verbose] Output format: TEXT
[verbose] Detected category: SPRING_CONTEXT_FAILURE
[verbose] Confidence: HIGH

[verbose] Prompt sent to AI:
---
You are an expert Java/JVM debugging assistant. Analyze the following JVM failure...

## Detected Issue Category
SPRING_CONTEXT_FAILURE — Spring application context failed to start

## Extracted Evidence
- Exceptions found: BeanCreationException, UnsatisfiedDependencyException, ...
- Caused-by chain: org.springframework.beans.factory.UnsatisfiedDependencyException: ..., ...
- Mentioned classes: BeanCreationException, ...
- Framework indicators: Spring Framework

## Raw Log Excerpt
```
org.springframework.beans.factory.BeanCreationException: ...
```
...
---
```

Using verbose with JSON format still writes the JSON analysis to stdout and the diagnostics to stderr:

```bash
# JSON on stdout, diagnostics on stderr
jvm-ai-debug analyze stacktrace.txt --format json --verbose > analysis.json
```
