package com.antonsamoljuk.jvmaidbg.ai;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

public class OllamaLauncher {

    private static final int CONNECT_CHECK_TIMEOUT_MS = 2_000;
    private static final int MAX_WAIT_SECONDS = 30;
    private static final int POLL_INTERVAL_MS = 500;
    private static final char[] FRAMES = {'|', '/', '-', '\\'};

    private OllamaLauncher() {}

    public static void ensureRunning(String baseUrl) {
        if (isReachable(baseUrl)) return;

        System.err.println("Ollama is not running — starting...");
        launch();
        waitUntilReady(baseUrl);
        System.err.println("Ollama ready.");
    }

    public static boolean isReachable(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), CONNECT_CHECK_TIMEOUT_MS);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static void launch() {
        try {
            new ProcessBuilder("ollama", "serve")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not start Ollama. Make sure 'ollama' is installed and on your PATH.", e);
        }
    }

    private static void waitUntilReady(String baseUrl) {
        boolean isTty = System.console() != null;
        int maxAttempts = (MAX_WAIT_SECONDS * 1000) / POLL_INTERVAL_MS;
        for (int i = 0; i < maxAttempts; i++) {
            if (isTty) {
                System.err.print("\rWaiting for Ollama... " + FRAMES[i % FRAMES.length]);
                System.err.flush();
            }
            if (isReachable(baseUrl)) {
                if (isTty) {
                    System.err.print("\r" + " ".repeat(30) + "\r");
                    System.err.flush();
                }
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Ollama to start", e);
            }
        }
        throw new RuntimeException("Ollama did not become ready within " + MAX_WAIT_SECONDS + " seconds");
    }
}
