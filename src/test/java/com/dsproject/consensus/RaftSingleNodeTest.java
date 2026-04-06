package com.dsproject.consensus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Raft leader election and commit with no peers (single-node “cluster”).
 */
class RaftSingleNodeTest {

    @Test
    void proposeCommitsWhenRunningAlone() throws Exception {
        List<Map<String, Object>> committed = new ArrayList<>();
        RaftNode node = new RaftNode("solo", List.of(), committed::add);
        node.start();
        Thread.sleep(1600);
        assertTrue(node.isLeader(), "single node should elect itself");

        Map<String, Object> data = new HashMap<>();
        data.put("transaction_id", "tx-solo-1");
        data.put("amount", 42.0);
        data.put("currency", "USD");
        data.put("status", "success");
        data.put("timestamp", 1.0);
        data.put("logical_index", -1);
        data.put("metadata", new HashMap<String, Object>());

        assertTrue(node.propose(data));
        assertEquals(1, committed.size());
        assertEquals("tx-solo-1", committed.get(0).get("transaction_id"));

        node.stop();
    }
}
