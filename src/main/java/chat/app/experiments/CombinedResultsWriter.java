package main.java.chat.app.experiments;

import main.java.chat.app.common.Metrics;

import java.io.*;
import java.util.*;
// import java.util.stream.Collectors;

public class CombinedResultsWriter {

    public static void writeCombinedCsv(File outDir, ScenarioConfig cfg, List<Metrics> metricsList) {
        File out = new File(outDir, "combined_summary.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("scenario," + cfg.name);
            pw.println("transport," + cfg.transport);
            pw.println("clients," + cfg.clients);
            pw.println("latencyMs," + cfg.latencyMs);
            pw.println("lossProb," + cfg.lossProb);
            pw.println();

            long totalBytesSent = metricsList.stream().mapToLong(m -> {
                try {
                    java.lang.reflect.Field f = Metrics.class.getDeclaredField("bytesSent");
                    f.setAccessible(true);
                    java.util.concurrent.atomic.AtomicLong al = (java.util.concurrent.atomic.AtomicLong) f.get(m);
                    return al.get();
                } catch (Exception e) { return 0L; }
            }).sum();

            long totalBytesReceived = metricsList.stream().mapToLong(m -> {
                try {
                    java.lang.reflect.Field f = Metrics.class.getDeclaredField("bytesReceived");
                    f.setAccessible(true);
                    java.util.concurrent.atomic.AtomicLong al = (java.util.concurrent.atomic.AtomicLong) f.get(m);
                    return al.get();
                } catch (Exception e) { return 0L; }
            }).sum();

            long totalMessagesSent = metricsList.stream().mapToLong(m -> {
                try {
                    java.lang.reflect.Field f = Metrics.class.getDeclaredField("messagesSent");
                    f.setAccessible(true);
                    java.util.concurrent.atomic.AtomicLong al = (java.util.concurrent.atomic.AtomicLong) f.get(m);
                    return al.get();
                } catch (Exception e) { return 0L; }
            }).sum();

            long totalMessagesReceived = metricsList.stream().mapToLong(m -> {
                try {
                    java.lang.reflect.Field f = Metrics.class.getDeclaredField("messagesReceived");
                    f.setAccessible(true);
                    java.util.concurrent.atomic.AtomicLong al = (java.util.concurrent.atomic.AtomicLong) f.get(m);
                    return al.get();
                } catch (Exception e) { return 0L; }
            }).sum();

            pw.println("totalBytesSent," + totalBytesSent);
            pw.println("totalBytesReceived," + totalBytesReceived);
            pw.println("totalMessagesSent," + totalMessagesSent);
            pw.println("totalMessagesReceived," + totalMessagesReceived);

            // compute aggregate RTT stats: combine all client RTT samples in memory
            List<Double> allRttsMs = new ArrayList<>();
            for (Metrics m : metricsList) {
                try {
                    java.lang.reflect.Field f = Metrics.class.getDeclaredField("rttSamples");
                    f.setAccessible(true);
                    List<Long> samples = (List<Long>) f.get(m);
                    synchronized (samples) {
                        for (Long ns : samples) allRttsMs.add(ns / 1_000_000.0);
                    }
                } catch (Exception e) {}
            }
            Collections.sort(allRttsMs);
            if (!allRttsMs.isEmpty()) {
                double median = percentile(allRttsMs, 50);
                double p95 = percentile(allRttsMs, 95);
                double mean = allRttsMs.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                pw.println("rtt_median_ms," + median);
                pw.println("rtt_p95_ms," + p95);
                pw.println("rtt_mean_ms," + mean);
                pw.println("rtt_samples," + allRttsMs.size());
            } else {
                pw.println("rtt_median_ms,");
                pw.println("rtt_p95_ms,");
                pw.println("rtt_mean_ms,");
                pw.println("rtt_samples,0");
            }

            System.out.println("Wrote combined CSV: " + out.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double percentile(List<Double> sorted, double pct) {
        if (sorted.isEmpty()) return Double.NaN;
        double pos = pct / 100.0 * (sorted.size() - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        if (upper == lower) return sorted.get(lower);
        double weight = pos - lower;
        return sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
    }
}
