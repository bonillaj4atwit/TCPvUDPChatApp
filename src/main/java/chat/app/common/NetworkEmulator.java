package chat.app.common;

import java.util.Random;
import java.util.concurrent.*;

/**
 * Application-level network impairment injector: latency + jitter + loss.
 * emulateSend schedules a Runnable to run after simulated delay, or returns false if dropped.
 */
public class NetworkEmulator {
    private final ScheduledExecutorService scheduler;
    private final Random rng = new Random();
    private final int meanDelayMs;
    private final int jitterMs;
    private final double lossProb;

    public NetworkEmulator(int meanDelayMs, int jitterMs, double lossProb) {
        this.meanDelayMs = Math.max(0, meanDelayMs);
        this.jitterMs = Math.max(0, jitterMs);
        this.lossProb = Math.max(0.0, Math.min(1.0, lossProb));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "net-emulator");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Simulate sending a message. If dropped, returns false; otherwise schedules sendTask
     * on scheduler and returns true.
     */
    public boolean emulateSend(Runnable sendTask) {
        if (rng.nextDouble() < lossProb) {
            return false; // dropped
        }
        int jitter = jitterMs == 0 ? 0 : rng.nextInt(jitterMs * 2 + 1) - jitterMs;
        long delay = Math.max(0, meanDelayMs + jitter);
        scheduler.schedule(sendTask, delay, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
