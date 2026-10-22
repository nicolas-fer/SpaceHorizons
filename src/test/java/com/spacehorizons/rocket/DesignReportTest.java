package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.DesignReport.Problem;
import com.spacehorizons.rocket.DesignReport.Type;
import com.spacehorizons.rocket.MissionBudget.Destination;
import com.spacehorizons.space.flight.RocketStats;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/** O relatório do painel de projeto: os números são os da montagem, os destinos os do orçamento, e os problemas apontam o bloco certo. */
class DesignReportTest {
    private static final class World implements RocketAssembly.Grid {
        private final Map<Long, RocketBlockKind> blocks = new HashMap<>();

        World put(int x, int y, int z, RocketBlockKind kind) {
            this.blocks.put(RocketAssembly.pack(x, y, z), kind);
            return this;
        }

        World fill(int x0, int y0, int z0, int w, int h, int d, RocketBlockKind kind) {
            for (int y = y0; y < y0 + h; y++) {
                for (int z = z0; z < z0 + d; z++) {
                    for (int x = x0; x < x0 + w; x++) {
                        put(x, y, z, kind);
                    }
                }
            }
            return this;
        }

        @Override
        public RocketBlockKind kindAt(int x, int y, int z) {
            return this.blocks.getOrDefault(RocketAssembly.pack(x, y, z), RocketBlockKind.AIR);
        }
    }

    private static DesignReport report(World w, int sx, int sy, int sz) {
        return DesignReport.of(RocketAssembly.analyze(w, List.of(new int[] {sx, sy, sz})), 0, 0, 0);
    }

    /** Plataforma, 3x3 de motores avulsos, coluna de tanques 3x3x5, cockpit e paraquedas: como o foguete da missão. */
    private static World missionLike() {
        World w = new World().fill(-2, 0, -2, 5, 1, 5, RocketBlockKind.PAD);
        w.fill(-1, 1, -1, 3, 1, 3, RocketBlockKind.ENGINE);
        w.fill(-1, 2, -1, 3, 5, 3, RocketBlockKind.FUEL_TANK);
        return w.put(0, 7, 0, RocketBlockKind.COCKPIT).put(0, 8, 0, RocketBlockKind.PARACHUTE);
    }

    private static boolean has(DesignReport r, Type type) {
        return r.problems().stream().anyMatch(p -> p.type() == type);
    }

    @Test
    void osNumerosSaoOsDaMontagemEODestinoOdoOrcamento() throws Exception {
        World w = missionLike();
        DesignReport r = report(w, 0, 7, 0);
        RocketAssembly.Result scanned = RocketAssembly.scan(w, 0, 7, 0);
        RocketStats stats = RocketSpecs.statsFor(scanned.census());
        assertTrue(r.found());
        assertEquals(scanned.cells().size(), r.blocks());
        assertEquals(stats.dryMassT(), r.dryMassT(), 1.0e-12);
        assertEquals(stats.thrust(), r.thrust(), 1.0e-12);
        assertEquals(stats.ispTicks(), r.ispTicks(), 1.0e-12);
        assertEquals(stats.fuelCapacityMb(), r.fuelCapacityMb(), 1.0e-12);
        assertEquals(stats.wetMass(stats.fuelCapacityMb()), r.wetMassT(), 1.0e-12);
        assertEquals(stats.deltaV(stats.fuelCapacityMb()), r.deltaV(), 1.0e-12);
        assertEquals(stats.twr(stats.fuelCapacityMb(), DesignReport.EARTH_GRAVITY), r.twr(), 1.0e-12);
        assertEquals(MissionBudget.reachable(r.deltaV(), r.twr(), true), r.destinationValue());
        assertTrue(r.assemblable());
        assertEquals(9, r.soloEngines());
        assertEquals(1, r.tankColumns(), "a coluna 3x3x5 se forma e o casco pesa menos");
        assertEquals(0, r.looseTanks());
    }

