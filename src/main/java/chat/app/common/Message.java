package chat.app.common;

import java.nio.charset.StandardCharsets;
/**
 * Simple text message format used by clients/servers.
 * Format examples:
 *  - "MSG:<clientId>:<sendTsNs>"
 *  - "PING:<clientId>:<seq>:<sendTsNs>"
 *  - "PONG:<clientId>:<seq>:<sendTsNs>"
 */
public class Message {
     public static byte[] toBytes(String s) {
        return (s + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static String fromBytes(byte[] b, int len) {
        return new String(b, 0, len, StandardCharsets.UTF_8).trim();
    }
}
