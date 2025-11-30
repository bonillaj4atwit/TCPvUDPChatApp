package main.java.chat.app.common;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.sun.management.OperatingSystemMXBean;

/**
 * Thread-safe metrics collector.
 * - Records RTT samples (ns)
 * - Counts bytes/messages sent/received
 * - Records emulator-drop counts (simulated loss)
 * - Exports a CSV-like file.
 */
public class Metrics {
    private final List<Long> rttSamples = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong messagesSent = new AtomicLong();
    private final AtomicLong messagesReceived = new AtomicLong();
    private final AtomicLong emulatorDrops = new AtomicLong();

    private final OperatingSystemMXBean osBean;

    public Metrics() {
        OperatingSystemMXBean b = null;
        try {
            b = (OperatingSystemMXBean) ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        } catch (Throwable t) {
            // fallback if not available
        }
        this.osBean = b;
    }

    public void recordRTT(long rttNs) { rttSamples.add(rttNs); }
    public void addBytesSent(long b){ bytesSent.addAndGet(b); }
    public void addBytesReceived(long b){ bytesReceived.addAndGet(b); }
    public void incMessagesSent(){ messagesSent.incrementAndGet(); }
    public void incMessagesReceived(){ messagesReceived.incrementAndGet(); }
    public void incEmulatorDrop(){ emulatorDrops.incrementAndGet(); }

    public double getProcessCpuPercent() {
        if (osBean == null) return Double.NaN;
        double v = osBean.getProcessCpuLoad();
        if (v < 0) return Double.NaN;
        return v * 100.0;
    }

    /**
     * Write a small CSV-like file with metrics and RTT samples (ms).
     */
    public void writeCsv(File f, String headerInfo) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.println("info," + headerInfo);
            pw.println("messagesSent," + messagesSent.get());
            pw.println("messagesReceived," + messagesReceived.get());
            pw.println("bytesSent," + bytesSent.get());
            pw.println("bytesReceived," + bytesReceived.get());
            pw.println("emulatorDrops," + emulatorDrops.get());
            pw.println("processCpuPercent," + getProcessCpuPercent());

            pw.println("rttSamplesCount," + rttSamples.size());
            synchronized (rttSamples) {
                for (long ns : rttSamples) {
                    pw.println(ns / 1_000_000.0); // ms
                }
            }
        }
    }
}
