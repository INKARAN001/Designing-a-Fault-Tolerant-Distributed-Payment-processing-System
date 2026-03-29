package com.dsproject.faulttolerance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Simple periodic failure detector.
 *
 * <p>How it works:
 * 1) Every CHECK_INTERVAL_MS, each peer is pinged using /health.
 * 2) A node is marked failed only after FAILURE_THRESHOLD consecutive misses
 *    (this avoids false alarms from one temporary network hiccup).
 * 3) If a failed node becomes healthy again, we mark it alive (recovery).
 */
public class FailureDetector {
    // Check peers once per second.
    private static final long CHECK_INTERVAL_MS = 1000;
    // Mark failure after N consecutive unsuccessful checks.
    private static final int FAILURE_THRESHOLD = 2;

    private final List<String> peerUrls;
    private final Consumer<String> onFailed;
    private final HttpClient client;
    private final Set<String> alive = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    // For each peer, how many consecutive health check failures happened.
    private final Map<String, Integer> consecutiveFailures = new HashMap<>();
    private volatile boolean running = false;

    public FailureDetector(List<String> peerUrls, Consumer<String> onFailed) {
        this.peerUrls = new ArrayList<>(peerUrls);
        this.onFailed = onFailed;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.alive.addAll(peerUrls);
        for (String url : peerUrls) {
            consecutiveFailures.put(url, 0);
        }
    }

    public void start() {
        running = true;
        Thread t = new Thread(this::loop, "failure-detector");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException ignored) {
            }
            for (String url : peerUrls) {
                if (isHealthy(url)) {
                    markHealthy(url);
                } else {
                    markUnhealthy(url);
                }
            }
        }
    }

    private boolean isHealthy(String baseUrl) {
        String u = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(u + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void markFailed(String url) {
        synchronized (this) {
            if (failed.contains(url)) {
                return;
            }
            failed.add(url);
            alive.remove(url);
        }
        onFailed.accept(url);
    }

    private void markHealthy(String url) {
        synchronized (this) {
            consecutiveFailures.put(url, 0);
            alive.add(url);
            failed.remove(url);
        }
    }

    private void markUnhealthy(String url) {
        boolean shouldMarkFailed = false;
        synchronized (this) {
            int misses = consecutiveFailures.getOrDefault(url, 0) + 1;
            consecutiveFailures.put(url, misses);
            if (misses >= FAILURE_THRESHOLD && !failed.contains(url)) {
                shouldMarkFailed = true;
            }
        }
        if (shouldMarkFailed) {
            markFailed(url);
        }
    }

    public synchronized List<String> alivePeers() {
        return new ArrayList<>(alive);
    }

    public synchronized List<String> failedPeers() {
        return new ArrayList<>(failed);
    }
}
