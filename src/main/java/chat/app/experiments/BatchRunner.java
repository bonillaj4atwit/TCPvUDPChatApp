package chat.app.experiments;

import chat.app.common.NetworkEmulator;
import chat.app.common.Metrics;
import chat.app.common.ServerMetrics;
import chat.app.tcp.TcpServerNio;
import chat.app.tcp.TcpClientNio;
import chat.app.udp.UdpServerNio;
import chat.app.udp.UdpClientNio;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
// import java.nio.file.*;
import java.util.*;
// import java.util.concurrent.*;

/**
 * BatchRunner reads one or more JSON scenario files (or a directory) and runs them sequentially.
 * For each scenario:
 *  - starts server (with ServerMetrics)
 *  - spawns clients (each with its own Metrics)
 *  - waits duration
 *  - stops clients and server
 *  - writes per-client CSVs and combined CSV (via CombinedResultsWriter)
 */
public class BatchRunner {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: BatchRunner <scenario.json | scenarios_dir>");
            System.exit(1);
        }

        File f = new File(args[0]);
        List<File> scenarioFiles = new ArrayList<>();
        if (f.isDirectory()) {
            File[] files = f.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) Collections.addAll(scenarioFiles, files);
        } else scenarioFiles.add(f);

        ObjectMapper mapper = new ObjectMapper();
        for (File scFile : scenarioFiles) {
            System.out.println("Running scenario file: " + scFile.getName());
            ScenarioConfig cfg = mapper.readValue(scFile, ScenarioConfig.class);
            runScenario(cfg);
            System.out.println("Finished: " + cfg.name);
        }
    }

    private static void runScenario(ScenarioConfig cfg) throws Exception {
        NetworkEmulator emulator = new NetworkEmulator(cfg.latencyMs, cfg.jitterMs, cfg.lossProb);
        ServerMetrics serverMetrics = new ServerMetrics();

        Thread serverThread;
        if ("tcp".equalsIgnoreCase(cfg.transport)) {
            TcpServerNio server = new TcpServerNio(cfg.port, serverMetrics);
            serverThread = new Thread(server, "tcp-server");
        } else {
            UdpServerNio server = new UdpServerNio(cfg.port, serverMetrics);
            serverThread = new Thread(server, "udp-server");
        }
        serverThread.start();

        List<Thread> clientThreads = new ArrayList<>();
        List<Metrics> metricsList = new ArrayList<>();

        for (int i = 0; i < cfg.clients; i++) {
            Metrics m = new Metrics();
            metricsList.add(m);
            Thread t;
            if ("tcp".equalsIgnoreCase(cfg.transport)) {
                TcpClientNio client = new TcpClientNio(i, "localhost", cfg.port, emulator, m);
                t = new Thread(client, "tcp-client-" + i);
            } else {
                UdpClientNio client = new UdpClientNio(i, "localhost", cfg.port, emulator, m);
                t = new Thread(client, "udp-client-" + i);
            }
            t.start();
            clientThreads.add(t);
            Thread.sleep(30);
        }

        System.out.printf("Scenario '%s' running: transport=%s clients=%d latency=%d loss=%.3f duration=%ds%n",
                cfg.name, cfg.transport, cfg.clients, cfg.latencyMs, cfg.lossProb, cfg.durationSec);

        Thread.sleep(cfg.durationSec * 1000L);

        // stop clients
        for (Thread t : clientThreads) {
            t.interrupt();
        }
        Thread.sleep(800);

        // stop server
        // send interrupt to server thread and rely on its shutdown mechanism
        serverThread.interrupt();

        // write per-client CSVs
        File outDir = new File("results", cfg.name);
        outDir.mkdirs();
        for (int i = 0; i < metricsList.size(); i++) {
            Metrics m = metricsList.get(i);
            File f = new File(outDir, String.format("%s_client_%02d.csv", cfg.transport, i));
            String header = String.format("scenario=%s,transport=%s,clients=%d,latency=%d,loss=%.3f",
                    cfg.name, cfg.transport, cfg.clients, cfg.latencyMs, cfg.lossProb);
            try {
                m.writeCsv(f, header);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // write server metrics
        try {
            File sf = new File(outDir, "server_metrics.csv");
            String header = String.format("scenario=%s,transport=%s", cfg.name, cfg.transport);
            serverMetrics.writeCsv(sf, header);
        } catch (Exception e) { e.printStackTrace(); }

        // write combined CSV
        CombinedResultsWriter.writeCombinedCsv(outDir, cfg, metricsList);

        emulator.shutdown();
    }
}
