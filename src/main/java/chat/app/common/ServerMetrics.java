package main.java.chat.app.common;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.io.*;

/**
 * Aggregates server-side metrics.
 * - messageProcessingTimes (ns) recorded per-message
 * - bytesReceived / bytesSent counters
 * - messagesReceived / messagesSent counts
 */
public class ServerMetrics {
    private final List<Long> processingNs = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong messagesReceived = new AtomicLong();
    private final AtomicLong messagesSent = new AtomicLong();

    public void recordProcessingNs(long ns) { processingNs.add(ns); }
    public void addBytesReceived(long b){ bytesReceived.addAndGet(b); }
    public void addBytesSent(long b){ bytesSent.addAndGet(b); }
    public void incMessagesReceived(){ messagesReceived.incrementAndGet(); }
    public void incMessagesSent(){ messagesSent.incrementAndGet(); }

    public double medianProcessingMs() {
        synchronized(processingNs) {
            if (processingNs.isEmpty()) return Double.NaN;
            List<Long> copy = new ArrayList<>(processingNs);
            Collections.sort(copy);
            long mid = copy.get(copy.size()/2);
            return mid / 1_000_000.0;
        }
    }

    public void writeCsv(File out, String headerInfo) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("info," + headerInfo);
            pw.println("messagesReceived," + messagesReceived.get());
            pw.println("messagesSent," + messagesSent.get());
            pw.println("bytesReceived," + bytesReceived.get());
            pw.println("bytesSent," + bytesSent.get());
            pw.println("medianProcessingMs," + medianProcessingMs());
            pw.println("processingSamplesCount," + processingNs.size());
        }
    }
}
