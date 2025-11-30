package main.java.chat.app.tcp;

import main.java.chat.app.common.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TCP server using NIO Selector. Accepts clients, reads newline-terminated text messages,
 * responds to PING with PONG, and broadcasts other messages to all connected clients.
 */
public class TcpServerNio implements Runnable {
    private final int port;
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final List<SocketChannel> clients = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = true;

    public TcpServerNio(int port) throws IOException {
        this.port = port;
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
                        // on exception, cancel key and close channel
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
        buf.flip();
        byte[] bytes = new byte[buf.limit()];
        buf.get(bytes);
        buf.clear();
        String s = new String(bytes, StandardCharsets.UTF_8);
        // messages can be multiple lines; process line by line
        String[] lines = s.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (line.startsWith("PING:")) {
                // reply only to this socket with PONG (replace PING with PONG)
                String pong = line.replaceFirst("PING", "PONG") + "\n";
                sc.write(ByteBuffer.wrap(pong.getBytes(StandardCharsets.UTF_8)));
            } else {
                // broadcast to all clients
                broadcast(line + "\n");
            }
        }
    }

    private void broadcast(String msg) {
        byte[] b = Message.toBytes(msg);
        synchronized (clients) {
            Iterator<SocketChannel> it = clients.iterator();
            while (it.hasNext()) {
                SocketChannel c = it.next();
                try {
                    c.write(ByteBuffer.wrap(b));
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
