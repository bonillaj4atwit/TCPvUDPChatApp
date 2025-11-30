package main.java.chat.app.tcp;

import main.java.chat.app.common.Message;
import main.java.chat.app.common.ServerMetrics;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TCP server using NIO Selector. Accepts clients, reads newline-terminated text messages,
 * responds to PING with PONG, and broadcasts other messages to all connected clients.
 *
 * Now records server-side metrics via ServerMetrics (if provided).
 */
public class TcpServerNio implements Runnable {
    private final int port;
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final List<SocketChannel> clients = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = true;
    private final ServerMetrics serverMetrics;

    public TcpServerNio(int port) throws IOException {
        this(port, null);
    }

    public TcpServerNio(int port, ServerMetrics serverMetrics) throws IOException {
        this.port = port;
        this.serverMetrics = serverMetrics;
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    public void run() {
        System.out.println("TCP server listening on port " + port);
        try {
            while (running) {
                selector.select(200);
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    try {
                        if (!key.isValid()) continue;
                        if (key.isAcceptable()) handleAccept();
                        else if (key.isReadable()) handleRead(key);
                    } catch (IOException e) {
                        Channel ch = key.channel();
                        key.cancel();
                        if (ch instanceof SocketChannel) {
                            clients.remove(ch);
                            try { ch.close(); } catch (IOException ignored) {}
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { selector.close(); serverChannel.close(); } catch (IOException ignored) {}
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel sc = serverChannel.accept();
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(8192));
        clients.add(sc);
        System.out.println("Accepted TCP client: " + sc.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        int read = sc.read(buf);
        if (read == -1) {
            clients.remove(sc);
            sc.close();
            return;
        }
        if (read == 0) return;
        if (serverMetrics != null) serverMetrics.addBytesReceived(read);

        buf.flip();
        byte[] bytes = new byte[buf.limit()];
        buf.get(bytes);
        buf.clear();
        long start = System.nanoTime();
        String s = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = s.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (line.startsWith("PING:")) {
                // reply only to this socket with PONG (replace PING with PONG)
                String pong = line.replaceFirst("PING", "PONG") + "\n";
                byte[] outb = pong.getBytes(StandardCharsets.UTF_8);
                sc.write(ByteBuffer.wrap(outb));
                if (serverMetrics != null) {
                    serverMetrics.addBytesSent(outb.length);
                    serverMetrics.incMessagesSent();
                }
            } else {
                // broadcast to all clients
                byte[] out = Message.toBytes(line);
                broadcast(out);
            }
            if (serverMetrics != null) serverMetrics.incMessagesReceived();
        }
        long procNs = System.nanoTime() - start;
        if (serverMetrics != null) serverMetrics.recordProcessingNs(procNs);
    }

    private void broadcast(byte[] outBytes) {
        synchronized (clients) {
            Iterator<SocketChannel> it = clients.iterator();
            while (it.hasNext()) {
                SocketChannel c = it.next();
                try {
                    c.write(ByteBuffer.wrap(outBytes));
                    if (serverMetrics != null) serverMetrics.addBytesSent(outBytes.length);
                    if (serverMetrics != null) serverMetrics.incMessagesSent();
                } catch (IOException e) {
                    it.remove();
                    try { c.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    public void shutdown() {
        running = false;
        selector.wakeup();
    }
}