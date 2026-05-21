package com.antonsamoljuk.jvmaidbg.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * User-supplied configuration loaded from JSON files. Every field is nullable —
 * a null field means "not set here, fall through to the next precedence layer".
 * Resolution order (highest wins): CLI flag &gt; env var &gt; project config &gt; global config &gt; built-in default.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {

    public String defaultProvider;
    public String openaiModel;
    public String anthropicModel;
    public String ollamaModel;
    public String ollamaBaseUrl;
    public Integer maxPromptChars;
    public Boolean cacheEnabled;
    public String rulesFile;

    public static AppSettings empty() {
        return new AppSettings();
    }

    /** Returns a new AppSettings where this object's non-null fields override {@code base}. */
    public AppSettings mergeOver(AppSettings base) {
        AppSettings merged = new AppSettings();
        merged.defaultProvider = firstNonNull(this.defaultProvider, base.defaultProvider);
        merged.openaiModel     = firstNonNull(this.openaiModel,     base.openaiModel);
        merged.anthropicModel  = firstNonNull(this.anthropicModel,  base.anthropicModel);
        merged.ollamaModel     = firstNonNull(this.ollamaModel,     base.ollamaModel);
        merged.ollamaBaseUrl   = firstNonNull(this.ollamaBaseUrl,   base.ollamaBaseUrl);
        merged.maxPromptChars  = firstNonNull(this.maxPromptChars,  base.maxPromptChars);
        merged.cacheEnabled    = firstNonNull(this.cacheEnabled,    base.cacheEnabled);
        merged.rulesFile       = firstNonNull(this.rulesFile,       base.rulesFile);
        return merged;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
