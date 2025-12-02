package chat.app.tcp;

import chat.app.common.Message;
import chat.app.common.NetworkEmulator;
import chat.app.common.Metrics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.*;

/**
 * Non-blocking TCP client that:
 *  - connects to server
 *  - periodically sends chat messages and PINGs (for RTT)
 *  - uses NetworkEmulator to schedule sends (simulate latency/loss)
 *  - records metrics
 */
public class TcpClientNio implements Runnable {
    private final int clientId;
    private final String host;
    private final int port;
    private final NetworkEmulator emulator;
    private final Metrics metrics;

    private SocketChannel channel;
    private Selector selector;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean running = true;
    private long pingSeq = 0;

    public TcpClientNio(int clientId, String host, int port, NetworkEmulator emulator, Metrics metrics) {
        this.clientId = clientId;
        this.host = host;
        this.port = port;
        this.emulator = emulator;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            selector = Selector.open();
            channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
            channel.connect(new InetSocketAddress(host, port));

            // schedule periodic sends
            scheduler.scheduleAtFixedRate(this::sendChat, 200, 200, TimeUnit.MILLISECONDS); // 5/sec
            scheduler.scheduleAtFixedRate(this::sendPing, 1000, 1000, TimeUnit.MILLISECONDS); // 1/sec

            while (running && selector.isOpen()) {
                selector.select(200);
                for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
                    SelectionKey key = it.next(); it.remove();
                    if (!key.isValid()) continue;
                    if (key.isConnectable()) finishConnect();
                    if (key.isReadable()) readFromServer(key);
                }
            }
        } catch (IOException e) {
            // e.printStackTrace();
        } finally {
            try { if (channel != null) channel.close(); } catch (IOException ignored) {}
            try { if (selector != null) selector.close(); } catch (IOException ignored) {}
            scheduler.shutdownNow();
        }
    }

    private void finishConnect() throws IOException {
        if (channel.finishConnect()) {
            channel.register(selector, SelectionKey.OP_READ);
            // connected
        }
    }

    private void readFromServer(SelectionKey key) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(8192);
            int r = channel.read(buf);
            if (r <= 0) return;
            metrics.addBytesReceived(r);
            buf.flip();
            String s = StandardCharsets.UTF_8.decode(buf).toString();
            for (String line : s.split("\n")) {
                if (line.trim().isEmpty()) continue;
                metrics.incMessagesReceived();
                if (line.startsWith("PONG:")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 4) {
                        // PONG:clientId:seq:sendTsNs
                        long sendNs = Long.parseLong(parts[3]);
                        long rttNs = System.nanoTime() - sendNs;
                        metrics.recordRTT(rttNs);
                    }
                }
            }
        } catch (IOException e) {
            // e.printStackTrace();
        }
    }

    private void sendChat() {
        String payload = "MSG:" + clientId + ":" + System.nanoTime();
        byte[] bytes = Message.toBytes(payload);
        boolean scheduled = emulator.emulateSend(() -> {
            try {
                channel.write(ByteBuffer.wrap(bytes));
                metrics.addBytesSent(bytes.length);
                metrics.incMessagesSent();
            } catch (IOException e) {
                // ignore
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
                channel.write(ByteBuffer.wrap(bytes));
                metrics.addBytesSent(bytes.length);
                metrics.incMessagesSent();
            } catch (IOException e) {
            }
        });
        if (!scheduled) metrics.incEmulatorDrop();
    }

    public void shutdown() {
        running = false;
        try { if (selector != null) selector.wakeup(); } catch (Exception ignored) {}
        scheduler.shutdownNow();
    }
}
