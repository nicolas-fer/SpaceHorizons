package com.spacehorizons.ship;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

/**
 * O censo das naves (plano 1b): guarda o último resultado de cada nave ({@link ShipCensusResult}) e roda os censos pedidos em lotes, no fim
 * de cada tick do servidor, com o mesmo orçamento das conversões ({@link ConversionBudget}). {@link #scanNow} varre a caixa toda de uma
 * vez (para o que precisa do número agora: a ponte, o comando, a criação da nave de teste). Só roda no thread do servidor.
 */
public final class ShipCensusService {
    private static final Map<Integer, ShipCensusResult> LATEST = new HashMap<>();
    private static final Map<Integer, ShipCensusScanner> RUNNING = new HashMap<>();

    private ShipCensusService() {
    }

    /** O último censo da nave, ou null se nunca foi feito nesta sessão. */
    public static @Nullable ShipCensusResult latest(int shipId) {
        return LATEST.get(shipId);
    }

    /** Pede um censo em lotes (sem efeito se já há um rodando para a nave). */
    public static void request(MinecraftServer server, ShipRecord ship) {
        ServerLevel yard = ShipYard.level(server);
        if (yard == null || RUNNING.containsKey(ship.id())) {
            return;
        }
        RUNNING.put(ship.id(), new ShipCensusScanner(yard, ship.box()));
    }

    /** Há um censo em andamento para a nave? */
    public static boolean isRunning(int shipId) {
        return RUNNING.containsKey(shipId);
    }

    /** Conta a nave agora, de uma vez (carrega os chunks que faltarem), guarda e devolve o resultado. */
    public static ShipCensusResult scanNow(MinecraftServer server, ShipRecord ship) {
        ServerLevel yard = ShipYard.level(server);
        if (yard == null) {
            throw new IllegalStateException("este mundo não tem a dimensão do estaleiro");
        }
        RUNNING.remove(ship.id());
        ShipCensusScanner scanner = new ShipCensusScanner(yard, ship.box());
        scanner.step(new ConversionBudget(Integer.MAX_VALUE, 0, System::nanoTime));
        ShipCensusResult result = scanner.result();
        LATEST.put(ship.id(), result);
        return result;
    }

    /** A cada tick do servidor (fim do tick): avança os censos pedidos dentro do orçamento. */
    public static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty()) {
            return;
        }
        ConversionBudget budget = ConversionBudget.forTick();
        Iterator<Map.Entry<Integer, ShipCensusScanner>> it = RUNNING.entrySet().iterator();
        while (it.hasNext() && !budget.exhausted()) {
            Map.Entry<Integer, ShipCensusScanner> e = it.next();
            try {
                if (e.getValue().step(budget)) {
                    LATEST.put(e.getKey(), e.getValue().result());
                    it.remove();
                }
            } catch (RuntimeException ex) {
                SpaceHorizons.LOGGER.error("[ship] censo da nave {} falhou", e.getKey(), ex);
                it.remove();
            }
        }
    }

    /** Esquece a nave (destruída). */
    public static void forget(int shipId) {
        LATEST.remove(shipId);
        RUNNING.remove(shipId);
    }

    /** O servidor parou: esquece tudo. */
    public static void reset() {
        LATEST.clear();
        RUNNING.clear();
    }
}
