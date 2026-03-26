package com.dsproject.replication;

import com.dsproject.payment.PaymentLedger;
import com.dsproject.payment.PaymentRecord;

import java.util.List;
import java.util.Map;

public class ReplicatedLedger {
    private final PaymentLedger ledger = new PaymentLedger();
    private int nextLogicalIndex = 0;

    public synchronized boolean applyCommitted(Map<String, Object> data) {
        if (data == null || data.get("transaction_id") == null) {
            return false;
        }
        if (ledger.getByTransactionId(String.valueOf(data.get("transaction_id"))) != null) {
            return false;
        }
        PaymentRecord record = PaymentRecord.fromMap(data);
        record.setLogicalIndex(nextLogicalIndex++);
        return ledger.append(record);
    }

    public synchronized List<PaymentRecord> getAll(boolean ordered) {
        return ordered ? ledger.getAllOrderedByLogicalIndex() : ledger.getAll();
    }

    public synchronized PaymentRecord getByTransactionId(String txId) {
        return ledger.getByTransactionId(txId);
    }

    public synchronized int size() {
        return ledger.size();
    }
}
