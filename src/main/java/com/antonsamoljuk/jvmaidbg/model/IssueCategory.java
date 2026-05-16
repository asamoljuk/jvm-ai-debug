package com.antonsamoljuk.jvmaidbg.model;

public enum IssueCategory {
    SPRING_CONTEXT_FAILURE("Spring application context failed to start"),
    NULL_POINTER_EXCEPTION("NullPointerException - null reference dereference"),
    CLASS_NOT_FOUND("ClassNotFoundException - missing dependency or classpath issue"),
    NO_CLASS_DEF_FOUND("NoClassDefFoundError - class loading failure at runtime"),
    MAVEN_BUILD_FAILURE("Maven build compilation or lifecycle failure"),
    GRADLE_BUILD_FAILURE("Gradle build failure"),
    JUNIT_TEST_FAILURE("JUnit test assertion or execution failure"),
    TESTNG_TEST_FAILURE("TestNG test failure"),
    HIBERNATE_MAPPING_ERROR("Hibernate ORM mapping or persistence error"),
    JVM_MEMORY_ERROR("JVM memory exhaustion or stack overflow"),
    UNKNOWN("Unknown issue - generic diagnostic analysis");

    private final String displayName;

    IssueCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
