package chat.app;

import chat.app.experiments.BatchRunner;
import chat.app.experiments.TestHarness;

import java.util.Arrays;

public class AppLauncher {

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  java -jar ... batch <scenario.json | scenarios_dir>");
        System.out.println("  java -jar ... harness [--transport=tcp|udp --clients=N --duration=SEC --latency=MS --loss=P ...]");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  mvn exec:java -Dexec.args=\"batch experiments/configs\"");
        System.out.println("  mvn exec:java -Dexec.args=\"harness --transport=udp --clients=20 --duration=30\"");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }

        String mode = args[0];
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (mode.toLowerCase()) {
            case "batch" -> BatchRunner.main(subArgs);
            case "harness" -> TestHarness.main(subArgs);
            default -> {
                System.out.println("Unknown mode: " + mode);
                usage();
                System.exit(1);
            }
        }
    }
}
