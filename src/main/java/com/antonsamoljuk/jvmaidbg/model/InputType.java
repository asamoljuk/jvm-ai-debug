package com.antonsamoljuk.jvmaidbg.model;

/** Hint about the nature of the input file, supplied via {@code --type}. */
public enum InputType {
    /** Plain Java/JVM stack trace — skip build-tool and test-framework checks. */
    STACKTRACE,
    /** Build-tool output (Maven/Gradle) — skip test-framework checks. */
    BUILD_LOG,
    /** Test runner output (JUnit/TestNG) — skip build-tool checks. */
    TEST_FAILURE,
    /** Auto-detect (default, full heuristic ordering). */
    AUTO
}
