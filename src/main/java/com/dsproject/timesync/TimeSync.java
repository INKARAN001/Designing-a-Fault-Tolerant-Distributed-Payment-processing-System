package com.dsproject.timesync;

import com.dsproject.payment.PaymentRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TimeSync {
    private static volatile Double ntpOffsetSeconds = null;

    public static double getCorrectedTimestamp() {
        double nowSeconds = System.currentTimeMillis() / 1000.0;
        if (ntpOffsetSeconds == null) {
            return nowSeconds;
        }
        return nowSeconds + ntpOffsetSeconds;
    }

    public static void setNtpOffset(Double offsetSeconds) {
        ntpOffsetSeconds = offsetSeconds;
    }

    public static List<PaymentRecord> reorderByTimestamp(List<PaymentRecord> records) {
        List<PaymentRecord> out = new ArrayList<>(records);
        out.sort(Comparator
                .comparingDouble(PaymentRecord::getTimestamp)
                .thenComparingInt(PaymentRecord::getLogicalIndex));
        return out;
    }
}
