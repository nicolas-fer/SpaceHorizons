package com.spacehorizons.rocket.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.MissionBudget.Destination;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketCensus;
import com.spacehorizons.rocket.RocketDesign;
import com.spacehorizons.rocket.RocketSpecs;
import com.spacehorizons.rocket.TestWorld;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtOps;

/**
 * A planta de montagem (docs/PLANO_TIERS.md, seção 4): o formato (NBT, JSON e rede), as plantas embutidas contra os projetos que o simulador
 * da missão voou, a comparação planta × mundo (falta, errado, certo) e salvar a estrutura sobre a plataforma como planta.
 */
class BlueprintTest {
    /** O nome de estado que o "jogo" de brinquedo dá a cada papel (o que BlueprintStates.serialize faria). */
    private static String stateOf(RocketBlockKind kind) {
        return switch (kind) {
            case PAD -> Blueprints.PAD;
            case ENGINE -> Blueprints.ENGINE;
            case FUEL_TANK -> Blueprints.TANK;
            case COCKPIT -> Blueprints.COCKPIT;
            case PARACHUTE -> Blueprints.PARACHUTE;
            case HULL -> Blueprints.HULL;
            default -> "minecraft:" + kind.name().toLowerCase(Locale.ROOT);
        };
    }

    // ---------------------------------------------------------------- formato

