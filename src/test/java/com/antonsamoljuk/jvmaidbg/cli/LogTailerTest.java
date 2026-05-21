package com.antonsamoljuk.jvmaidbg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LogTailerTest {

    @Test
    void looksLikeStackTraceRequiresExceptionAndFrame() {
        assertTrue(LogTailer.looksLikeStackTrace("NullPointerException\n\tat foo.Bar.baz(Bar.java:10)"));
        assertTrue(LogTailer.looksLikeStackTrace("java.lang.Error: oops\n    at qux"));
        assertFalse(LogTailer.looksLikeStackTrace("just some log line, no stack"));
        assertFalse(LogTailer.looksLikeStackTrace("NullPointerException without any stack frame"));
        assertFalse(LogTailer.looksLikeStackTrace("\tat com.example.Foo — frame without exception"));
    }

    @Test
    void emitsChunkAfterDebouncedStackTrace(@TempDir Path tempDir) throws Exception {
        Path log = tempDir.resolve("live.log");
        Files.writeString(log, "initial line\n"); // pre-existing content — tailer should skip it

        List<String> chunks = new ArrayList<>();
        CountDownLatch received = new CountDownLatch(1);
        LogTailer tailer = new LogTailer(log, 200, chunk -> {
            chunks.add(chunk);
            received.countDown();
        });

        Thread tailerThread = new Thread(() -> {
            try {
                tailer.tail();
            } catch (Exception ignored) {}
        });
        tailerThread.setDaemon(true);
        tailerThread.start();

        // Give tailer time to seek to end
        Thread.sleep(300);

        // Append a stack trace
        Files.writeString(log,
                "RuntimeException: boom\n\tat com.example.Foo.bar(Foo.java:42)\n",
                StandardOpenOption.APPEND);

        // Wait for the debounced chunk
        assertTrue(received.await(3, TimeUnit.SECONDS), "expected chunk within timeout");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("RuntimeException"));
        assertTrue(chunks.get(0).contains("Foo.bar"));
        // Pre-existing line should NOT be in the chunk (tailer started at end)
        assertFalse(chunks.get(0).contains("initial line"));

        tailerThread.interrupt();
    }

    @Test
    void doesNotEmitWithoutStackTraceMarkers(@TempDir Path tempDir) throws Exception {
        Path log = tempDir.resolve("plain.log");
        Files.writeString(log, "");

        List<String> chunks = new ArrayList<>();
        LogTailer tailer = new LogTailer(log, 200, chunks::add);

        Thread tailerThread = new Thread(() -> {
            try { tailer.tail(); } catch (Exception ignored) {}
        });
        tailerThread.setDaemon(true);
        tailerThread.start();

        Thread.sleep(200);
        Files.writeString(log, "INFO server started\nINFO ready\n", StandardOpenOption.APPEND);

        // Wait past the debounce window
        Thread.sleep(1000);
        assertTrue(chunks.isEmpty(), "non-stack-trace content must not trigger analysis");
        tailerThread.interrupt();
    }
}
