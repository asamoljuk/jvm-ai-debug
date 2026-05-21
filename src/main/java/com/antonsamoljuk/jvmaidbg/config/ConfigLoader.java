package com.antonsamoljuk.jvmaidbg.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads {@link AppSettings} from JSON files. Global config (~/.jvm-ai-debug/config.json) is
 * read first; project config (./.jvm-ai-debug.json) is read second and overrides global.
 */
public final class ConfigLoader {

    public static final Path GLOBAL_PATH =
            Path.of(System.getProperty("user.home"), ".jvm-ai-debug", "config.json");
    public static final Path PROJECT_PATH = Path.of(".jvm-ai-debug.json");

    private ConfigLoader() {}

    /** Loads both global and project configs and merges them. */
    public static AppSettings load() {
        return load(GLOBAL_PATH, PROJECT_PATH);
    }

    /** Returns the list of config files that were actually found and loaded, for diagnostics. */
    public static List<Path> loadedSources() {
        List<Path> sources = new ArrayList<>();
        if (Files.exists(GLOBAL_PATH)) sources.add(GLOBAL_PATH);
        if (Files.exists(PROJECT_PATH)) sources.add(PROJECT_PATH);
        return sources;
    }

    static AppSettings load(Path globalPath, Path projectPath) {
        AppSettings global  = readFile(globalPath);
        AppSettings project = readFile(projectPath);
        return project.mergeOver(global);
    }

    private static AppSettings readFile(Path path) {
        if (path == null || !Files.exists(path)) return AppSettings.empty();
        try {
            return new ObjectMapper().readValue(path.toFile(), AppSettings.class);
        } catch (IOException e) {
            System.err.println("Warning: could not read config " + path + ": " + e.getMessage());
            return AppSettings.empty();
        }
    }
}
