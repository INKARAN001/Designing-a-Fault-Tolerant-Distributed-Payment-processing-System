package com.dsproject.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentLedgerTest {

    @Test
    void appendAndDedup() {
        PaymentLedger ledger = new PaymentLedger();
        PaymentRecord r1 = new PaymentRecord("tx1", 10.0, "USD", PaymentStatus.SUCCESS, 1000.0, 0, null);
        assertTrue(ledger.append(r1));
        assertFalse(ledger.append(r1));
        assertEquals(1, ledger.size());
        assertSame(r1, ledger.getByTransactionId("tx1"));
    }

    @Test
    void orderingByLogicalIndex() {
        PaymentLedger ledger = new PaymentLedger();
        for (int i : new int[]{2, 0, 1}) {
            PaymentRecord r = new PaymentRecord("tx" + i, i, "USD", PaymentStatus.SUCCESS, 1000.0 + i, i, null);
            ledger.append(r);
        }
        var ordered = ledger.getAllOrderedByLogicalIndex();
        assertEquals(0, ordered.get(0).getLogicalIndex());
        assertEquals(1, ordered.get(1).getLogicalIndex());
        assertEquals(2, ordered.get(2).getLogicalIndex());
    }
}
