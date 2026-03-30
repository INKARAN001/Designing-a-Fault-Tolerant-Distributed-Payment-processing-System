package com.dsproject.payment;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class PaymentRecord {
    private final String transactionId;
    private final double amount;
    private final String currency;
    private final PaymentStatus status;
    private final double timestamp;
    private int logicalIndex;
    private final Map<String, Object> metadata;

    public PaymentRecord(
            String transactionId,
            double amount,
            String currency,
            PaymentStatus status,
            double timestamp,
            int logicalIndex,
            Map<String, Object> metadata
    ) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.timestamp = timestamp;
        this.logicalIndex = logicalIndex;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public int getLogicalIndex() {
        return logicalIndex;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public void setLogicalIndex(int logicalIndex) {
        this.logicalIndex = logicalIndex;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("transaction_id", transactionId);
        m.put("amount", amount);
        m.put("currency", currency);
        m.put("status", status.name().toLowerCase());
        m.put("timestamp", timestamp);
        m.put("logical_index", logicalIndex);
        m.put("metadata", new HashMap<>(metadata));
        return m;
    }

    public static PaymentRecord fromMap(Map<String, Object> d) {
        String txId = stringVal(d.get("transaction_id"));
        double amount = numberToDouble(d.get("amount"));
        String currency = stringVal(d.get("currency"));
        if (currency == null) {
            currency = "USD";
        }
        PaymentStatus status = PaymentStatus.fromString(stringVal(d.get("status")));
        double ts = numberToDouble(d.get("timestamp"));
        int logical = (int) Math.round(numberToDouble(d.get("logical_index")));
        Object metaObj = d.get("metadata");
        Map<String, Object> meta = new HashMap<>();
        if (metaObj instanceof Map) {
            meta.putAll((Map<String, Object>) metaObj);
        }
        return new PaymentRecord(txId, amount, currency, status, ts, logical, meta);
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double numberToDouble(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return Double.parseDouble(String.valueOf(o));
    }
}
