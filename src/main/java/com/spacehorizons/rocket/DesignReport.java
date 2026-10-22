package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FuelLoad;
import com.spacehorizons.space.flight.Propellant;
import com.spacehorizons.space.flight.RocketStats;

import net.minecraft.network.FriendlyByteBuf;

/**
 * O relatório do painel de projeto da plataforma de lançamento (docs/PLANO_TIERS.md, seção 4): o que o foguete montado sobre a plataforma
 * tem (massa, empuxo, TWR, Δv), o destino que ele alcança ({@link MissionBudget}) e os problemas com a posição do bloco de cada um.
 *
 * REGRA: os números saem do MESMO cálculo da montagem. {@link #of} recebe o {@link RocketAssembly.Report} que {@code RocketAssembly.analyze}
 * produziu (a busca que {@code scan} também usa) e passa o censo por {@link RocketSpecs#statsFor}, a função que o retrato e a entidade usam;
 * os destinos vêm de {@link MissionBudget}, medido no simulador da missão. Logo o painel mostra o que o foguete montado terá.
 *
 * Os números são com os tanques CHEIOS (o projeto); o combustível de cada tanque no mundo é outra coisa e aparece na barra de ação do tanque.
 *
 * <p>Tier 2: {@code engineBlocks}, {@code soloEngines}, {@code engines2}, {@code engines3}, {@code tankBlocks}, {@code looseTanks} e {@code tankColumns}
 * são as peças QUÍMICAS (as do Tier 1); os motores e tanques dos outros tipos e os geradores RTG vêm em {@code lines} ({@link Line}), e o Δv, o TWR e as
 * massas já contam tudo (cada motor queimando o seu combustível, os motores nucleares e de fusão carregados, {@link RocketStats#deltaV(FuelLoad)}).
 */
