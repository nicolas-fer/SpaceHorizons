package com.spacehorizons.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

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
    /** O job que acabou no tick anterior: o tempo do tick dele só fica disponível no tick seguinte (ver {@link ConversionJob#sampleServerTicks}). */
    private static ConversionJob tail;
    /** Teto do retrato codificado do foguete (acima do pacote de spawn de 1 MiB o retrato vai em partes); configurável só para teste. */
    private static int maxPacketBytes = com.spacehorizons.rocket.RocketAssembler.MAX_PORTRAIT_BYTES;

    private ShipConversions() {
    }

    // ---------------------------------------------------------------- iniciar

    /**
     * Começa a converter o foguete em nave no estaleiro: valida e prepara (síncrono), e o resto roda em lotes nos ticks seguintes.
     * O foguete fica congelado até o fim. Devolve a nave (id) criada.
     */
    public static int startRocketToYard(ServerLevel level, RocketEntity rocket) throws ShipConversionException {
        return startRocketToYard(level, rocket, null);
    }

    /**
     * Igual ao de cima, mas a nave que sai do foguete voa de {@code flight} (a subida até a altitude de saída, fase 2a-ii): ao
     * terminar, o voo virtual dela começa daquele estado. Sem estado ({@code null}), a nave só fica no estaleiro.
     */
    public static int startRocketToYard(ServerLevel level, RocketEntity rocket, ShipFlight.@Nullable State flight) throws ShipConversionException {
        if (FROZEN.contains(rocket.getUUID())) {
            throw new ShipConversionException("Este foguete já está sendo convertido.");
        }
        RocketToYardJob job = new RocketToYardJob(level, rocket, flight);
        FROZEN.add(rocket.getUUID());
        JOBS.add(job);
        return job.shipId();
    }

    /** Começa a converter a nave {@code shipId} de volta em foguete (entidade) no lugar de onde veio. */
    public static void startYardToRocket(MinecraftServer server, int shipId) throws ShipConversionException {
        enqueueYardToRocket(server, shipId, false);
    }

    /**
     * Começa a descida da nave {@code shipId} (fase 2b): a entidade nasce na dimensão do corpo, onde a nave está no instante da leitura
     * da caixa, com o voo dela (ver {@link ShipDescent#plan}). Vale depois de {@link ShipDescent#crossed}.
     */
    public static void startDescent(MinecraftServer server, int shipId) throws ShipConversionException {
        enqueueYardToRocket(server, shipId, true);
    }

    private static void enqueueYardToRocket(MinecraftServer server, int shipId, boolean descending) throws ShipConversionException {
        for (ConversionJob job : JOBS) {
            if (job.shipId() == shipId) {
                throw new ShipConversionException("A nave " + shipId + " já está numa conversão.");
            }
        }
        JOBS.add(new YardToRocketJob(server, shipId, descending));
    }

    /**
     * Começa a ancorar a nave no ar (spike S6): o retrato girado {@code quarterTurns} quartos de volta vira blocos com o canto mínimo em
     * {@code origin}, em lotes, e a entidade (já congelada pela acomodação, {@link #freeze}) sai no fim. Ver {@link ShipAnchors}.
     */
    static void startAnchor(ServerLevel level, RocketEntity rocket, int quarterTurns, net.minecraft.core.BlockPos origin) throws ShipConversionException {
        AnchorJob job = new AnchorJob(level, rocket, quarterTurns, origin);
        FROZEN.add(rocket.getUUID());
        JOBS.add(job);
    }

    /** Congela o voo do foguete (a âncora acomoda a nave na grade antes do job; ver {@link ShipAnchors}). */
    static boolean freeze(UUID rocket) {
        return FROZEN.add(rocket);
    }

    static void unfreeze(UUID rocket) {
        FROZEN.remove(rocket);
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
        if (tail != null) {
            publish(server, tail);
            tail = null;
        }
        ConversionJob job = JOBS.peekFirst();
        if (job == null) {
            return;
        }
        if (job.run(server, ConversionBudget.forTick())) {
            JOBS.pollFirst();
            if (job instanceof RocketToYardJob toYard) {
                FROZEN.remove(toYard.rocketUuid());
            }
            if (job instanceof AnchorJob anchor) {
                FROZEN.remove(anchor.rocketUuid());
            }
            tail = job;
        }
    }

    private static void publish(MinecraftServer server, ConversionJob job) {
        job.sampleServerTicks(server);
        ConversionReport report = job.report();
        REPORTS.add(report);
        SpaceHorizons.LOGGER.info("[ship-conv] {}", report.line());
    }

    /** Teto, em bytes, do retrato codificado: acima disso a nave NÃO volta a foguete (o retrato grande vai em partes, mas há um teto). */
    public static int maxPacketBytes() {
        return maxPacketBytes;
    }

    /** Só para teste: baixa o teto para provar a recusa. */
    public static void setMaxPacketBytes(int bytes) {
        maxPacketBytes = Math.max(1, bytes);
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
        tail = null;
        maxPacketBytes = com.spacehorizons.rocket.RocketAssembler.MAX_PORTRAIT_BYTES;
    }
}
