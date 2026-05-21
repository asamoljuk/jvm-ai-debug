package com.antonsamoljuk.jvmaidbg.cli;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Tails a growing log file, accumulating new bytes, and emits an analysis-worthy chunk
 * whenever it sees stack-trace markers followed by a quiet period (debounce).
 *
 * <p>Designed to be cancelled via thread interrupt — Ctrl-C in the CLI handler.
 */
public class LogTailer {

    private static final int POLL_INTERVAL_MS = 500;
    private static final int READ_BUFFER_SIZE = 8 * 1024;

    private final Path file;
    private final long debounceMillis;
    private final Consumer<String> onChunk;

    public LogTailer(Path file, long debounceMillis, Consumer<String> onChunk) {
        this.file = file;
        this.debounceMillis = debounceMillis;
        this.onChunk = onChunk;
    }

    /** Blocks until interrupted. Each detected stack-trace chunk is passed to the consumer. */
    public void tail() throws IOException, InterruptedException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(raf.length()); // start at end — react only to new content

            StringBuilder buffer = new StringBuilder();
            long lastActivity = 0;

            while (!Thread.currentThread().isInterrupted()) {
                long currentLen = raf.length();
                if (currentLen < raf.getFilePointer()) {
                    // File was truncated or rotated — restart from the beginning.
                    raf.seek(0);
                }

                String chunk = readNew(raf);
                if (!chunk.isEmpty()) {
                    buffer.append(chunk);
                    lastActivity = System.currentTimeMillis();
                } else {
                    boolean quiet = System.currentTimeMillis() - lastActivity > debounceMillis;
                    if (quiet && buffer.length() > 0 && looksLikeStackTrace(buffer)) {
                        onChunk.accept(buffer.toString());
                        buffer.setLength(0);
                    }
                    Thread.sleep(POLL_INTERVAL_MS);
                }
            }
        }
    }

    private static String readNew(RandomAccessFile raf) throws IOException {
        long available = raf.length() - raf.getFilePointer();
        if (available <= 0) return "";
        int toRead = (int) Math.min(available, READ_BUFFER_SIZE);
        byte[] bytes = new byte[toRead];
        int n = raf.read(bytes);
        return n > 0 ? new String(bytes, 0, n, StandardCharsets.UTF_8) : "";
    }

    /** Heuristic: an exception name plus at least one stack frame. */
    static boolean looksLikeStackTrace(CharSequence text) {
        String s = text.toString();
        boolean hasException = s.contains("Exception") || s.contains("Error");
        boolean hasFrame = s.contains("\tat ") || s.contains("    at ");
        return hasException && hasFrame;
    }
}
