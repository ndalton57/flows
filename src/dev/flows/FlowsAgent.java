package dev.flows;

import java.lang.instrument.Instrumentation;

/**
 * Premain entry point for the "Flows" server-side fluid flow-speed patch.
 *
 * <p>Loaded into the Paper server JVM via a startup flag, e.g.:
 * <pre>java -javaagent:flows.jar=water=2,lava=5 -jar paper-26.1.2.jar nogui</pre>
 *
 * <p>It rewrites {@code WaterFluid#getTickDelay} and {@code LavaFluid#getTickDelay}
 * to return the configured tick delays as the server's fluid classes load.
 * A lower delay = faster flow. This is purely server-side; vanilla clients are
 * unaffected and just render whatever block states the server sends.
 *
 * <p>Defaults: water = 2 ticks (vanilla 5, ~2.5x faster), lava = 4 ticks
 * (vanilla 30 overworld / 10 nether). Override via the agent args
 * {@code water=<n>,lava=<n>}, or via a Flows.conf when loaded through Primer - no rebuild needed.
 */
public final class FlowsAgent {

    private static final int DEFAULT_WATER = 2;
    private static final int DEFAULT_LAVA = 4;

    private FlowsAgent() {
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        int water = DEFAULT_WATER;
        int lava = DEFAULT_LAVA;

        if (agentArgs != null && !agentArgs.isBlank()) {
            for (String part : agentArgs.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    System.err.println("[Flows] Ignoring malformed agent arg: '" + part + "' (expected key=value)");
                    continue;
                }
                String key = kv[0].trim().toLowerCase();
                int n;
                try {
                    n = Integer.parseInt(kv[1].trim());
                } catch (NumberFormatException e) {
                    System.err.println("[Flows] Ignoring non-numeric agent arg: '" + part + "'");
                    continue;
                }
                if (n < 1) {
                    System.err.println("[Flows] Tick delay must be >= 1; clamping '" + part + "' to 1");
                    n = 1;
                }
                switch (key) {
                    case "water" -> water = n;
                    case "lava" -> lava = n;
                    default -> System.err.println("[Flows] Unknown agent arg key: '" + key + "'");
                }
            }
        }

        System.out.println("[Flows] Loading server-side fluid flow patch "
                + "(water getTickDelay -> " + water + ", lava getTickDelay -> " + lava + ").");
        inst.addTransformer(new GetTickDelayTransformer(water, lava), true);
        System.out.println("[Flows] Transformer registered; fluid classes will be patched as they load.");
    }
}
