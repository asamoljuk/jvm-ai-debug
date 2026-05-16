package com.antonsamoljuk.jvmaidbg.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
        name = "version",
        description = "Print version information"
)
public class VersionCommand implements Callable<Integer> {

    static final String VERSION = "1.0.0";

    @Override
    public Integer call() {
        System.out.println("AI JVM Debug Assistant v" + VERSION);
        System.out.println("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        return 0;
    }
}
