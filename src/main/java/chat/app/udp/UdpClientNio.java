package chat.app.udp;

import chat.app.common.Message;
import chat.app.common.NetworkEmulator;
import chat.app.common.Metrics;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.*;

/**
 * UDP non-blocking client:
 *  - sends chat messages and PINGs periodically (scheduled)
 *  - uses NetworkEmulator to schedule sends (simulate latency/loss)
 *  - listens for responses via DatagramChannel.receive
 */
public class UdpClientNio implements Runnable {
    private final int clientId;
    private final InetSocketAddress serverAddr;
    private final DatagramChannel channel;
    private final Selector selector;
    private final NetworkEmulator emulator;
    private final Metrics metrics;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean running = true;
    private long pingSeq = 0;

    public UdpClientNio(int clientId, String host, int port, NetworkEmulator emulator, Metrics metrics) throws IOException {
        this.clientId = clientId;
        this.serverAddr = new InetSocketAddress(host, port);
        this.channel = DatagramChannel.open();
        this.channel.configureBlocking(false);
        this.selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);
        this.emulator = emulator;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        scheduler.scheduleAtFixedRate(this::sendChat, 200, 200, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::sendPing, 1000, 1000, TimeUnit.MILLISECONDS);

        ByteBuffer buf = ByteBuffer.allocate(8192);
        try {
            while (running) {
                selector.select(200);
                for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
                    SelectionKey key = it.next(); it.remove();
                    if (!key.isValid()) continue;
                    if (key.isReadable()) {
                        buf.clear();
                        SocketAddress sa = channel.receive(buf);
                        if (sa == null) continue;
                        buf.flip();
                        byte[] b = new byte[buf.limit()];
                        buf.get(b);
                        metrics.addBytesReceived(b.length);
                        String s = new String(b, StandardCharsets.UTF_8);
                        for (String line : s.split("\n")) {
                            if (line.trim().isEmpty()) continue;
                            metrics.incMessagesReceived();
                            if (line.startsWith("PONG:")) {
                                String[] p = line.split(":");
                                if (p.length >= 4) {
                                    long sendNs = Long.parseLong(p[3]);
                                    long rttNs = System.nanoTime() - sendNs;
                                    metrics.recordRTT(rttNs);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // e.printStackTrace();
        } finally {
            scheduler.shutdownNow();
            try { selector.close(); channel.close(); } catch (IOException ignored) {}
        }
    }

    private void sendChat() {
        String payload = "MSG:" + clientId + ":" + System.nanoTime();
        byte[] bytes = Message.toBytes(payload);
        boolean scheduled = emulator.emulateSend(() -> {
            try {
                channel.send(ByteBuffer.wrap(bytes), serverAddr);
                metrics.addBytesSent(bytes.length);
                metrics.incMessagesSent();
            } catch (IOException e) {
            }
        });
        if (!scheduled) metrics.incEmulatorDrop();
    }

    private void sendPing() {
        long ts = System.nanoTime();
        String payload = "PING:" + clientId + ":" + (pingSeq++) + ":" + ts;
        byte[] bytes = Message.toBytes(payload);
        boolean scheduled = emulator.emulateSend(() -> {
            try {
                channel.send(ByteBuffer.wrap(bytes), serverAddr);
                metrics.addBytesSent(bytes.length);
                metrics.incMessagesSent();
            } catch (IOException e) {
            }
        });
        if (!scheduled) metrics.incEmulatorDrop();
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        try { selector.wakeup(); } catch (Exception ignored) {}
    }
}
