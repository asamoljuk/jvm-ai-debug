package com.antonsamoljuk.jvmaidbg.model;

import java.util.ArrayList;
import java.util.List;

public class ExtractedEvidence {

    private List<String> exceptionNames = new ArrayList<>();
    private List<String> causedByChain = new ArrayList<>();
    private List<String> classNames = new ArrayList<>();
    private List<String> methodNames = new ArrayList<>();
    private List<String> frameworkIndicators = new ArrayList<>();
    private List<String> buildToolIndicators = new ArrayList<>();
    private List<String> testFrameworkIndicators = new ArrayList<>();
    private String rawExcerpt = "";

    public List<String> getExceptionNames() { return exceptionNames; }
    public void setExceptionNames(List<String> exceptionNames) { this.exceptionNames = exceptionNames; }

    public List<String> getCausedByChain() { return causedByChain; }
    public void setCausedByChain(List<String> causedByChain) { this.causedByChain = causedByChain; }

    public List<String> getClassNames() { return classNames; }
    public void setClassNames(List<String> classNames) { this.classNames = classNames; }

    public List<String> getMethodNames() { return methodNames; }
    public void setMethodNames(List<String> methodNames) { this.methodNames = methodNames; }

    public List<String> getFrameworkIndicators() { return frameworkIndicators; }
    public void setFrameworkIndicators(List<String> frameworkIndicators) { this.frameworkIndicators = frameworkIndicators; }

    public List<String> getBuildToolIndicators() { return buildToolIndicators; }
    public void setBuildToolIndicators(List<String> buildToolIndicators) { this.buildToolIndicators = buildToolIndicators; }

    public List<String> getTestFrameworkIndicators() { return testFrameworkIndicators; }
    public void setTestFrameworkIndicators(List<String> testFrameworkIndicators) { this.testFrameworkIndicators = testFrameworkIndicators; }

    public String getRawExcerpt() { return rawExcerpt; }
    public void setRawExcerpt(String rawExcerpt) { this.rawExcerpt = rawExcerpt; }
}
