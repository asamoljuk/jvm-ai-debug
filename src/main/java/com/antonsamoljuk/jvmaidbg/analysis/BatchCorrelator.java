package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.IssueCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups per-file analysis outcomes by their root signature and reports clusters of
 * 2 or more files that share the same problem — likely a single root cause.
 */
public final class BatchCorrelator {

    /** A signature shared by multiple files. */
    public record Cluster(
            IssueCategory category,
            String topException,   // null if no exception was extracted
            int fileCount,
            List<String> files     // file names for display
    ) {
        /** Human-readable signature, e.g. "JVM_MEMORY_ERROR (OutOfMemoryError)". */
        public String signature() {
            return topException != null ? category + " (" + topException + ")" : category.toString();
        }
    }

    /** One entry per analyzed file. {@code topException} may be null. */
    public record Entry(String fileName, IssueCategory category, String topException) {}

    private BatchCorrelator() {}

    /**
     * Returns clusters where 2+ files share the same (category, topException) signature,
     * sorted by file count descending.
     */
    public static List<Cluster> correlate(List<Entry> entries) {
        if (entries == null || entries.size() < 2) return List.of();

        // Preserve discovery order within each group for stable file listing.
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.category() == null) continue;
            String key = e.category().name() + "|" + (e.topException() != null ? e.topException() : "");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<Cluster> clusters = new ArrayList<>();
        for (List<Entry> group : groups.values()) {
            if (group.size() < 2) continue;
            Entry first = group.get(0);
            List<String> files = group.stream().map(Entry::fileName).toList();
            clusters.add(new Cluster(first.category(), first.topException(), group.size(), files));
        }
        clusters.sort(Comparator.comparingInt(Cluster::fileCount).reversed());
        return clusters;
    }
}
