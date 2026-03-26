package com.dsproject.payment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Emulates payment processing (replaces Python {@code src/payment/processor.py}).
 */
public class PaymentProcessor {

    public PaymentRecord process(double amount, String currency, String idempotencyKey) {
        String transactionId = idempotencyKey != null && !idempotencyKey.isEmpty()
                ? idempotencyKey
                : UUID.randomUUID().toString();
        double ts = System.currentTimeMillis() / 1000.0;
        PaymentStatus status = mockProcess(amount);
        Map<String, Object> meta = new HashMap<>();
        meta.put("processor", "mock");
        return new PaymentRecord(transactionId, amount, currency, status, ts, -1, meta);
    }

    private PaymentStatus mockProcess(double amount) {
        if (amount > 1_000_000) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.SUCCESS;
    }
}
