package com.dsproject.timesync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NtpClientTest {

    @Test
    void ntpTimestamp_roundTrip() {
        byte[] buf = new byte[48];
        double unix = 1_700_000_000.0;
        NtpClient.writeNtpTimestamp(buf, 40, unix);
        double back = NtpClient.readNtpTimestampAsUnix(buf, 40);
        assertEquals(unix, back, 1e-5);
    }
}
