package com.antonsamoljuk.jvmaidbg.parser;

import com.antonsamoljuk.jvmaidbg.model.ExtractedEvidence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {

    private static final Pattern EXCEPTION_PATTERN =
            Pattern.compile("([a-zA-Z][a-zA-Z0-9_.]*(?:Exception|Error))");

    private static final Pattern CAUSED_BY_PATTERN =
            Pattern.compile("^\\s*Caused by:\\s*(.+)", Pattern.MULTILINE);

    private static final Pattern STACK_FRAME_PATTERN =
            Pattern.compile("\\s+at\\s+([a-zA-Z][a-zA-Z0-9_.]+)\\.([a-zA-Z][a-zA-Z0-9_]+)\\(");

    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("\\b([A-Z][a-zA-Z0-9]*(?:Service|Controller|Repository|Bean|Factory|Manager|Handler|Listener|Config|Configuration|Application|Component|Exception|Error|Impl)s?)\\b");

    private static final int MAX_EXCERPT_LENGTH = 3000;

    public ExtractedEvidence parse(String content) {
        ExtractedEvidence evidence = new ExtractedEvidence();

        evidence.setExceptionNames(extractExceptionNames(content));
        evidence.setCausedByChain(extractCausedByChain(content));
        evidence.setClassNames(extractClassNames(content));
        evidence.setMethodNames(extractMethodNames(content));
        evidence.setFrameworkIndicators(detectFrameworkIndicators(content));
        evidence.setBuildToolIndicators(detectBuildToolIndicators(content));
        evidence.setTestFrameworkIndicators(detectTestFrameworkIndicators(content));
        evidence.setRawExcerpt(truncate(content, MAX_EXCERPT_LENGTH));

        return evidence;
    }

    public List<String> extractExceptionNames(String content) {
        Set<String> exceptions = new LinkedHashSet<>();
        Matcher matcher = EXCEPTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            // keep only the simple class name portion
            String simple = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
            exceptions.add(simple);
        }
        return new ArrayList<>(exceptions);
    }

    public List<String> extractCausedByChain(String content) {
        List<String> chain = new ArrayList<>();
        Matcher matcher = CAUSED_BY_PATTERN.matcher(content);
        while (matcher.find()) {
            chain.add(matcher.group(1).trim());
        }
        return chain;
    }

    public List<String> extractClassNames(String content) {
        Set<String> classes = new LinkedHashSet<>();
        Matcher matcher = CLASS_NAME_PATTERN.matcher(content);
        while (matcher.find()) {
            classes.add(matcher.group(1));
        }
        // also extract from stack frames
        Matcher frameMatcher = STACK_FRAME_PATTERN.matcher(content);
        while (frameMatcher.find()) {
            String fqn = frameMatcher.group(1);
            String simple = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
            if (Character.isUpperCase(simple.charAt(0))) {
                classes.add(simple);
            }
        }
        return new ArrayList<>(classes);
    }

    public List<String> extractMethodNames(String content) {
        Set<String> methods = new LinkedHashSet<>();
        Matcher matcher = STACK_FRAME_PATTERN.matcher(content);
        while (matcher.find()) {
            methods.add(matcher.group(2));
        }
        return new ArrayList<>(methods);
    }

    public List<String> detectFrameworkIndicators(String content) {
        List<String> indicators = new ArrayList<>();
        String lower = content.toLowerCase();

        if (lower.contains("springframework") || lower.contains("spring boot") || lower.contains("beanfactory")) {
            indicators.add("Spring Framework");
        }
        if (lower.contains("hibernate") || lower.contains("javax.persistence") || lower.contains("jakarta.persistence")) {
            indicators.add("Hibernate/JPA");
        }
        if (lower.contains("tomcat") || lower.contains("catalina")) {
            indicators.add("Apache Tomcat");
        }
        if (lower.contains("netty") || lower.contains("reactor")) {
            indicators.add("Netty/Reactor");
        }
        if (lower.contains("jackson") || lower.contains("objectmapper")) {
            indicators.add("Jackson");
        }
        if (lower.contains("log4j") || lower.contains("logback") || lower.contains("slf4j")) {
            indicators.add("Logging Framework");
        }
        return indicators;
    }

    public List<String> detectBuildToolIndicators(String content) {
        List<String> indicators = new ArrayList<>();
        String lower = content.toLowerCase();

        if (lower.contains("[error]") || lower.contains("build failure") || lower.contains("maven")) {
            indicators.add("Maven");
        }
        if (lower.contains("task :") || lower.contains("gradle") || lower.contains("build.gradle")) {
            indicators.add("Gradle");
        }
        return indicators;
    }

    public List<String> detectTestFrameworkIndicators(String content) {
        List<String> indicators = new ArrayList<>();
        String lower = content.toLowerCase();

        if (lower.contains("org.junit") || lower.contains("@test") || lower.contains("assertionerror")) {
            indicators.add("JUnit");
        }
        if (lower.contains("org.testng") || lower.contains("testng")) {
            indicators.add("TestNG");
        }
        if (lower.contains("mockito") || lower.contains("@mock") || lower.contains("@mockbean")) {
            indicators.add("Mockito");
        }
        return indicators;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "\n... [truncated]";
    }
}
