package com.dsproject.timesync;

import com.dsproject.payment.PaymentRecord;
import com.dsproject.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSyncTest {

    @Test
    void reorderByTimestamp() {
        List<PaymentRecord> records = new ArrayList<>();
        records.add(new PaymentRecord("a", 1, "USD", PaymentStatus.SUCCESS, 100, 1, new HashMap<>()));
        records.add(new PaymentRecord("b", 1, "USD", PaymentStatus.SUCCESS, 50, 0, new HashMap<>()));
        records.add(new PaymentRecord("c", 1, "USD", PaymentStatus.SUCCESS, 100, 2, new HashMap<>()));
        List<PaymentRecord> out = TimeSync.reorderByTimestamp(records);
        assertEquals(50, out.get(0).getTimestamp());
        assertEquals(100, out.get(1).getTimestamp());
        assertEquals(1, out.get(1).getLogicalIndex());
        assertEquals(100, out.get(2).getTimestamp());
        assertEquals(2, out.get(2).getLogicalIndex());
    }

    @Test
    void correctedTimestamp() {
        TimeSync.setNtpOffset(0.1);
        double t = TimeSync.getCorrectedTimestamp();
        TimeSync.setNtpOffset(null);
        assertTrue(t > 0);
    }
}
