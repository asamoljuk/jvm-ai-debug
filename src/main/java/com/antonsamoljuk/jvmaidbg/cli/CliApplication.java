package com.antonsamoljuk.jvmaidbg.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "jvm-ai-debug",
        description = "AI-powered JVM debug assistant — analyzes stack traces, build logs, and test failures.",
        mixinStandardHelpOptions = true,
        version = "AI JVM Debug Assistant v" + VersionCommand.VERSION,
        subcommands = {
                AnalyzeCommand.class,
                VersionCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class CliApplication {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliApplication()).execute(args);
        System.exit(exitCode);
    }
}
