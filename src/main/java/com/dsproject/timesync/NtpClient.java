package com.dsproject.timesync;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Minimal SNTP client (RFC 5905-style) over UDP port 123.
 * Computes clock offset in seconds: value to add to local Unix time to align with server.
 */
public final class NtpClient {

    /** Seconds from 1900-01-01 to 1970-01-01 UTC. */
    static final long NTP_UNIX_EPOCH_DIFF = 2208988800L;

    private NtpClient() {
    }

    /**
     * Queries an NTP/SNTP server and returns estimated offset (NTP time minus local Unix seconds at receive).
     * Ignores network delay; good enough for logging correlation in a course prototype.
     */
    public static double queryOffsetSeconds(String host, int timeoutMs) throws IOException {
        InetAddress addr = InetAddress.getByName(host);
        byte[] buf = new byte[48];
        buf[0] = 0x23; // LI=0, VN=4, mode=3 (client)
        double t1unix = System.currentTimeMillis() / 1000.0;
        writeNtpTimestamp(buf, 40, t1unix);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            DatagramPacket request = new DatagramPacket(buf, buf.length, addr, 123);
            socket.send(request);
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
            double t4unix = System.currentTimeMillis() / 1000.0;
            double t3unix = readNtpTimestampAsUnix(buf, 40);
            return t3unix - t4unix;
        }
    }

    static void writeNtpTimestamp(byte[] buf, int offset, double unixSeconds) {
        double ntp = unixSeconds + NTP_UNIX_EPOCH_DIFF;
        long secs = (long) Math.floor(ntp);
        long frac = Math.round((ntp - secs) * 0x1_0000_0000L) & 0xffff_ffffL;
        putU32(buf, offset, secs);
        putU32(buf, offset + 4, frac);
    }

    static double readNtpTimestampAsUnix(byte[] buf, int offset) {
        long secs = readU32(buf, offset);
        long frac = readU32(buf, offset + 4);
        return secs - NTP_UNIX_EPOCH_DIFF + (frac & 0xffff_ffffL) / 4294967296.0;
    }

    private static void putU32(byte[] buf, int offset, long v) {
        buf[offset] = (byte) (v >> 24);
        buf[offset + 1] = (byte) (v >> 16);
        buf[offset + 2] = (byte) (v >> 8);
        buf[offset + 3] = (byte) v;
    }

    private static long readU32(byte[] buf, int offset) {
        return ((buf[offset] & 0xffL) << 24)
                | ((buf[offset + 1] & 0xffL) << 16)
                | ((buf[offset + 2] & 0xffL) << 8)
                | (buf[offset + 3] & 0xffL);
    }
}
