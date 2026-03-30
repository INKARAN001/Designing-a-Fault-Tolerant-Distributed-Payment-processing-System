package com.dsproject.replication;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicatedLedgerTest {

    @Test
    void applyCommittedDedup() {
        ReplicatedLedger rep = new ReplicatedLedger();
        Map<String, Object> data = new HashMap<>();
        data.put("transaction_id", "tid1");
        data.put("amount", 5.0);
        data.put("currency", "USD");
        data.put("status", "success");
        data.put("timestamp", 1000.0);
        data.put("logical_index", -1);
        data.put("metadata", new HashMap<String, Object>());
        assertTrue(rep.applyCommitted(data));
        assertFalse(rep.applyCommitted(data));
        assertEquals(1, rep.size());
    }
}
