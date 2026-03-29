package com.dsproject.faulttolerance;

import com.dsproject.util.HttpJson;

import java.time.Duration;
import java.util.List;

/**
 * Simple client-side failover helper.
 *
 * <p>Strategy:
 * 1) First, try to find a healthy leader node.
 * 2) If leader is not found, return any healthy node.
 * 3) If no node is healthy, throw an error.
 */
public final class Failover {

    private Failover() {
    }

    public static String getLeaderOrAny(List<String> nodeUrls) {
        for (String url : nodeUrls) {
            String base = normalize(url);
            if (isLeader(base)) {
                return base; // best case: send request directly to the leader
            }
        }

        // Fallback: if leader is unknown, at least talk to a healthy node.
        for (String url : nodeUrls) {
            String base = normalize(url);
            if (isHealthy(base)) {
                return base;
            }
        }
        throw new RuntimeException("No payment server available");
    }

    private static String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isLeader(String baseUrl) {
        var data = HttpJson.getJson(baseUrl + "/health", Duration.ofSeconds(1));
        if (data.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(data.get("is_leader"));
    }

    private static boolean isHealthy(String baseUrl) {
        var data = HttpJson.getJson(baseUrl + "/health", Duration.ofSeconds(1));
        return !data.isEmpty();
    }
}
