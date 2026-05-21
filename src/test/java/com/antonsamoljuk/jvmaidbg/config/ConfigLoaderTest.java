package com.antonsamoljuk.jvmaidbg.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void missingFilesReturnEmptySettings(@TempDir Path tempDir) {
        Path global = tempDir.resolve("no-global.json");
        Path project = tempDir.resolve("no-project.json");
        AppSettings result = ConfigLoader.load(global, project);
        assertNull(result.defaultProvider);
        assertNull(result.maxPromptChars);
        assertNull(result.cacheEnabled);
    }

    @Test
    void loadsAllFieldsFromGlobalFile(@TempDir Path tempDir) throws IOException {
        Path global = tempDir.resolve("config.json");
        Files.writeString(global, """
                {
                  "defaultProvider": "ollama",
                  "openaiModel": "gpt-4o",
                  "anthropicModel": "claude-opus-4",
                  "ollamaModel": "codellama",
                  "ollamaBaseUrl": "http://10.0.0.5:11434",
                  "maxPromptChars": 5000,
                  "cacheEnabled": false,
                  "rulesFile": "/etc/jvm-rules.json"
                }
                """);
        AppSettings result = ConfigLoader.load(global, tempDir.resolve("nothere.json"));
        assertEquals("ollama", result.defaultProvider);
        assertEquals("gpt-4o", result.openaiModel);
        assertEquals("claude-opus-4", result.anthropicModel);
        assertEquals("codellama", result.ollamaModel);
        assertEquals("http://10.0.0.5:11434", result.ollamaBaseUrl);
        assertEquals(5000, result.maxPromptChars);
        assertEquals(false, result.cacheEnabled);
        assertEquals("/etc/jvm-rules.json", result.rulesFile);
    }

    @Test
    void projectFileOverridesGlobal(@TempDir Path tempDir) throws IOException {
        Path global = tempDir.resolve("global.json");
        Path project = tempDir.resolve("project.json");
        Files.writeString(global, """
                { "defaultProvider": "openai", "openaiModel": "gpt-4o-mini", "maxPromptChars": 2500 }
                """);
        Files.writeString(project, """
                { "defaultProvider": "ollama", "ollamaModel": "llama3.1" }
                """);
        AppSettings result = ConfigLoader.load(global, project);
        // Project wins on defaultProvider
        assertEquals("ollama", result.defaultProvider);
        // Project adds ollamaModel
        assertEquals("llama3.1", result.ollamaModel);
        // Global values pass through when project doesn't set them
        assertEquals("gpt-4o-mini", result.openaiModel);
        assertEquals(2500, result.maxPromptChars);
    }

    @Test
    void corruptJsonFallsBackToEmpty(@TempDir Path tempDir) throws IOException {
        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, "{ this is not json");
        AppSettings result = ConfigLoader.load(bad, tempDir.resolve("missing.json"));
        assertNull(result.defaultProvider);
    }

    @Test
    void unknownPropertiesAreIgnored(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("c.json");
        Files.writeString(file, """
                { "futureField": "ignored", "defaultProvider": "anthropic" }
                """);
        AppSettings result = ConfigLoader.load(file, tempDir.resolve("none.json"));
        assertEquals("anthropic", result.defaultProvider);
    }

    @Test
    void mergeOverPreservesBothLayers() {
        AppSettings global = new AppSettings();
        global.defaultProvider = "openai";
        global.maxPromptChars = 1000;
        AppSettings project = new AppSettings();
        project.defaultProvider = "ollama";  // overrides
        // project does not set maxPromptChars
        AppSettings merged = project.mergeOver(global);
        assertEquals("ollama", merged.defaultProvider);
        assertEquals(1000, merged.maxPromptChars);
    }
}
