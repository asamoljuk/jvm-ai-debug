package com.antonsamoljuk.jvmaidbg.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
        name = "version",
        description = "Print version information"
)
public class VersionCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("AI JVM Debug Assistant v" + VersionProvider.read());
        System.out.println("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        return 0;
    }
}