    @Test
    void umaColunaFormadaDaMaisDeltaVQueOsMesmosTanquesAvulsos() {
        // 36 tanques: uma coluna 3x3x4, ou duas camadas 3x3x2 separadas por uma camada de casco (altura 2 < 4: nenhuma coluna se forma)
        World formed = new World().fill(-2, 0, -2, 5, 1, 5, RocketBlockKind.PAD).fill(-1, 1, -1, 3, 1, 3, RocketBlockKind.ENGINE)
                .fill(-1, 2, -1, 3, 4, 3, RocketBlockKind.FUEL_TANK).put(0, 6, 0, RocketBlockKind.COCKPIT).put(0, 7, 0, RocketBlockKind.PARACHUTE);
        World loose = new World().fill(-2, 0, -2, 5, 1, 5, RocketBlockKind.PAD).fill(-1, 1, -1, 3, 1, 3, RocketBlockKind.ENGINE)
                .fill(-1, 2, -1, 3, 2, 3, RocketBlockKind.FUEL_TANK).fill(-1, 4, -1, 3, 1, 3, RocketBlockKind.HULL)
                .fill(-1, 5, -1, 3, 2, 3, RocketBlockKind.FUEL_TANK).put(0, 7, 0, RocketBlockKind.COCKPIT).put(0, 8, 0, RocketBlockKind.PARACHUTE);
        DesignReport a = report(formed, 0, 6, 0);
        DesignReport b = report(loose, 0, 7, 0);
        assertEquals(1, a.tankColumns());
        assertEquals(0, b.tankColumns());
        assertEquals(a.fuelCapacityMb(), b.fuelCapacityMb(), 1.0e-9, "a mesma capacidade: 4.000 mB por bloco");
        assertTrue(a.dryMassT() < b.dryMassT(), "casco mais leve: " + a.dryMassT() + " contra " + b.dryMassT());
        assertTrue(a.deltaV() > b.deltaV());
    }

    @Test
    void semParaquedasAvisaNoBlocoMaisAltoELimitaAVoltaDaLua() {
        World w = missionLike().put(0, 8, 0, RocketBlockKind.HULL);
        DesignReport r = report(w, 0, 7, 0);
        Problem p = r.problems().stream().filter(x -> x.type() == Type.NO_PARACHUTE).findFirst().orElseThrow();
        assertEquals(8, p.y(), "o bloco mais alto");
        assertEquals(0, r.parachutes());
        assertTrue(r.destinationValue().ordinal() <= Destination.ORBIT.ordinal(), "sem paraquedas não promete a volta da Lua");
        assertTrue(r.assemblable(), "a falta de paraquedas é um aviso, não impede montar");
    }

    @Test
    void motorSemTanqueAcimaApontaOMotor() {
        // dois motores; só o de x=0 tem tanque acima
        World w = new World().fill(-1, 0, -1, 4, 1, 3, RocketBlockKind.PAD);
        w.put(0, 1, 0, RocketBlockKind.ENGINE).put(2, 1, 0, RocketBlockKind.ENGINE);
        w.fill(0, 2, 0, 1, 4, 1, RocketBlockKind.FUEL_TANK).put(0, 6, 0, RocketBlockKind.COCKPIT);
        w.put(1, 1, 0, RocketBlockKind.HULL).put(2, 2, 0, RocketBlockKind.HULL).put(1, 2, 0, RocketBlockKind.HULL);
        DesignReport r = report(w, 0, 6, 0);
        List<Problem> bad = r.problems().stream().filter(x -> x.type() == Type.ENGINE_WITHOUT_TANK).toList();
        assertEquals(1, bad.size());
        assertEquals(2, bad.get(0).x());
        assertEquals(1, bad.get(0).y());
    }

    @Test
    void estruturaDeTanqueIncompletaApontaOsBlocosAvulsosQueEncostam() {
        // coluna 2x2 com só 3 de altura: não forma (mínimo 4) e os 12 blocos ficam avulsos encostados uns nos outros
        World w = new World().fill(-1, 0, -1, 4, 1, 4, RocketBlockKind.PAD);
        w.put(0, 1, 0, RocketBlockKind.ENGINE).fill(0, 2, 0, 2, 3, 2, RocketBlockKind.FUEL_TANK).put(0, 5, 0, RocketBlockKind.COCKPIT);
        DesignReport r = report(w, 0, 5, 0);
        assertTrue(has(r, Type.INCOMPLETE_TANK));
        assertEquals(12, r.looseTanks());
        assertEquals(0, r.tankColumns());
        assertTrue(r.problems().stream().filter(x -> x.type() == Type.INCOMPLETE_TANK).count() <= 8, "no máximo 8 destaques");
        // com a altura certa (4) a coluna se forma e o aviso some
        World ok = new World().fill(-1, 0, -1, 4, 1, 4, RocketBlockKind.PAD);
        ok.put(0, 1, 0, RocketBlockKind.ENGINE).fill(0, 2, 0, 2, 4, 2, RocketBlockKind.FUEL_TANK).put(0, 6, 0, RocketBlockKind.COCKPIT);
        assertFalse(has(report(ok, 0, 6, 0), Type.INCOMPLETE_TANK));
        assertEquals(1, report(ok, 0, 6, 0).tankColumns());
    }

