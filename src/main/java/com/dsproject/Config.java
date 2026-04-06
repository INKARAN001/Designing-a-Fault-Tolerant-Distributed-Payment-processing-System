package com.dsproject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared configuration (replaces Python {@code src/config.py}).
 */
public final class Config {

    // Heartbeat must be much smaller than election timeout for stable leadership.
    // Tuned for faster demo failover after a node crash (sub-second election typical).
    public static final double HEARTBEAT_INTERVAL_SEC = 0.08;
    public static final int ELECTION_TIMEOUT_MIN_MS = 220;
    public static final int ELECTION_TIMEOUT_MAX_MS = 380;
    public static final int REPLICATION_QUORUM = 2;
    public static final String NTP_SERVER = "pool.ntp.org";
    /** Minimum interval between NTP queries (ms); default 10 minutes. */
    public static final int NTP_SYNC_INTERVAL_MS = 600_000;
    public static final int LOG_REORDER_BUFFER_SIZE = 1000;

    private Config() {
    }

    public static List<String> getNodeUrls(String host, int numNodes, int basePort) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            urls.add("http://" + host + ":" + (basePort + i));
        }
        return urls;
    }
}
