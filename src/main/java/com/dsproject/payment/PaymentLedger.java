package com.dsproject.payment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentLedger {
    private final List<PaymentRecord> records = new ArrayList<>();
    private final Map<String, PaymentRecord> byTransactionId = new HashMap<>();

    public synchronized boolean append(PaymentRecord record) {
        if (byTransactionId.containsKey(record.getTransactionId())) {
            return false;
        }
        records.add(record);
        byTransactionId.put(record.getTransactionId(), record);
        return true;
    }

    public synchronized PaymentRecord getByTransactionId(String transactionId) {
        return byTransactionId.get(transactionId);
    }

    public synchronized List<PaymentRecord> getAll() {
        return new ArrayList<>(records);
    }

    public synchronized List<PaymentRecord> getAllOrderedByLogicalIndex() {
        List<PaymentRecord> out = new ArrayList<>(records);
        out.sort(Comparator
                .comparingInt(PaymentRecord::getLogicalIndex)
                .thenComparingDouble(PaymentRecord::getTimestamp));
        return out;
    }

    public synchronized int size() {
        return records.size();
    }
}