    @Test
    void oFormatoVaiEVoltaPorNbtJsonERede() {
        Blueprint saved = Blueprint.builder("", "Meu foguete", "Nicolas").add(0, 0, 0, Blueprints.PAD, RocketBlockKind.PAD)
                .add(0, 1, 0, Blueprints.ENGINE, RocketBlockKind.ENGINE).add(0, 2, 0, Blueprints.TANK, RocketBlockKind.FUEL_TANK)
                .add(-3, 3, 7, "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", RocketBlockKind.STRUCTURE)
                .add(0, 3, 0, Blueprints.COCKPIT, RocketBlockKind.COCKPIT).build();
        for (Blueprint b : new Blueprint[] {saved, Blueprints.SUBORBITAL, Blueprints.ORBITAL, Blueprints.LUNAR}) {
            var nbt = Blueprint.CODEC.encodeStart(NbtOps.INSTANCE, b).getOrThrow();
            assertEquals(b, Blueprint.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(), "NBT de " + b);
            var json = Blueprint.CODEC.encodeStart(JsonOps.INSTANCE, b).getOrThrow();
            assertEquals(b, Blueprint.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), "JSON de " + b);
            ByteBuf buf = Unpooled.buffer();
            Blueprint.STREAM_CODEC.encode(buf, b);
            Blueprint back = Blueprint.STREAM_CODEC.decode(buf);
            assertEquals(b, back, "rede de " + b);
            assertEquals(b.hashCode(), back.hashCode());
            assertEquals(0, buf.readableBytes(), "o pacote foi lido inteiro");
        }
        // o que foi gravado volta igual, bloco a bloco
        Blueprint back = Blueprint.CODEC.parse(JsonOps.INSTANCE, Blueprint.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow()).getOrThrow();
        assertEquals(5, back.size());
        assertEquals("Meu foguete", back.name());
        assertEquals("Nicolas", back.author());
        assertEquals(-3, back.minX());
        assertEquals(7, back.maxZ());
        int stairs = -1;
        for (int i = 0; i < back.size(); i++) {
            if (back.dx(i) == -3) {
                stairs = i;
            }
        }
        assertTrue(stairs >= 0);
        assertEquals(3, back.dy(stairs));
        assertEquals(7, back.dz(stairs));
        assertEquals(RocketBlockKind.STRUCTURE, back.kind(stairs));
        assertTrue(back.state(stairs).startsWith("minecraft:oak_stairs[facing=east"));
    }

    @Test
    void comparaPorValorERecusaDadosInvalidos() {
        assertEquals(Blueprints.design("orbital", 2, 1, 4, 1, 0), Blueprints.ORBITAL);
        assertNotEquals(Blueprints.ORBITAL, Blueprints.ORBITAL.withName("outro"));
        assertNotEquals(Blueprints.ORBITAL, Blueprints.SUBORBITAL);
        // a mesma posição duas vezes fica com a última
        Blueprint twice = Blueprint.builder("", "", "").add(0, 1, 0, Blueprints.HULL, RocketBlockKind.HULL).add(0, 1, 0, Blueprints.TANK, RocketBlockKind.FUEL_TANK).build();
        assertEquals(1, twice.size());
        assertEquals(RocketBlockKind.FUEL_TANK, twice.kind(0));
        // fora do alcance e índice de paleta inválido são recusados
        assertThrows(IllegalArgumentException.class, () -> Blueprint.builder("", "", "").add(600, 0, 0, Blueprints.HULL, RocketBlockKind.HULL));
        var json = Blueprint.CODEC.encodeStart(JsonOps.INSTANCE, Blueprints.SUBORBITAL).getOrThrow().getAsJsonObject();
        json.getAsJsonArray("states").set(0, new com.google.gson.JsonPrimitive(99));
        assertTrue(Blueprint.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), "índice fora da paleta é erro");
        // papel desconhecido (de uma versão futura) vale como estrutura
        var json2 = Blueprint.CODEC.encodeStart(JsonOps.INSTANCE, Blueprints.SUBORBITAL).getOrThrow().getAsJsonObject();
        json2.getAsJsonArray("kinds").set(0, new com.google.gson.JsonPrimitive("WARP_CORE"));
        assertEquals(RocketBlockKind.STRUCTURE, Blueprint.CODEC.parse(JsonOps.INSTANCE, json2).getOrThrow().kinds().get(0));
        // empacotamento dos deslocamentos nas bordas
        for (int[] d : new int[][] {{-512, -512, -512}, {511, 511, 511}, {0, 0, 0}, {-1, 200, 37}}) {
            int p = Blueprint.pack(d[0], d[1], d[2]);
            assertEquals(d[0], Blueprint.unpackX(p));
            assertEquals(d[1], Blueprint.unpackY(p));
            assertEquals(d[2], Blueprint.unpackZ(p));
        }
    }

    // ---------------------------------------------------------------- plantas embutidas

    private static void sameAsSimulated(Blueprint blueprint, TestWorld.Design simulated, Destination expected) {
        RocketDesign.Search search = RocketDesign.search(blueprint.grid(), 0, 0, 0);
        assertNotNull(search.report(), "a busca do painel acha o foguete da planta " + blueprint);
        RocketCensus fromBlueprint = search.report().result().census();
        RocketCensus fromSimulated = simulated.analyze().result().census();
        assertTrue(fromBlueprint.sameShape(fromSimulated), "mesmo censo do projeto voado no simulador: " + fromBlueprint + " x " + fromSimulated);
        var a = RocketSpecs.statsFor(fromBlueprint);
        var b = RocketSpecs.statsFor(fromSimulated);
        assertEquals(b.thrust(), a.thrust(), 1.0e-9);
        assertEquals(b.dryMassT(), a.dryMassT(), 1.0e-9);
        assertEquals(b.fuelCapacityMb(), a.fuelCapacityMb(), 1.0e-9);
        DesignReport design = blueprint.design();
        assertTrue(design.found());
        assertTrue(design.assemblable(), "a planta montada é montável: " + design.problems());
        assertEquals(expected, design.destinationValue(), "destino do projeto da planta " + blueprint + " (Δv " + design.deltaV() + ", TWR " + design.twr() + ")");
        // as plataformas da base: a âncora e a pegada com a borda
        assertEquals(RocketBlockKind.PAD, blueprint.grid().kindAt(0, 0, 0));
        assertEquals(RocketBlockKind.PAD, blueprint.grid().kindAt(-1, 0, -1));
        assertTrue(blueprint.count(RocketBlockKind.PAD) >= 9);
    }

    @Test
    void asPlantasEmbutidasSaoOsProjetosQueOSimuladorVoou() {
        // os mesmos projetos de MissionBudgetTest (projetoDeSaltoSuborbital..., projetoDeOrbita..., projetoDeLuaPousoEVolta...)
        sameAsSimulated(Blueprints.SUBORBITAL, TestWorld.design(1, 1, 8, 1, 0, true), Destination.SUBORBITAL);
        sameAsSimulated(Blueprints.ORBITAL, TestWorld.design(2, 1, 4, 1, 0, true), Destination.ORBIT);
        sameAsSimulated(Blueprints.LUNAR, TestWorld.design(0, 3, 5, 3, 8, true), Destination.MOON_LANDING_RETURN);
        assertFalse(Blueprints.byId("orbital").locked());
        assertTrue(Blueprints.byId("lunar").locked(), "a lunar vem da história (base abandonada): trancada em sobrevivência");
        assertNull(Blueprints.byId("nada"));
    }

    // ---------------------------------------------------------------- planta x mundo

    /** Mundo de brinquedo por strings de estado, com a mesma regra de comparação do jogo. */
    private static BlueprintCheck.Progress check(Blueprint b, Map<Long, String> world, int px, int py, int pz) {
        return BlueprintCheck.check(b, (entry, dx, dy, dz) -> BlueprintCheck.compare(b.state(entry), world.get(key(px + dx, py + dy, pz + dz))));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFF;
    }

    @Test
    void comparaPlantaComMundoFaltandoErradoECerto() {
        Blueprint b = Blueprints.ORBITAL;
        Map<Long, String> world = new HashMap<>();
        int px = 100;
        int py = 64;
        int pz = -20;
        // plataforma vazia (só a âncora posta): tudo falta, menos a âncora
        world.put(key(px, py, pz), Blueprints.PAD);
        BlueprintCheck.Progress p = check(b, world, px, py, pz);
        assertEquals(b.size(), p.total());
        assertEquals(1, p.correct());
        assertEquals(b.size() - 1, p.missing());
        assertEquals(0, p.wrong());
        assertFalse(p.complete());
        // parte posta: as plataformas e os motores certos; um casco no lugar de um tanque e um tanque no lugar do cockpit (errados)
        int tankEntry = -1;
        int cockpitEntry = -1;
        for (int i = 0; i < b.size(); i++) {
            String s = b.state(i);
            int x = px + b.dx(i);
            int y = py + b.dy(i);
            int z = pz + b.dz(i);
            if (s.equals(Blueprints.PAD) || s.equals(Blueprints.ENGINE)) {
                world.put(key(x, y, z), s);
            } else if (s.equals(Blueprints.TANK) && tankEntry < 0) {
                tankEntry = i;
                world.put(key(x, y, z), Blueprints.HULL);
            } else if (s.equals(Blueprints.COCKPIT)) {
                cockpitEntry = i;
                world.put(key(x, y, z), Blueprints.TANK);
            }
        }
        p = check(b, world, px, py, pz);
        int pads = b.count(RocketBlockKind.PAD);
        int engines = b.count(RocketBlockKind.ENGINE);
        assertEquals(pads + engines, p.correct());
        assertEquals(2, p.wrong());
        assertEquals(b.size() - pads - engines - 2, p.missing());
        assertEquals(BlueprintCheck.WRONG, p.status()[tankEntry]);
        assertEquals(BlueprintCheck.WRONG, p.status()[cockpitEntry]);
        assertEquals(p.missing() + p.wrong(), p.toDo());
        // completa: some tudo
        for (int i = 0; i < b.size(); i++) {
            world.put(key(px + b.dx(i), py + b.dy(i), pz + b.dz(i)), b.state(i));
        }
        p = check(b, world, px, py, pz);
        assertTrue(p.complete());
        assertEquals(b.size(), p.correct());
        // estado desconhecido (mod ausente) não conta
        assertEquals(BlueprintCheck.UNKNOWN, BlueprintCheck.compare(null, "x"));
        assertEquals(BlueprintCheck.MISSING, BlueprintCheck.compare("x", null));
    }

    // ---------------------------------------------------------------- salvar a estrutura como planta

    @Test
    void salvaOFogueteSobreAPlataformaComoPlantaEReaplica() {
        TestWorld.Design d = TestWorld.design(3, 2, 8, 2, 20, true); // o "Lua e volta" do painel
        // a plataforma clicada é a do canto (2, 0, 2) da base 7x7: vira a âncora
        int px = 2;
        int py = 0;
        int pz = 2;
        Blueprint saved = BlueprintCapture.capture(d.world(), (x, y, z) -> stateOf(d.world().kindAt(x, y, z)), px, py, pz, "", "Teste");
        assertNotNull(saved);
        assertEquals("Teste", saved.author());
        RocketAssembly.Report original = d.analyze();
        int parts = original.result().cells().size();
        assertEquals(parts, saved.size() - saved.count(RocketBlockKind.PAD), "todas as peças que a montagem leva");
        // as plataformas: a pegada do foguete (x e z de -1 a 2, por causa dos cascos) mais a borda de 1, dentro da base 7x7
        assertEquals(RocketBlockKind.PAD, saved.grid().kindAt(0, 0, 0));
        assertEquals(RocketBlockKind.PAD, saved.grid().kindAt(-2 - px, 0, -2 - pz));
        assertEquals(RocketBlockKind.AIR, saved.grid().kindAt(-3 - px, 0, -3 - pz), "plataforma longe do foguete não entra");
        // o projeto da planta é o do foguete original (o mesmo cálculo do painel)
        DesignReport fromWorld = RocketDesign.analyze(d.world(), px, py, pz);
        DesignReport fromBlueprint = saved.design();
        assertEquals(fromWorld.blocks(), fromBlueprint.blocks());
        assertEquals(fromWorld.thrust(), fromBlueprint.thrust(), 1.0e-9);
        assertEquals(fromWorld.deltaV(), fromBlueprint.deltaV(), 1.0e-9);
        assertEquals(fromWorld.destinationValue(), fromBlueprint.destinationValue());
        assertEquals(Destination.MOON_RETURN, fromBlueprint.destinationValue());
        // reaplicada no mesmo lugar: tudo certo
        Map<Long, String> world = new HashMap<>();
        for (int y = -1; y < 30; y++) {
            for (int z = -4; z <= 4; z++) {
                for (int x = -4; x <= 4; x++) {
                    RocketBlockKind k = d.world().kindAt(x, y, z);
                    if (k != RocketBlockKind.AIR) {
                        world.put(key(x, y, z), stateOf(k));
                    }
                }
            }
        }
        assertTrue(check(saved, world, px, py, pz).complete());
        // reaplicada em outra plataforma (vazia, só a âncora): tudo falta menos a âncora
        Map<Long, String> other = new HashMap<>();
        other.put(key(500, 70, 500), Blueprints.PAD);
        BlueprintCheck.Progress p = check(saved, other, 500, 70, 500);
        assertEquals(saved.size() - 1, p.missing());
        assertEquals(1, p.correct());
        // nada sobre a plataforma: não há o que salvar
        TestWorld empty = new TestWorld().fill(-3, 0, -3, 7, 1, 7, RocketBlockKind.PAD);
        assertNull(BlueprintCapture.capture(empty, (x, y, z) -> stateOf(empty.kindAt(x, y, z)), 0, 0, 0, "", "Teste"));
        // um foguete incompleto (sem cockpit) também vira planta
        TestWorld partial = new TestWorld().fill(-3, 0, -3, 7, 1, 7, RocketBlockKind.PAD).fill(0, 1, 0, 2, 2, 2, RocketBlockKind.ENGINE);
        Blueprint half = BlueprintCapture.capture(partial, (x, y, z) -> stateOf(partial.kindAt(x, y, z)), 0, 0, 0, "", "Teste");
        assertNotNull(half);
        assertEquals(8, half.count(RocketBlockKind.ENGINE));
        assertFalse(half.design().assemblable());
    }
}
