package com.antonsamoljuk.jvmaidbg.cli;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[]{"AI JVM Debug Assistant v" + read()};
    }

    /** Reads Implementation-Version from the JAR manifest. Returns "dev" when run from IDE/classes. */
    public static String read() {
        String v = VersionProvider.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
