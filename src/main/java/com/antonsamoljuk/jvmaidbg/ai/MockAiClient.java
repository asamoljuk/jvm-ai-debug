package com.antonsamoljuk.jvmaidbg.ai;

import com.antonsamoljuk.jvmaidbg.model.AnalysisRequest;
import com.antonsamoljuk.jvmaidbg.model.AnalysisResponse;
import com.antonsamoljuk.jvmaidbg.model.IssueCategory;

import java.util.List;

public class MockAiClient implements AiClient {

    @Override
    public AnalysisResponse analyze(AnalysisRequest request) {
        IssueCategory category = request.getDetectedIssue().getCategory();
        List<String> detectedClasses = request.getDetectedIssue().getEvidence().getClassNames();

        return switch (category) {
            case SPRING_CONTEXT_FAILURE -> springContextResponse(detectedClasses);
            case NULL_POINTER_EXCEPTION -> nullPointerResponse(detectedClasses);
            case CLASS_NOT_FOUND -> classNotFoundResponse(detectedClasses);
            case NO_CLASS_DEF_FOUND -> noClassDefFoundResponse(detectedClasses);
            case MAVEN_BUILD_FAILURE -> mavenBuildResponse();
            case GRADLE_BUILD_FAILURE -> gradleBuildResponse();
            case JUNIT_TEST_FAILURE -> junitFailureResponse(detectedClasses);
            case TESTNG_TEST_FAILURE -> testNgFailureResponse(detectedClasses);
            case HIBERNATE_MAPPING_ERROR -> hibernateResponse(detectedClasses);
            case JVM_MEMORY_ERROR -> jvmMemoryResponse();
            default -> unknownResponse(request.getDetectedIssue().getEvidence().getExceptionNames());
        };
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    private AnalysisResponse springContextResponse(List<String> classes) {
        return new AnalysisResponse(
                IssueCategory.SPRING_CONTEXT_FAILURE.name(),
                "Spring application context failed to start",
                "A circular dependency between Spring beans prevents the application context from initializing. " +
                        "Spring cannot resolve the dependency graph because two or more beans mutually depend on each other " +
                        "through constructor injection, creating an unresolvable cycle.",
                List.of(
                        "BeanCreationException found in stack trace",
                        "UnsatisfiedDependencyException indicates unresolved bean dependency",
                        "Circular dependency detected in the Spring dependency graph",
                        "Constructor injection used - Spring cannot break the cycle automatically"
                ),
                List.of(
                        "Refactor the dependency direction: extract shared logic into a third service that both depend on",
                        "Introduce an interface boundary to decouple the beans",
                        "Use setter or field injection with @Lazy on one side as a temporary workaround",
                        "Split responsibilities: the circular dependency often signals a design smell",
                        "Use ApplicationContext.getBean() lazily within a method if restructuring is not feasible"
                ),
                "HIGH",
                classes.isEmpty() ? List.of("UserService", "NotificationService") : classes
        );
    }

    private AnalysisResponse nullPointerResponse(List<String> classes) {
        return new AnalysisResponse(
                IssueCategory.NULL_POINTER_EXCEPTION.name(),
                "NullPointerException — null reference dereference",
                "An object reference is null at the point of use. This typically occurs when a field or variable " +
                        "was never initialized, a method returned null unexpectedly, or optional chaining was skipped. " +
                        "In Spring applications this often means a @Autowired field was not injected (e.g. object created with new instead of Spring).",
                List.of(
                        "NullPointerException thrown at a method call or field access",
                        "Object reference was never assigned or returned null from a method",
                        "Possible missing initialization or incorrect Spring bean wiring"
                ),
                List.of(
                        "Check the stack frame immediately before the NPE — identify which reference is null",
                        "Add null guards: Objects.requireNonNull() or Optional<T> wrapping",
                        "Ensure Spring-managed beans are not instantiated with new — use @Autowired or constructor injection",
                        "Enable Java 14+ helpful NPE messages: -XX:+ShowCodeDetailsInExceptionMessages",
                        "Validate method return values before chaining calls"
                ),
                "HIGH",
                classes
        );
    }

    private AnalysisResponse classNotFoundResponse(List<String> classes) {
        return new AnalysisResponse(
                IssueCategory.CLASS_NOT_FOUND.name(),
                "ClassNotFoundException — class missing from classpath",
                "The JVM cannot find the requested class at runtime. This usually means a dependency is missing " +
                        "from pom.xml/build.gradle, the JAR was not packaged correctly, or there is a version conflict " +
                        "causing the wrong version to be loaded.",
                List.of(
                        "ClassNotFoundException thrown by the class loader",
                        "Required class is absent from the runtime classpath",
                        "Possible dependency scope mismatch (e.g., provided vs compile)"
                ),
                List.of(
                        "Add the missing dependency to pom.xml or build.gradle",
                        "Run 'mvn dependency:tree' to find version conflicts",
                        "Check dependency scope — 'provided' dependencies are not packaged into the fat JAR",
                        "Ensure the uber-JAR is built correctly and includes all transitive dependencies",
                        "Verify the class name and package — check for typos or moved packages"
                ),
                "HIGH",
                classes
        );
    }

    private AnalysisResponse noClassDefFoundResponse(List<String> classes) {
        return new AnalysisResponse(
                IssueCategory.NO_CLASS_DEF_FOUND.name(),
                "NoClassDefFoundError — class present at compile time but missing at runtime",
                "The class was available during compilation but cannot be found by the JVM at runtime. " +
                        "This differs from ClassNotFoundException in that the class existed when the code was compiled. " +
                        "Common causes: dependency not included in the runtime classpath, class loading isolation issues " +
                        "in application servers, or a failed static initializer in the target class.",
                List.of(
                        "NoClassDefFoundError thrown — class was resolvable at compile time",
                        "Runtime classpath differs from compile-time classpath",
                        "Possible static initializer failure causing subsequent load attempts to fail"
                ),
                List.of(
                        "Ensure runtime dependencies are not marked as 'provided' when they should be bundled",
                        "Check application server classloader hierarchy for isolation issues",
                        "Look for ExceptionInInitializerError earlier in the log — a static block may have failed",
                        "Run 'mvn dependency:resolve' to confirm all runtime dependencies are present",
                        "Inspect the fat JAR contents: 'jar tf app.jar | grep ClassName'"
                ),
                "HIGH",
                classes
        );
    }

    private AnalysisResponse mavenBuildResponse() {
        return new AnalysisResponse(
                IssueCategory.MAVEN_BUILD_FAILURE.name(),
                "Maven build failure",
                "The Maven build failed during one of its lifecycle phases. Common causes include compilation errors, " +
                        "test failures blocking the build, unresolvable dependencies, or a plugin execution failure. " +
                        "The [ERROR] lines in the build output pinpoint the exact failure.",
                List.of(
                        "BUILD FAILURE marker detected in Maven output",
                        "[ERROR] lines indicate specific compilation or plugin errors",
                        "Maven lifecycle execution halted before completion"
                ),
                List.of(
                        "Read all [ERROR] lines carefully — they identify the exact failing module and line",
                        "Run 'mvn clean compile -e' for full stack traces from plugin exceptions",
                        "Run 'mvn dependency:resolve' to check for missing or conflicting dependencies",
                        "Use 'mvn -pl <module> install -am' to build only the failing module",
                        "Check Java version compatibility: 'mvn -version' vs project source/target settings"
                ),
                "MEDIUM",
                List.of()
        );
    }

    private AnalysisResponse gradleBuildResponse() {
        return new AnalysisResponse(
                IssueCategory.GRADLE_BUILD_FAILURE.name(),
                "Gradle build failure",
                "A Gradle task failed during the build. This could be a compilation error, test failure, " +
                        "dependency resolution problem, or configuration issue in build.gradle.",
                List.of(
                        "BUILD FAILED marker detected in Gradle output",
                        "Task failure reported in the build output",
                        "Gradle task dependency chain was interrupted"
                ),
                List.of(
                        "Run 'gradle <task> --stacktrace' for full exception details",
                        "Run 'gradle dependencies' to inspect the dependency tree",
                        "Use 'gradle --info' or 'gradle --debug' for verbose logging",
                        "Check Gradle version compatibility with the project wrapper",
                        "Ensure JAVA_HOME points to the correct JDK version"
                ),
                "MEDIUM",
                List.of()
        );
    }

    private AnalysisResponse junitFailureResponse(List<String> classes) {
        return new AnalysisResponse(
                IssueCategory.JUNIT_TEST_FAILURE.name(),
                "JUnit test failure",
                "One or more JUnit tests failed. This indicates either an assertion mismatch (expected vs actual), " +
                        "an unexpected exception thrown during test execution, or test setup/teardown issues. " +
                        "The failing assertion or exception identifies the exact behavioral regression.",
                List.of(
                        "JUnit test failure reported in the output",
                        "AssertionError or test assertion mismatch detected",
                        "Test execution completed but assertions failed"
                ),
                List.of(
                        "Read the assertion error message — it shows expected vs actual values",
                        "Check @BeforeEach and @AfterEach methods for state leaking between tests",
                        "Ensure mocks are reset/re-initialized between tests",
                        "Use @TestMethodOrder to isolate ordering dependencies",
                        "Add logging in the failing test to trace intermediate state"
                ),
                "HIGH",
                classes
        );
    }

    private AnalysisResponse testNgFailureResponse(List<String> classes) {
        return new AnalysisResponse(
                IssueCategory.TESTNG_TEST_FAILURE.name(),
                "TestNG test failure",
                "A TestNG test suite has reported failures. Check the TestNG report for the failed test methods, " +
                        "exception messages, and the @BeforeMethod/@AfterMethod lifecycle for setup issues.",
                List.of(
                        "TestNG test failure detected",
                        "Test method assertion or exception failure"
                ),
                List.of(
                        "Review the TestNG HTML report for detailed failure info",
                        "Check @BeforeMethod setup for initialization errors",
                        "Inspect test group dependencies and ordering",
                        "Ensure test data providers return valid data"
                ),
                "MEDIUM",
                classes
        );
    }

    private AnalysisResponse hibernateResponse(List<String> classes) {
        return new AnalysisResponse(
                IssueCategory.HIBERNATE_MAPPING_ERROR.name(),
                "Hibernate ORM mapping or persistence error",
                "Hibernate cannot map the entity to the database schema. Common causes include a mismatch between " +
                        "the entity class and the database table columns, missing @Column annotations, incorrect " +
                        "cascade settings, or schema validation failure when hibernate.hbm2ddl.auto=validate is set.",
                List.of(
                        "Hibernate mapping exception detected in stack trace",
                        "Entity-to-schema mismatch or invalid mapping configuration",
                        "Possible schema validation failure"
                ),
                List.of(
                        "Compare entity field names and types against the actual database schema",
                        "Set spring.jpa.show-sql=true and check the generated DDL",
                        "Use hibernate.hbm2ddl.auto=update temporarily to see what Hibernate expects",
                        "Check @Column(name=...) mappings match actual column names",
                        "Verify @JoinColumn and @ManyToOne/@OneToMany cascade settings",
                        "Run schema migrations with Flyway or Liquibase to align schema"
                ),
                "HIGH",
                classes
        );
    }

    private AnalysisResponse jvmMemoryResponse() {
        return new AnalysisResponse(
                IssueCategory.JVM_MEMORY_ERROR.name(),
                "JVM memory exhaustion or stack overflow",
                "The JVM has run out of heap memory (OutOfMemoryError) or has exhausted thread stack space " +
                        "(StackOverflowError). OutOfMemoryError usually indicates a memory leak, oversized data " +
                        "processing, or insufficient heap settings. StackOverflowError indicates unbounded recursion.",
                List.of(
                        "OutOfMemoryError or StackOverflowError detected",
                        "JVM process ran out of allocated memory or stack space"
                ),
                List.of(
                        "For OutOfMemoryError: increase heap with -Xmx (e.g., -Xmx2g)",
                        "Take a heap dump: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/",
                        "Analyze heap dump with Eclipse MAT or VisualVM to find memory leaks",
                        "For StackOverflowError: identify and fix the recursive call chain",
                        "Increase stack size as a workaround: -Xss4m (but fix the root recursion)",
                        "Profile the application with JFR: -XX:+FlightRecorder"
                ),
                "HIGH",
                List.of()
        );
    }

    private AnalysisResponse unknownResponse(List<String> exceptions) {
        String exceptionsStr = exceptions.isEmpty() ? "no specific exceptions identified"
                : String.join(", ", exceptions);
        return new AnalysisResponse(
                IssueCategory.UNKNOWN.name(),
                "Unknown JVM issue — generic diagnostic",
                "The issue could not be automatically categorized. The log contains: " + exceptionsStr + ". " +
                        "Manual inspection of the full stack trace and log context is recommended. " +
                        "Look for the first exception in the chain — it is usually the true root cause.",
                List.of(
                        "Issue category could not be determined from the log content",
                        "Exceptions found: " + exceptionsStr
                ),
                List.of(
                        "Read the first exception at the top of the stack trace — it is the root cause",
                        "Search each exception name in the project issue tracker",
                        "Enable DEBUG logging for the relevant packages",
                        "Reproduce the issue in isolation with a minimal test case",
                        "Add -ea (enable assertions) and -verbose:class flags to the JVM"
                ),
                "LOW",
                exceptions
        );
    }
}

