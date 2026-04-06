package com.dsproject.faulttolerance;

import com.dsproject.util.HttpJson;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Simple client-side failover helper.
 *
 * <p>Strategy: return a node whose {@code /health} reports {@code is_leader=true} and is not
 * in simulated crash mode. Never returns a follower (avoids forwarded-payment proxy loops).
 */
public final class Failover {

    private Failover() {
    }

    public static String getLeaderOrAny(List<String> nodeUrls) {
        for (String url : nodeUrls) {
            String base = normalize(url);
            if (isLeader(base)) {
                return base;
            }
        }
        throw new RuntimeException("No leader available");
    }

    private static String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isLeader(String baseUrl) {
        var data = HttpJson.getJson(baseUrl + "/health", Duration.ofSeconds(1));
        if (data.isEmpty() || isCrashedOrUnavailable(data)) {
            return false;
        }
        return Boolean.TRUE.equals(data.get("is_leader"));
    }

    private static boolean isCrashedOrUnavailable(Map<String, Object> health) {
        if (Boolean.TRUE.equals(health.get("simulated_crash"))) {
            return true;
        }
        return "crashed".equalsIgnoreCase(String.valueOf(health.get("status")));
    }
}
