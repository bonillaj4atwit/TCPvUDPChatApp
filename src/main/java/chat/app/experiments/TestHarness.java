package main.java.chat.app.experiments;

import main.java.chat.app.common.NetworkEmulator;
import main.java.chat.app.common.Metrics;
import main.java.chat.app.tcp.TcpServerNio;
import main.java.chat.app.tcp.TcpClientNio;
import main.java.chat.app.udp.UdpServerNio;
import main.java.chat.app.udp.UdpClientNio;

import java.io.File;
import java.util.*;
// import java.util.concurrent.*;

/**
 * Simple test harness that starts a server (tcp/udp), spawns N clients,
 * runs for duration seconds, then writes per-client CSV metrics to results/.
 *
 * Example usage (run main) - modify parameters below or adapt to parse CLI args.
 */
public class TestHarness {

    public static void main(String[] args) throws Exception {
        // Default scenario parameters - adapt or read from CLI/JSON
        String transport = "tcp"; // "tcp" or "udp"
        int port = 9000;
        int clientCount = 10;
        int durationSec = 20;
        int meanLatencyMs = 50;
        int jitterMs = 10;
        double lossProb = 0.02;

        // quick arg parsing (optional)
        for (String a : args) {
            if (a.startsWith("--transport=")) transport = a.split("=")[1];
            if (a.startsWith("--clients=")) clientCount = Integer.parseInt(a.split("=")[1]);
            if (a.startsWith("--duration=")) durationSec = Integer.parseInt(a.split("=")[1]);
            if (a.startsWith("--latency=")) meanLatencyMs = Integer.parseInt(a.split("=")[1]);
            if (a.startsWith("--loss=")) lossProb = Double.parseDouble(a.split("=")[1]);
            if (a.startsWith("--port=")) port = Integer.parseInt(a.split("=")[1]);
        }

        NetworkEmulator emulator = new NetworkEmulator(meanLatencyMs, jitterMs, lossProb);
        Thread serverThread = null;
        if (transport.equalsIgnoreCase("tcp")) {
            TcpServerNio server = new TcpServerNio(port);
            serverThread = new Thread(server, "tcp-server");
            serverThread.start();
        } else {
            UdpServerNio server = new UdpServerNio(port);
            serverThread = new Thread(server, "udp-server");
            serverThread.start();
        }

        List<Thread> clientThreads = new ArrayList<>();
        List<Metrics> metricsList = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            Metrics m = new Metrics();
            metricsList.add(m);
            if (transport.equalsIgnoreCase("tcp")) {
                TcpClientNio c = new TcpClientNio(i, "localhost", port, emulator, m);
                Thread t = new Thread(c, "tcp-client-" + i);
                t.start();
                clientThreads.add(t);
            } else {
                UdpClientNio c = new UdpClientNio(i, "localhost", port, emulator, m);
                Thread t = new Thread(c, "udp-client-" + i);
                t.start();
                clientThreads.add(t);
            }
            // stagger client starts slightly
            Thread.sleep(30);
        }

        System.out.printf("Running experiment: transport=%s clients=%d latency=%dms loss=%.3f duration=%ds%n",
                transport, clientCount, meanLatencyMs, lossProb, durationSec);

        Thread.sleep(durationSec * 1000L);

        // shutdown clients
        for (Thread t : clientThreads) {
            t.interrupt();
        }

        // give clients a moment to stop
        Thread.sleep(1000);

        // Write results
        File outDir = new File("results");
        outDir.mkdirs();
        for (int i = 0; i < metricsList.size(); i++) {
            Metrics m = metricsList.get(i);
            File f = new File(outDir, String.format("%s_client_%02d.csv", transport, i));
            String header = String.format("transport=%s,clients=%d,latency=%d,loss=%.3f", transport, clientCount, meanLatencyMs, lossProb);
            try {
                m.writeCsv(f, header);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        emulator.shutdown();
        System.out.println("Experiment complete. Results written to " + outDir.getAbsolutePath());
        System.exit(0);
    }
}