    @Test
    void encostadoNoTerrenoApontaOBlocoDeTerrenoEImpedeMontar() {
        World w = missionLike().put(2, 3, 0, RocketBlockKind.TERRAIN);
        DesignReport r = report(w, 0, 7, 0);
        Problem p = r.problems().stream().filter(x -> x.type() == Type.TOUCHES_TERRAIN).findFirst().orElseThrow();
        assertEquals(List.of(2, 3, 0), List.of(p.x(), p.y(), p.z()));
        assertFalse(r.assemblable());
        assertTrue(p.type().blocking());
    }

    @Test
    void semMotorSemTanqueSemCockpitMultiplosCockpitsSaoProblemasQueImpedem() {
        World semMotor = missionLike().fill(-1, 1, -1, 3, 1, 3, RocketBlockKind.HULL);
        assertTrue(has(report(semMotor, 0, 7, 0), Type.NO_ENGINE));
        World doisCockpits = missionLike().put(0, 9, 0, RocketBlockKind.COCKPIT);
        DesignReport r = report(doisCockpits, 0, 7, 0);
        assertTrue(has(r, Type.MULTIPLE_COCKPITS));
        assertFalse(r.assemblable());
        // sem cockpit nenhum (parte do projeto): o relatório ainda existe, com o problema
        World semCockpit = missionLike().put(0, 7, 0, RocketBlockKind.HULL);
        DesignReport s = DesignReport.of(RocketAssembly.analyze(semCockpit, List.of(new int[] {0, 6, 0})), 0, 0, 0);
        assertTrue(has(s, Type.NO_COCKPIT));
        assertTrue(s.found());
        assertTrue(s.deltaV() > 0);
    }

    @Test
    void twrAbaixoDe1NaoDecolaEEntre1E1Virgula2SoChegaAoSalto() {
        // muito peso e pouco empuxo: 1 motor e 20 colunas de tanque
        World pesado = new World().fill(-2, 0, -2, 5, 1, 5, RocketBlockKind.PAD);
        pesado.put(0, 1, 0, RocketBlockKind.ENGINE).fill(-1, 2, -1, 3, 12, 3, RocketBlockKind.FUEL_TANK).put(0, 14, 0, RocketBlockKind.COCKPIT);
        DesignReport r = report(pesado, 0, 14, 0);
        assertTrue(r.twr() < 1.0, "twr " + r.twr());
        assertEquals(Destination.NO_LIFTOFF, r.destinationValue());
        assertTrue(has(r, Type.LOW_TWR));
    }

    @Test
    void relatorioViajaPelaRedeIgual() {
        DesignReport r = report(missionLike().put(2, 3, 0, RocketBlockKind.TERRAIN), 0, 7, 0);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        r.write(buf);
        DesignReport back = DesignReport.read(buf);
        assertEquals(r, back);
        assertEquals(0, buf.readableBytes());
        FriendlyByteBuf empty = new FriendlyByteBuf(Unpooled.buffer());
        DesignReport.nothing(3, 4, 5).write(empty);
        assertEquals(DesignReport.nothing(3, 4, 5), DesignReport.read(empty));
    }

    @Test
    void relatorioMalformadoEhRecusado() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        DesignReport.nothing(0, 0, 0).write(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        // o byte do destino fica antes do double, da contagem de problemas e da contagem de linhas do Tier 2: força um destino inexistente
        int destinationIndex = bytes.length - 1 - 1 - 8 - 1;
        bytes[destinationIndex] = 99;
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DesignReport.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes))));
    }

    @Test
    void numeroDeProblemasEhLimitado() {
        World w = missionLike();
        // terreno colado em volta da coluna de tanques: 60 blocos de terreno, cada um um problema
        for (int y = 2; y <= 6; y++) {
            for (int i = -1; i <= 1; i++) {
                w.put(2, y, i, RocketBlockKind.TERRAIN).put(-2, y, i, RocketBlockKind.TERRAIN);
                w.put(i, y, 2, RocketBlockKind.TERRAIN).put(i, y, -2, RocketBlockKind.TERRAIN);
            }
        }
        RocketAssembly.Report analysis = RocketAssembly.analyze(w, List.of(new int[] {0, 7, 0}));
        assertTrue(analysis.blockers().size() > DesignReport.MAX_PROBLEMS, "mais de 24 recusas: " + analysis.blockers().size());
        assertEquals(DesignReport.MAX_PROBLEMS, DesignReport.of(analysis, 0, 0, 0).problems().size());
    }
}