public record DesignReport(int padX, int padY, int padZ, boolean found, int blocks, int sizeX, int sizeY, int sizeZ, double dryMassT, double wetMassT,
        double fuelCapacityMb, double thrust, double ispTicks, double twr, double deltaV, int engineBlocks, int soloEngines, int engines2,
        int engines3, int tankBlocks, int looseTanks, int tankColumns, int parachutes, boolean assemblable, byte destination,
        double nextRequiredDeltaV, List<Problem> problems, List<Line> lines) {

    /** Sem peças do Tier 2 (o relatório de antes). */
    public DesignReport(int padX, int padY, int padZ, boolean found, int blocks, int sizeX, int sizeY, int sizeZ, double dryMassT, double wetMassT,
            double fuelCapacityMb, double thrust, double ispTicks, double twr, double deltaV, int engineBlocks, int soloEngines, int engines2,
            int engines3, int tankBlocks, int looseTanks, int tankColumns, int parachutes, boolean assemblable, byte destination,
            double nextRequiredDeltaV, List<Problem> problems) {
        this(padX, padY, padZ, found, blocks, sizeX, sizeY, sizeZ, dryMassT, wetMassT, fuelCapacityMb, thrust, ispTicks, twr, deltaV, engineBlocks,
                soloEngines, engines2, engines3, tankBlocks, looseTanks, tankColumns, parachutes, assemblable, destination, nextRequiredDeltaV, problems,
                List.of());
    }

    public DesignReport {
        problems = List.copyOf(problems);
        lines = List.copyOf(lines);
    }

    /**
     * Uma linha do Tier 2 no painel: um tipo de motor, de tanque ou os geradores RTG ({@code kind}, o papel do bloco). Motor: {@code blocks} e os
     * avulsos, cubos 2x2x2 e 3x3x3, o empuxo efetivo e o Isp do tipo, e {@code power} a energia em % (só o iônico; 100 nos outros). Tanque:
     * {@code blocks}, avulsos, colunas ({@code cubes2}) e {@code capacityMb}. RTG: {@code blocks}. {@code capacityMb} do motor é a carga dele
     * (núcleo do nuclear, hélio-3 do de fusão).
     */
    public record Line(RocketBlockKind kind, int blocks, int solo, int cubes2, int cubes3, double thrust, double ispTicks, double capacityMb, int power) {
    }

    /** Tipos de problema; os de {@link #blocking} impedem a montagem de verdade, os outros são avisos de projeto. */
    public enum Type {
        // impedem a montagem (as mesmas recusas de RocketException)
        NO_COCKPIT(true), MULTIPLE_COCKPITS(true), NO_ENGINE(true), NO_TANK(true), NO_PAD(true), TOO_LARGE(true), TOUCHES_TERRAIN(true),
        FORBIDDEN_BLOCK(true), UNLOADED_CHUNK(true),
        // avisos de projeto
        NO_PARACHUTE(false), ENGINE_WITHOUT_TANK(false), INCOMPLETE_TANK(false), LOW_TWR(false),
        // Tier 2: motor sem nenhum tanque do combustível dele (arg = o papel do tanque que falta) e motores iônicos sem energia bastante (arg = %)
        NO_ENGINE_FUEL(false), LOW_POWER(false);

        private final boolean blocking;

        Type(boolean blocking) {
            this.blocking = blocking;
        }

        public boolean blocking() {
            return this.blocking;
        }

        public static Type of(RocketException.Reason reason) {
            return switch (reason) {
                case NO_COCKPIT -> NO_COCKPIT;
                case MULTIPLE_COCKPITS -> MULTIPLE_COCKPITS;
                case NO_ENGINE -> NO_ENGINE;
                case NO_TANK -> NO_TANK;
                case NO_PAD -> NO_PAD;
                case TOUCHES_TERRAIN -> TOUCHES_TERRAIN;
                case FORBIDDEN_BLOCK -> FORBIDDEN_BLOCK;
                case UNLOADED_CHUNK -> UNLOADED_CHUNK;
                default -> TOO_LARGE;
            };
        }
    }

    /** Um problema no bloco (x, y, z) do mundo; {@code arg} é um número para a mensagem (cockpits achados, TWR em centésimos...). */
    public record Problem(Type type, int x, int y, int z, int arg) {
    }

    /** Máximo de problemas enviados (os mais graves primeiro): o painel e o destaque no mundo não precisam de mais. */
    public static final int MAX_PROBLEMS = 24;
    /** Gravidade de superfície da Terra, em blocos/tick²: o TWR do painel é o da decolagem. */
    public static final double EARTH_GRAVITY = BodyPhysics.of(SolarSystem.EARTH).surfaceGravity();

    public MissionBudget.Destination destinationValue() {
        return MissionBudget.Destination.values()[this.destination];
    }

    /** A plataforma sem nada montado sobre ela. */
    public static DesignReport nothing(int padX, int padY, int padZ) {
        return new DesignReport(padX, padY, padZ, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false,
                (byte) MissionBudget.Destination.NO_LIFTOFF.ordinal(), 0.0, List.of());
    }

    /** O relatório de uma busca (a mesma que a montagem faz) a partir da plataforma em (padX, padY, padZ). */
    public static DesignReport of(RocketAssembly.Report report, int padX, int padY, int padZ) {
        RocketAssembly.Result r = report.result();
        if (r.cells().isEmpty()) {
            return nothing(padX, padY, padZ);
        }
        RocketCensus census = r.census();
        RocketStats stats = RocketSpecs.statsFor(census);
        double fuel = stats.fuelCapacityMb();
        boolean chemical = stats.chemicalOnly();
        FuelLoad full = stats.fullLoad();
        double twr = chemical ? stats.twr(fuel, EARTH_GRAVITY) : stats.twr(full, EARTH_GRAVITY);
        double dv = chemical ? stats.deltaV(fuel) : stats.deltaV(full);
        double wet = chemical ? stats.wetMass(fuel) : stats.wetMass(full);
        MissionBudget.Destination destination = MissionBudget.reachable(dv, twr, census.parachutes() > 0);
        MissionBudget.Destination next = MissionBudget.next(destination);

        List<Problem> problems = new ArrayList<>();
        for (RocketAssembly.Blocker b : report.blockers()) {
            Type type = b.reason() == RocketException.Reason.TOO_LARGE ? Type.TOO_LARGE : Type.of(b.reason());
            int arg = b.args().length > 0 && b.args()[0] instanceof Integer i && b.reason() == RocketException.Reason.MULTIPLE_COCKPITS ? i : 0;
            addOnce(problems, new Problem(type, b.x(), b.y(), b.z(), arg));
        }
        addDesignWarnings(report, census, twr, problems);
        if (problems.size() > MAX_PROBLEMS) {
            problems = new ArrayList<>(problems.subList(0, MAX_PROBLEMS));
        }
        return new DesignReport(padX, padY, padZ, true, r.cells().size(), r.sizeX(), r.sizeY(), r.sizeZ(), stats.dryMassT(), wet, fuel,
                stats.thrust(), stats.ispTicks(), twr, dv, census.engines(), census.soloEngines(), census.engineStructuresOfSide(Multiblocks.ENGINE_SMALL),
                census.engineStructuresOfSide(Multiblocks.ENGINE_LARGE), census.tanks(), census.looseTanks(), census.tankStructures().size(),
                census.parachutes(), report.assemblable(), (byte) destination.ordinal(),
                next == null ? 0.0 : MissionBudget.required(next), List.copyOf(problems), tier2Lines(census, stats));
    }

    /** As linhas do Tier 2: cada tipo de motor e de tanque presente e os geradores RTG. */
    static List<Line> tier2Lines(RocketCensus census, RocketStats stats) {
        List<Line> lines = new ArrayList<>();
        if (!census.hasTier2()) {
            return lines;
        }
        for (EngineType type : new EngineType[] {EngineType.NUCLEAR, EngineType.ION, EngineType.FUSION}) {
            RocketBlockKind kind = type.kind();
            int blocks = census.count(kind);
            if (blocks == 0) {
                continue;
            }
            double thrust = 0.0;
            double isp = type.isp(1);
            for (com.spacehorizons.space.flight.EngineGroup g : stats.groups()) {
                if (g.type().equals(type.key())) {
                    thrust = g.thrust();
                    isp = g.ispTicks();
                }
            }
            List<Multiblocks.Box> boxes = census.structuresOf(kind);
            int power = type == EngineType.ION ? (int) Math.round(RocketSpecs.ionPowerFraction(census) * 100.0) : 100;
            lines.add(new Line(kind, blocks, census.looseOf(kind), (int) boxes.stream().filter(b -> b.side() == 2).count(),
                    (int) boxes.stream().filter(b -> b.side() == 3).count(), thrust, isp, (double) blocks * type.chargePerBlockMb(), power));
        }
        for (RocketBlockKind kind : new RocketBlockKind[] {RocketBlockKind.HYDROGEN_TANK, RocketBlockKind.XENON_TANK}) {
            int blocks = census.count(kind);
            if (blocks > 0) {
                lines.add(new Line(kind, blocks, census.looseOf(kind), census.structuresOf(kind).size(), 0, 0.0, 0.0,
                        (double) blocks * RocketSpecs.TANK_CAPACITY_MB, 100));
            }
        }
        if (census.rtgs() > 0) {
            lines.add(new Line(RocketBlockKind.RTG, census.rtgs(), census.rtgs(), 0, 0, 0.0, 0.0, 0.0, 100));
        }
        return lines;
    }

    private static void addOnce(List<Problem> problems, Problem problem) {
        for (Problem p : problems) {
            if (p.type() == problem.type() && p.x() == problem.x() && p.y() == problem.y() && p.z() == problem.z()) {
                return;
            }
        }
        problems.add(problem);
    }

    /** Os avisos de projeto: o que não impede montar mas o foguete não faz direito. */
    private static void addDesignWarnings(RocketAssembly.Report report, RocketCensus census, double twr, List<Problem> problems) {
        RocketAssembly.Result r = report.result();
        List<RocketAssembly.Cell> cells = r.cells();
        // sem paraquedas: a volta à Terra não é prometida; marca o bloco mais alto (onde ele iria)
        if (census.parachutes() == 0) {
            RocketAssembly.Cell top = cells.get(0);
            for (RocketAssembly.Cell c : cells) {
                if (c.y() > top.y()) {
                    top = c;
                }
            }
            addOnce(problems, new Problem(Type.NO_PARACHUTE, top.x(), top.y(), top.z(), 0));
        }
        // TWR baixo: não decola (<= 1) ou só chega ao salto (< 1,2); marca o cockpit
        if (census.allEngines() > 0 && twr < MissionBudget.MIN_TWR_ORBIT) {
            addOnce(problems, new Problem(Type.LOW_TWR, r.cockpitX(), r.cockpitY(), r.cockpitZ(), (int) Math.round(twr * 100.0)));
        }
        // motor sem tanque acima, na coluna dele (só se há tanque: sem nenhum, o problema é NO_TANK). Cada tipo de motor olha o tanque do SEU
        // combustível; o de fusão leva o hélio-3 dentro dele e não precisa de tanque. Tipo sem nenhum tanque do seu combustível: NO_ENGINE_FUEL.
        for (EngineType type : EngineType.values()) {
            RocketBlockKind tankKind = RocketSpecs.tankKindOf(type.propellant());
            if (tankKind == null || census.count(type.kind()) == 0) {
                continue;
            }
            Multiblocks.Tiling engines = report.structures().of(type.kind());
            boolean anyTank = census.count(tankKind) > 0;
            if (!anyTank && type == EngineType.CHEMICAL && census.allTanks() == 0 && !RocketSpecs.carriesOwnFuel(census)) {
                continue; // foguete sem tanque nenhum: o problema é NO_TANK
            }
            for (long cell : engineHeads(engines)) {
                int x = net.minecraft.core.BlockPos.getX(cell);
                int y = net.minecraft.core.BlockPos.getY(cell);
                int z = net.minecraft.core.BlockPos.getZ(cell);
                if (!anyTank) {
                    addOnce(problems, new Problem(Type.NO_ENGINE_FUEL, x, y, z, tankKind.ordinal()));
                } else if (!hasTankAbove(cells, engines, tankKind, x, y, z)) {
                    addOnce(problems, new Problem(Type.ENGINE_WITHOUT_TANK, x, y, z, 0));
                }
            }
        }
        // motores iônicos com menos energia que a pedida (geradores RTG de menos): marca o primeiro motor iônico
        if (census.count(RocketBlockKind.ION_ENGINE) > 0 && RocketSpecs.ionPowerFraction(census) < 1.0) {
            List<Long> heads = engineHeads(report.structures().of(RocketBlockKind.ION_ENGINE));
            if (!heads.isEmpty()) {
                long cell = heads.get(0);
                addOnce(problems, new Problem(Type.LOW_POWER, net.minecraft.core.BlockPos.getX(cell), net.minecraft.core.BlockPos.getY(cell),
                        net.minecraft.core.BlockPos.getZ(cell), (int) Math.round(RocketSpecs.ionPowerFraction(census) * 100.0)));
            }
        }
        // estrutura de tanque incompleta: tanques que valem um a um mas encostam em outro tanque igual (uma coluna que não fechou)
        int flagged = 0;
        for (RocketBlockKind tankKind : new RocketBlockKind[] {RocketBlockKind.FUEL_TANK, RocketBlockKind.HYDROGEN_TANK, RocketBlockKind.XENON_TANK}) {
            flagged = incompleteTanks(report, tankKind, problems, flagged);
        }
    }

    /** Marca (até 8 no total) os tanques do papel dado que ficaram avulsos encostados num tanque igual. */
    private static int incompleteTanks(RocketAssembly.Report report, RocketBlockKind tankKind, List<Problem> problems, int flagged) {
        java.util.Set<Long> tankCells = new java.util.HashSet<>();
        for (RocketAssembly.Cell c : report.result().cells()) {
            if (c.kind() == tankKind) {
                tankCells.add(RocketAssembly.pack(c.x(), c.y(), c.z()));
            }
        }
        for (long cell : report.structures().of(tankKind).loose()) {
            int x = net.minecraft.core.BlockPos.getX(cell);
            int y = net.minecraft.core.BlockPos.getY(cell);
            int z = net.minecraft.core.BlockPos.getZ(cell);
            boolean touches = tankCells.contains(RocketAssembly.pack(x + 1, y, z)) || tankCells.contains(RocketAssembly.pack(x - 1, y, z))
                    || tankCells.contains(RocketAssembly.pack(x, y + 1, z)) || tankCells.contains(RocketAssembly.pack(x, y - 1, z))
                    || tankCells.contains(RocketAssembly.pack(x, y, z + 1)) || tankCells.contains(RocketAssembly.pack(x, y, z - 1));
            if (touches && flagged < 8) {
                addOnce(problems, new Problem(Type.INCOMPLETE_TANK, x, y, z, 0));
                flagged++;
            }
        }
        return flagged;
    }

    /** Uma célula por motor (o mestre de cada estrutura, o bloco de cada motor avulso), em posições do mundo. */
    private static List<Long> engineHeads(Multiblocks.Tiling engines) {
        List<Long> heads = new ArrayList<>();
        for (long cell : engines.loose()) {
            heads.add(cell);
        }
        for (Multiblocks.Box box : engines.boxes()) {
            heads.add(Multiblocks.masterOf(box));
        }
        return heads;
    }

    /** Há um tanque do papel dado numa das colunas do motor (a pegada dele), acima dele? */
    private static boolean hasTankAbove(List<RocketAssembly.Cell> cells, Multiblocks.Tiling engines, RocketBlockKind tankKind, int x, int y, int z) {
        Multiblocks.Box box = Multiblocks.boxAt(engines, x, y, z);
        int x0 = box != null ? box.x() : x;
        int z0 = box != null ? box.z() : z;
        int w = box != null ? box.w() : 1;
        int d = box != null ? box.d() : 1;
        int top = box != null ? box.y() + box.h() : y + 1;
        for (RocketAssembly.Cell c : cells) {
            if (c.kind() == tankKind && c.y() >= top && c.x() >= x0 && c.x() < x0 + w && c.z() >= z0 && c.z() < z0 + d) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- rede

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.padX);
        buf.writeInt(this.padY);
        buf.writeInt(this.padZ);
        buf.writeBoolean(this.found);
        buf.writeVarInt(this.blocks);
        buf.writeVarInt(this.sizeX);
        buf.writeVarInt(this.sizeY);
        buf.writeVarInt(this.sizeZ);
        buf.writeDouble(this.dryMassT);
        buf.writeDouble(this.wetMassT);
        buf.writeDouble(this.fuelCapacityMb);
        buf.writeDouble(this.thrust);
        buf.writeDouble(this.ispTicks);
        buf.writeDouble(this.twr);
        buf.writeDouble(this.deltaV);
        buf.writeVarInt(this.engineBlocks);
        buf.writeVarInt(this.soloEngines);
        buf.writeVarInt(this.engines2);
        buf.writeVarInt(this.engines3);
        buf.writeVarInt(this.tankBlocks);
        buf.writeVarInt(this.looseTanks);
        buf.writeVarInt(this.tankColumns);
        buf.writeVarInt(this.parachutes);
        buf.writeBoolean(this.assemblable);
        buf.writeByte(this.destination);
        buf.writeDouble(this.nextRequiredDeltaV);
        buf.writeVarInt(this.problems.size());
        for (Problem p : this.problems) {
            buf.writeByte(p.type().ordinal());
            buf.writeInt(p.x());
            buf.writeInt(p.y());
            buf.writeInt(p.z());
            buf.writeVarInt(p.arg());
        }
        buf.writeVarInt(this.lines.size());
        for (Line l : this.lines) {
            buf.writeByte(l.kind().ordinal());
            buf.writeVarInt(l.blocks());
            buf.writeVarInt(l.solo());
            buf.writeVarInt(l.cubes2());
            buf.writeVarInt(l.cubes3());
            buf.writeDouble(l.thrust());
            buf.writeDouble(l.ispTicks());
            buf.writeDouble(l.capacityMb());
            buf.writeVarInt(l.power());
        }
    }

    public static DesignReport read(FriendlyByteBuf buf) {
        int padX = buf.readInt();
        int padY = buf.readInt();
        int padZ = buf.readInt();
        boolean found = buf.readBoolean();
        int blocks = buf.readVarInt();
        int sizeX = buf.readVarInt();
        int sizeY = buf.readVarInt();
        int sizeZ = buf.readVarInt();
        double dry = buf.readDouble();
        double wet = buf.readDouble();
        double capacity = buf.readDouble();
        double thrust = buf.readDouble();
        double isp = buf.readDouble();
        double twr = buf.readDouble();
        double dv = buf.readDouble();
        int engineBlocks = buf.readVarInt();
        int solo = buf.readVarInt();
        int e2 = buf.readVarInt();
        int e3 = buf.readVarInt();
        int tankBlocks = buf.readVarInt();
        int loose = buf.readVarInt();
        int columns = buf.readVarInt();
        int chutes = buf.readVarInt();
        boolean assemblable = buf.readBoolean();
        byte destination = buf.readByte();
        if (destination < 0 || destination >= MissionBudget.Destination.values().length) {
            throw new IllegalArgumentException("destino inválido no painel: " + destination);
        }
        double next = buf.readDouble();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PROBLEMS) {
            throw new IllegalArgumentException("problemas demais no painel: " + count);
        }
        List<Problem> problems = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = buf.readByte();
            if (type < 0 || type >= Type.values().length) {
                throw new IllegalArgumentException("tipo de problema inválido: " + type);
            }
            problems.add(new Problem(Type.values()[type], buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt()));
        }
        int lineCount = buf.readVarInt();
        if (lineCount < 0 || lineCount > RocketBlockKind.values().length) {
            throw new IllegalArgumentException("linhas demais no painel: " + lineCount);
        }
        List<Line> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            int kind = buf.readByte();
            if (kind < 0 || kind >= RocketBlockKind.values().length) {
                throw new IllegalArgumentException("papel inválido no painel: " + kind);
            }
            lines.add(new Line(RocketBlockKind.values()[kind], buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readVarInt()));
        }
        return new DesignReport(padX, padY, padZ, found, blocks, sizeX, sizeY, sizeZ, dry, wet, capacity, thrust, isp, twr, dv, engineBlocks, solo, e2, e3,
                tankBlocks, loose, columns, chutes, assemblable, destination, next, List.copyOf(problems), lines);
    }
}
