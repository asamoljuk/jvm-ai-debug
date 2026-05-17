package com.antonsamoljuk.jvmaidbg.cli;

public class Spinner implements AutoCloseable {

    private static final char[] FRAMES = {'|', '/', '-', '\\'};
    private static final int INTERVAL_MS = 100;

    private final Thread thread;
    private volatile boolean running = true;

    public Spinner(String message) {
        boolean isTty = System.console() != null;
        thread = new Thread(() -> {
            if (!isTty) return;
            int frame = 0;
            while (running) {
                System.err.print("\r" + message + " " + FRAMES[frame++ % FRAMES.length]);
                System.err.flush();
                try {
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
            System.err.print("\r" + " ".repeat(message.length() + 2) + "\r");
            System.err.flush();
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        running = false;
        try {
            thread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
