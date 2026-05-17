# Issue Categories

The tool detects 11 issue categories. Detection runs automatically on every input — the `--type` flag is reserved for future use and does not currently influence which category is chosen.

---

## Detection pipeline

The `LogParser` first extracts raw signals from the input text:

- **Exception names** — any token ending in `Exception` or `Error` (e.g., `BeanCreationException`, `OutOfMemoryError`)
- **Caused-by chain** — lines matching `Caused by: ...`
- **Class names** — tokens starting with an uppercase letter followed by a known suffix (`Service`, `Controller`, `Repository`, `Bean`, `Factory`, `Manager`, `Handler`, `Listener`, `Config`, `Configuration`, `Application`, `Component`, `Exception`, `Error`, `Impl`) and class names from stack frame `at` lines
- **Method names** — method names from stack frame `at` lines
- **Framework indicators** — keyword matches for Spring, Hibernate, Tomcat, Netty, Jackson, logging frameworks
- **Build tool indicators** — keyword matches for Maven (`[ERROR]`, `build failure`, `maven`) and Gradle (`task :`, `gradle`, `build.gradle`)
- **Test framework indicators** — keyword matches for JUnit (`org.junit`, `@test`, `assertionerror`), TestNG, Mockito

The `IssueDetector` then maps these signals to a category using priority-ordered rules. **The order matters**: some categories would be misidentified if checked in the wrong order (e.g., a JUnit failure inside a Maven build output contains both JUnit signals and `BUILD FAILURE` — JUnit must be checked first).

---

## Confidence scoring

| Condition | Confidence |
|-----------|-----------|
| Exception names found **and** caused-by chain present | `HIGH` |
| Exception names found **or** caused-by chain present (not both) | `MEDIUM` |
| Neither exceptions nor caused-by chain found | `LOW` |
| Category is `UNKNOWN` | Always `LOW` |

---

## Categories

### `SPRING_CONTEXT_FAILURE`
**Display name:** Spring application context failed to start

**Detection signals (any one triggers):**
- Exception name contains `BeanCreationException`, `UnsatisfiedDependencyException`, `BeanDefinitionParsingException`, or `NoSuchBeanDefinitionException`
- Raw content contains both `applicationcontext` and `fail` (case-insensitive)

**Typical input:**
```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'userService'
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: ...
Caused by: org.springframework.beans.factory.BeanCurrentlyInCreationException: ...
```

**Mock response fixes:**
1. Refactor the dependency direction — extract shared logic into a third service
2. Introduce an interface boundary to decouple the beans
3. Use setter/field injection with `@Lazy` as a temporary workaround
4. Split responsibilities (circular dependency often signals a design smell)
5. Use `ApplicationContext.getBean()` lazily within a method

---

### `NULL_POINTER_EXCEPTION`
**Display name:** NullPointerException - null reference dereference

**Detection signals:**
- Exception name is exactly `NullPointerException`

**Checked after:** `SPRING_CONTEXT_FAILURE` and JVM memory errors.

**Typical input:**
```
java.lang.NullPointerException: Cannot invoke "com.example.UserRepository.findById(Long)"
  because "this.userRepository" is null
    at com.example.UserService.getUserById(UserService.java:45)
```

**Mock response fixes:**
1. Check the stack frame immediately before the NPE — identify which reference is null
2. Add null guards: `Objects.requireNonNull()` or `Optional<T>` wrapping
3. Ensure Spring-managed beans are not instantiated with `new` — use `@Autowired` or constructor injection
4. Enable Java 14+ helpful NPE messages: `-XX:+ShowCodeDetailsInExceptionMessages`
5. Validate method return values before chaining calls

---

### `CLASS_NOT_FOUND`
**Display name:** ClassNotFoundException - missing dependency or classpath issue

**Detection signals:**
- Exception name is exactly `ClassNotFoundException`

**Typical input:**
```
java.lang.ClassNotFoundException: com.example.MissingClass
    at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:520)
```

**Mock response fixes:**
1. Add the missing dependency to `pom.xml` or `build.gradle`
2. Run `mvn dependency:tree` to find version conflicts
3. Check dependency scope — `provided` dependencies are not packaged into the fat JAR
4. Ensure the uber-JAR is built correctly and includes all transitive dependencies
5. Verify the class name and package — check for typos or moved packages

---

### `NO_CLASS_DEF_FOUND`
**Display name:** NoClassDefFoundError - class loading failure at runtime

**Detection signals:**
- Exception name is exactly `NoClassDefFoundError`

**Distinction from `CLASS_NOT_FOUND`:** The class existed at compile time but cannot be found at runtime. Often caused by a failed static initializer or a scope mismatch.

**Mock response fixes:**
1. Ensure runtime dependencies are not marked as `provided` when they should be bundled
2. Check application server classloader hierarchy for isolation issues
3. Look for `ExceptionInInitializerError` earlier in the log — a static block may have failed
4. Run `mvn dependency:resolve` to confirm all runtime dependencies are present
5. Inspect the fat JAR: `jar tf app.jar | grep ClassName`

---

### `MAVEN_BUILD_FAILURE`
**Display name:** Maven build compilation or lifecycle failure

**Detection signals (all checked after JUnit/TestNG):**
- Raw content contains `build failure`, `[error]`, `compilation failure`, or `maven`
- And `Gradle` is **not** listed in build tool indicators

**Note:** This category is checked **after** `JUNIT_TEST_FAILURE` and `TESTNG_TEST_FAILURE`. A Maven build that fails because of a JUnit test failure contains both signals, and `JUNIT_TEST_FAILURE` is the more specific and useful category.

