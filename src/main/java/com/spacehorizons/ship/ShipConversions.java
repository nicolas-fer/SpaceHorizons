package com.spacehorizons.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * A fila e o laço das conversões entidade ↔ blocos do estaleiro (spike S3, plano 3.2 e 3.4): um job de cada vez roda a cada tick do
 * servidor com o orçamento de {@link ConversionBudget}, em lotes (D10: sem hitch de um tick só). Mantém também o conjunto de foguetes
 * "congelados" (o voo não ticka enquanto os blocos estão sendo escritos), os relatórios medidos e a recuperação depois de um
 * fechamento no meio de uma conversão ({@link #recover}). Só roda no thread do servidor.
 */
public final class ShipConversions {
    private static final ArrayDeque<ConversionJob> JOBS = new ArrayDeque<>();
    private static final Set<UUID> FROZEN = new HashSet<>();
    private static final List<ConversionReport> REPORTS = new ArrayList<>();
    private static boolean recovered;

    private ShipConversions() {
    }

    // ---------------------------------------------------------------- iniciar

    /**
     * Começa a converter o foguete em nave no estaleiro: valida e prepara (síncrono), e o resto roda em lotes nos ticks seguintes.
     * O foguete fica congelado até o fim. Devolve a nave (id) criada.
     */
    public static int startRocketToYard(ServerLevel level, RocketEntity rocket) throws ShipConversionException {
        if (FROZEN.contains(rocket.getUUID())) {
            throw new ShipConversionException("Este foguete já está sendo convertido.");
        }
        RocketToYardJob job = new RocketToYardJob(level, rocket);
        FROZEN.add(rocket.getUUID());
        JOBS.add(job);
        return job.shipId();
    }

    /** Começa a converter a nave {@code shipId} de volta em foguete (entidade) no lugar de onde veio. */
    public static void startYardToRocket(MinecraftServer server, int shipId) throws ShipConversionException {
        for (ConversionJob job : JOBS) {
            if (job.shipId() == shipId) {
                throw new ShipConversionException("A nave " + shipId + " já está numa conversão.");
            }
        }
        JOBS.add(new YardToRocketJob(server, shipId));
    }

    /** Põe um job no fim da fila (usado pelos jobs que desfazem a si mesmos). */
    static void enqueue(ConversionJob job) {
        JOBS.add(job);
    }

    // ---------------------------------------------------------------- laço

    /** A cada tick do servidor (fim do tick): roda o job da frente da fila dentro do orçamento. */
    public static void tick(MinecraftServer server) {
        if (!recovered) {
            recovered = true;
            recover(server);
        }
        ConversionJob job = JOBS.peekFirst();
        if (job == null) {
            return;
        }
        if (job.run(server, ConversionBudget.forTick())) {
            JOBS.pollFirst();
            ConversionReport report = job.report();
            REPORTS.add(report);
            SpaceHorizons.LOGGER.info("[ship-conv] {}", report.line());
            if (job instanceof RocketToYardJob toYard) {
                FROZEN.remove(toYard.rocketUuid());
            }
        }
    }

    /** O foguete está sendo convertido (o voo dele não deve tickar)? Chamado de {@code RocketEntity.tick}. */
    public static boolean isFrozen(Entity rocket) {
        return !FROZEN.isEmpty() && FROZEN.contains(rocket.getUUID());
    }

    /** Há conversão em andamento ou na fila? */
    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    /** Todos os relatórios desta sessão do servidor, do mais antigo ao mais novo. */
    public static List<ConversionReport> reports() {
        return Collections.unmodifiableList(REPORTS);
    }

    // ---------------------------------------------------------------- recuperação e fim do servidor

    /**
     * Depois de abrir um mundo salvo no meio de uma conversão: {@code FILLING} e {@code REMOVING} deixaram blocos pela metade com a
     * entidade ainda (ou já) existindo, então limpa a caixa e esquece a nave (a entidade é a verdade nos dois casos); {@code EMPTYING}
     * só lia, nada mudou, volta a {@code READY}.
     */
    static void recover(MinecraftServer server) {
        if (ShipYard.level(server) == null) {
            return;
        }
        ShipRegistry registry = ShipRegistry.get(server);
        for (ShipRecord ship : List.copyOf(registry.all())) {
            switch (ship.phase()) {
                case READY -> {
                }
                case EMPTYING -> {
                    registry.update(ship.withPhase(ShipRecord.Phase.READY));
                    SpaceHorizons.LOGGER.warn("[ship-conv] nave {} estava lendo os blocos quando o mundo fechou: nada mudou, volta a READY", ship.id());
                }
                case FILLING, REMOVING -> {
                    SpaceHorizons.LOGGER.warn("[ship-conv] nave {} estava em {} quando o mundo fechou: limpando a caixa e esquecendo a nave", ship.id(), ship.phase());
                    JOBS.add(new ClearBoxJob(server, ship.id(), "recuperação (" + ship.phase() + ")"));
                }
            }
        }
    }

    /** O servidor parou: esquece fila, congelados e relatórios (o mundo seguinte, no mesmo processo, começa limpo). */
    public static void reset() {
        JOBS.clear();
        FROZEN.clear();
        REPORTS.clear();
        recovered = false;
    }
}
