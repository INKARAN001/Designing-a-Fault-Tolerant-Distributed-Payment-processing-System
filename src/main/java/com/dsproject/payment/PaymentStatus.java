package com.dsproject.payment;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED;

    public static PaymentStatus fromString(String s) {
        if (s == null || s.isEmpty()) {
            return PENDING;
        }
        return PaymentStatus.valueOf(s.trim().toUpperCase());
    }
}
