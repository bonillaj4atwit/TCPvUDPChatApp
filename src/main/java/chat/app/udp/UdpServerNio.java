package main.java.chat.app.udp;

import main.java.chat.app.common.Message;
import main.java.chat.app.common.ServerMetrics;


import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * UDP server using DatagramChannel. Receives datagrams, responds to PINGs with PONG,
 * and broadcasts other messages to all known client addresses. Records server metrics.
 */
public class UdpServerNio implements Runnable {
    private final int port;
    private final DatagramChannel channel;
    private final Selector selector;
    private final Set<SocketAddress> clients = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean running = true;
    private final ServerMetrics serverMetrics;

    public UdpServerNio(int port) throws IOException {
        this(port, null);
    }

    public UdpServerNio(int port, ServerMetrics serverMetrics) throws IOException {
        this.port = port;
        this.serverMetrics = serverMetrics;
        this.channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(port));
        this.selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        System.out.println("UDP server listening on port " + port);
        ByteBuffer buf = ByteBuffer.allocate(8192);
        try {
            while (running) {
                selector.select(200);
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) continue;
                    if (key.isReadable()) {
                        buf.clear();
                        SocketAddress sa = channel.receive(buf);
                        if (sa == null) continue;
                        buf.flip();
                        int len = buf.limit();
                        byte[] b = new byte[len];
                        buf.get(b);
                        String msg = new String(b, StandardCharsets.UTF_8).trim();
                        clients.add(sa);
                        if (serverMetrics != null) {
                            serverMetrics.addBytesReceived(len);
                            serverMetrics.incMessagesReceived();
                        }
                        long start = System.nanoTime();
                        if (msg.startsWith("PING:")) {
                            String pong = msg.replaceFirst("PING", "PONG") + "\n";
                            byte[] out = pong.getBytes(StandardCharsets.UTF_8);
                            channel.send(ByteBuffer.wrap(out), sa);
                            if (serverMetrics != null) {
                                serverMetrics.addBytesSent(out.length);
                                serverMetrics.incMessagesSent();
                            }
                        } else {
                            byte[] out = Message.toBytes(msg);
                            synchronized (clients) {
                                for (SocketAddress client : clients) {
                                    channel.send(ByteBuffer.wrap(out), client);
                                    if (serverMetrics != null) {
                                        serverMetrics.addBytesSent(out.length);
                                        serverMetrics.incMessagesSent();
                                    }
                                }
                            }
                        }
                        long procNs = System.nanoTime() - start;
                        if (serverMetrics != null) serverMetrics.recordProcessingNs(procNs);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { selector.close(); channel.close(); } catch (IOException ignored) {}
        }
    }

    public void shutdown() {
        running = false;
        selector.wakeup();
    }
}
