package chat.app.experiments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POJO representing a scenario config that BatchRunner will read from JSON.
 *
 * Example JSON fields:
 * {
 *   "name": "tcp_10_clients_50ms",
 *   "transport": "tcp",
 *   "port": 9000,
 *   "clients": 10,
 *   "durationSec": 30,
 *   "latencyMs": 50,
 *   "jitterMs": 10,
 *   "lossProb": 0.02
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioConfig {
    public String name = "scenario";
    public String transport = "tcp"; // "tcp" or "udp"
    public int port = 9000;
    public int clients = 10;
    public int durationSec = 20;
    public int latencyMs = 50;
    public int jitterMs = 10;
    public double lossProb = 0.0;

    // getters/setters optional (Jackson can use public fields)
}
