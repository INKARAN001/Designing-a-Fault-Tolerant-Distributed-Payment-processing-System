package com.dsproject.timesync;

import com.dsproject.payment.PaymentRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TimeSync {
    private static volatile Double ntpOffsetSeconds = null;
    private static volatile Thread ntpSyncThread;

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

    /**
     * Periodically queries an NTP/SNTP host and updates {@link #setNtpOffset(Double)}.
     * Failures are logged; previous offset is kept until the next successful sync.
     */
    public static void startBackgroundNtpSync(String ntpHost, int intervalMs) {
        if (ntpSyncThread != null && ntpSyncThread.isAlive()) {
            return;
        }
        ntpSyncThread = new Thread(() -> {
            var log = System.getLogger(TimeSync.class.getName());
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    double offset = NtpClient.queryOffsetSeconds(ntpHost, 4000);
                    setNtpOffset(offset);
                    log.log(System.Logger.Level.INFO, "NTP offset updated: {0} s (server {1})", offset, ntpHost);
                } catch (Exception e) {
                    log.log(System.Logger.Level.WARNING, "NTP sync failed ({0}): {1}", ntpHost, e.getMessage());
                }
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ntp-sync");
        ntpSyncThread.setDaemon(true);
        ntpSyncThread.start();
    }

    public static List<PaymentRecord> reorderByTimestamp(List<PaymentRecord> records) {
        List<PaymentRecord> out = new ArrayList<>(records);
        out.sort(Comparator
                .comparingDouble(PaymentRecord::getTimestamp)
                .thenComparingInt(PaymentRecord::getLogicalIndex));
        return out;
    }
}