**Mock confidence:** MEDIUM (build failures often lack a Java exception stack trace).

**Typical input:**
```
[ERROR] COMPILATION ERROR :
[ERROR] UserService.java:[23,15] cannot find symbol
[INFO] BUILD FAILURE
```

**Mock response fixes:**
1. Read all `[ERROR]` lines — they identify the exact failing module and line
2. Run `mvn clean compile -e` for full stack traces from plugin exceptions
3. Run `mvn dependency:resolve` to check for missing or conflicting dependencies
4. Use `mvn -pl <module> install -am` to build only the failing module
5. Check Java version: `mvn -version` vs project `source`/`target` settings

---

### `GRADLE_BUILD_FAILURE`
**Display name:** Gradle build failure

**Detection signals:**
- Raw content contains `task :`, `build failed`, or `gradle`, or `Gradle` is in build tool indicators

**Mock confidence:** MEDIUM.

**Mock response fixes:**
1. Run `gradle <task> --stacktrace` for full exception details
2. Run `gradle dependencies` to inspect the dependency tree
3. Use `gradle --info` or `gradle --debug` for verbose logging
4. Check Gradle version compatibility with the project wrapper
5. Ensure `JAVA_HOME` points to the correct JDK version

---

### `JUNIT_TEST_FAILURE`
**Display name:** JUnit test assertion or execution failure

**Detection signals (checked before Maven/Gradle):**
- Raw content contains `org.junit`, `assertionerror`, or `tests run:`
- Or test framework indicators include `JUnit`

**Mock confidence:** HIGH.

**Typical input:**
```
org.opentest4j.AssertionFailedError: expected: <User{id=1}> but was: <null>
    at UserServiceTest.shouldReturnUserById(UserServiceTest.java:55)
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
```

**Mock response fixes:**
1. Read the assertion error message — it shows expected vs actual values
2. Check `@BeforeEach` and `@AfterEach` methods for state leaking between tests
3. Ensure mocks are reset/re-initialized between tests
4. Use `@TestMethodOrder` to isolate ordering dependencies
5. Add logging in the failing test to trace intermediate state

---

### `TESTNG_TEST_FAILURE`
**Display name:** TestNG test failure

**Detection signals (checked before Maven/Gradle):**
- Raw content contains `org.testng`
- Or test framework indicators include `TestNG`

**Mock confidence:** MEDIUM.

**Mock response fixes:**
1. Review the TestNG HTML report for detailed failure info
2. Check `@BeforeMethod` setup for initialization errors
3. Inspect test group dependencies and ordering
4. Ensure test data providers return valid data

---

### `HIBERNATE_MAPPING_ERROR`
**Display name:** Hibernate ORM mapping or persistence error

**Detection signals:**
- Exception name contains `MappingException`, `HibernateException`, `PersistenceException`, or `EntityNotFoundException`
- Or exception name contains `org.hibernate`, `javax.persistence`, or `jakarta.persistence`
- Or raw content contains `hibernate` and (`mapping` or `schema`)

**Mock confidence:** HIGH.

**Mock response fixes:**
1. Compare entity field names and types against the actual database schema
2. Set `spring.jpa.show-sql=true` and check the generated DDL
3. Use `hibernate.hbm2ddl.auto=update` temporarily to see what Hibernate expects
4. Check `@Column(name=...)` mappings match actual column names
5. Verify `@JoinColumn` and `@ManyToOne`/`@OneToMany` cascade settings
6. Run schema migrations with Flyway or Liquibase to align the schema

---

### `JVM_MEMORY_ERROR`
**Display name:** JVM memory exhaustion or stack overflow

**Detection signals (checked early, before generic exceptions):**
- Exception name contains `OutOfMemoryError`, `StackOverflowError`, or `GCOverheadLimitExceededError`

**Mock confidence:** HIGH.

**Mock response fixes:**
1. For `OutOfMemoryError`: increase heap with `-Xmx` (e.g., `-Xmx2g`)
2. Take a heap dump: `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/`
3. Analyze heap dump with Eclipse MAT or VisualVM to find memory leaks
4. For `StackOverflowError`: identify and fix the recursive call chain
5. Increase stack size as a workaround: `-Xss4m` (but fix the root recursion)
6. Profile with JFR: `-XX:+FlightRecorder`

---

### `UNKNOWN`
**Display name:** Unknown issue - generic diagnostic analysis

**Triggered when:** No other category matches.

**Confidence:** Always `LOW`.

**Mock response:** The root cause text includes the list of exception names that were extracted (or `"no specific exceptions identified"` if none were found). Fixes are generic debugging steps applicable to any JVM issue.

**Mock response fixes:**
1. Read the first exception at the top of the stack trace — it is the root cause
2. Search each exception name in the project issue tracker
3. Enable DEBUG logging for the relevant packages
4. Reproduce the issue in isolation with a minimal test case
5. Add `-ea` (enable assertions) and `-verbose:class` flags to the JVM

---

## Detection priority order

The detector checks categories in this exact sequence. The first match wins.

```
1. SPRING_CONTEXT_FAILURE
2. JVM_MEMORY_ERROR
3. CLASS_NOT_FOUND
4. NO_CLASS_DEF_FOUND
5. NULL_POINTER_EXCEPTION
6. HIBERNATE_MAPPING_ERROR
7. JUNIT_TEST_FAILURE          ← must be before Maven/Gradle
8. TESTNG_TEST_FAILURE         ← must be before Maven/Gradle
9. MAVEN_BUILD_FAILURE
10. GRADLE_BUILD_FAILURE
11. UNKNOWN
```
