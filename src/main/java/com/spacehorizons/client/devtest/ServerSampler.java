package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.spacehorizons.SpaceHorizons;

/**
 * Só em dev (autoteste): amostra a pilha da "Server thread" a cada 20 ms e, a cada 20 s, loga as pilhas mais frequentes
 * da thread quando ela está trabalhando (não esperando o próximo tick). Mostra o que enche o tick durante o voo.
 */
final class ServerSampler extends Thread {
    private static final int DEPTH = 9;
    private final Map<String, Integer> counts = new HashMap<>();
    private int busy;
    private int total;

    private ServerSampler() {
        super("SpaceHorizons-ServerSampler");
        setDaemon(true);
    }

    static void launch() {
        new ServerSampler().start();
    }

    @Override
    public void run() {
        long lastDump = System.nanoTime();
        while (true) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                return;
            }
            Thread server = find();
            if (server != null) {
                StackTraceElement[] stack = server.getStackTrace();
                total++;
                if (!idle(stack)) {
                    busy++;
                    StringBuilder key = new StringBuilder();
                    for (StackTraceElement e : stack) {
                        if (e.getMethodName().equals("managedBlock") || e.getMethodName().equals("park")
                                || e.getMethodName().equals("parkNanos")) {
                            key.append("[ESPERANDO] ");
                            break;
                        }
                    }
                    int shown = 0;
                    for (StackTraceElement e : stack) {
                        String c = e.getClassName();
                        if (c.startsWith("java.") || c.startsWith("jdk.")) {
                            continue;
                        }
                        key.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(" < ");
                        if (++shown >= DEPTH) {
                            break;
                        }
                    }
                    counts.merge(key.toString(), 1, Integer::sum);
                }
            }
            if (System.nanoTime() - lastDump > 20_000_000_000L) {
                lastDump = System.nanoTime();
                dump();
            }
        }
    }

    private static boolean idle(StackTraceElement[] stack) {
        for (int i = 0; i < Math.min(stack.length, 6); i++) {
            String m = stack[i].getMethodName();
            if (m.equals("waitUntilNextTick")) {
                return true;
            }
        }
        return stack.length == 0;
    }

    private static Thread find() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals("Server thread")) {
                return t;
            }
        }
        return null;
    }

    private void dump() {
        if (busy == 0) {
            counts.clear();
            total = 0;
            return;
        }
        SpaceHorizons.LOGGER.info("[SAMPLER] servidor ocupado em {}% das amostras ({}/{}); pilhas mais frequentes:", 100 * busy / Math.max(1, total),
                busy, total);
        new ArrayList<>(counts.entrySet()).stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
                .forEach(e -> SpaceHorizons.LOGGER.info("[SAMPLER] {}% {}", 100 * e.getValue() / Math.max(1, busy), e.getKey()));
        counts.clear();
        busy = 0;
        total = 0;
    }
}
