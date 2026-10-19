package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SunModelTest {
    private static final double C_EARTH = SolarSystem.EARTH.planet().circumferenceBlocks();
    private static final double C_MOON = SolarSystem.MOON.planet().circumferenceBlocks();

    @Test
    void atEarthSpawnTheEffectiveClockIsTheRealClock() {
        for (double clock : new double[] {0, 1000, 6000, 11999, 18000, 23999, 48_123.5}) {
            assertEquals(clock, SunModel.effectiveClock(SolarSystem.EARTH, 0, 0, clock), 1e-6);
        }
    }

    @Test
    void sunRisesInTheEastAndIsAlreadyUpAtTickZeroOnSpawn() {
        // como no vanilla: a curva de Bézier do sun_angle deixa o sol ~12° acima do horizonte leste no tick 0
        double[] local = new double[3];
        SunModel.localDirection(SolarSystem.EARTH, 0, 0, 0, local);
        assertTrue(local[0] > 0.95, "sol deveria estar a leste, x=" + local[0]);
        assertTrue(local[1] > 0.1 && local[1] < 0.3, "sol deveria estar baixo, up=" + local[1]);
        assertEquals(0.0, local[2], 1e-9);
        SunModel.localDirection(SolarSystem.EARTH, 0, 0, 6000, local);
        assertEquals(1.0, local[1], 1e-9);
    }

    @Test
    void skyEaseMatchesTheVanillaBezier() {
        assertEquals(0.0, SunModel.skyEase(0.0), 1e-9);
        assertEquals(0.5, SunModel.skyEase(0.5), 1e-9);
        assertEquals(1.0, SunModel.skyEase(1.0), 1e-9);
        // um quarto do dia depois do meio-dia o sol do vanilla ainda tem ~12° de altura (77,7° do zênite)
        assertEquals(77.7, SunModel.skyEase(0.25) * 360.0, 0.3);
        double prev = 0.0;
        for (double x = 0.0; x <= 1.0; x += 0.01) {
            double y = SunModel.skyEase(x);
            assertTrue(y >= prev - 1e-12, "não monótona em " + x);
            assertEquals(x, SunModel.skyEaseInverse(y), 1e-9);
            prev = y;
        }
    }

    @Test
    void sunSetsAtTheHorizonNearTick12780AndRisesNear23220() {
        double[] local = new double[3];
        double set = Double.NaN;
        double rise = Double.NaN;
        double prevUp = 1.0;
        for (double clock = 6000; clock < 30000; clock += 1) {
            SunModel.localDirection(SolarSystem.EARTH, 0, 0, clock, local);
            if (prevUp > 0 && local[1] <= 0) {
                set = clock;
            }
            if (prevUp <= 0 && local[1] > 0) {
                rise = clock;
            }
            prevUp = local[1];
        }
        assertEquals(12780, set, 30);
        assertEquals(23220, rise, 30);
    }

    @Test
    void vanillaAngleAndAzimuthTiltRebuildTheLocalSun() {
        double[] local = new double[3];
        for (double z : new double[] {0, 4000, -9000, 15000}) {
            for (double x = -30000; x <= 30000; x += 7919) {
                for (double clock = 0; clock < 48000; clock += 1777) {
                    SunModel.localDirection(SolarSystem.EARTH, x, z, clock, local);
                    double a = SunModel.vanillaSkyAngle(local);
                    double tilt = SunModel.azimuthTilt(local);
                    // sol do vanilla (-sen a, cos a, 0) girado por Ry(tilt): x' = x cos, z' = -x sen
                    double vx = -Math.sin(a);
                    double rx = vx * Math.cos(tilt);
                    double rz = -vx * Math.sin(tilt);
                    assertEquals(local[0], rx, 1e-9);
                    assertEquals(local[1], Math.cos(a), 1e-9);
                    assertEquals(local[2], rz, 1e-9);
                }
            }
        }
    }

    @Test
    void highLatitudeSunPathIsTiltedNotEastWest() {
        // a 60° de latitude, no meio-dia do spawn o sol está ao sul do zênite... e ao longo do dia sai do plano leste-cima
        double z = SolarSystem.EARTH.planet().radius() * Math.PI / 3.0;
        double[] local = new double[3];
        double maxNorthSouth = 0;
        for (double clock = 0; clock < 24000; clock += 100) {
            SunModel.localDirection(SolarSystem.EARTH, 0, z, clock, local);
            maxNorthSouth = Math.max(maxNorthSouth, Math.abs(local[2]));
        }
        assertTrue(maxNorthSouth > 0.5, "o caminho deveria ser inclinado, |z| máx=" + maxNorthSouth);
    }

    @Test
    void antipodeOfSpawnHasMidnightAtNoon() {
        // x = C/2 na Terra é o outro lado do planeta: meio-dia no spawn = meia-noite lá
        double clock = 6000;
        double eff = SunModel.effectiveClock(SolarSystem.EARTH, C_EARTH / 2, 0, clock);
        assertEquals(18000, Math.floorMod((long) Math.round(eff), 24000L), 1.0);
    }

    @Test
    void quarterPlanetEastIsAfternoon() {
        // 90° a leste do spawn o sol do meio-dia do spawn está no horizonte oeste: é o pôr do sol do vanilla (~12780)
        double eff = SunModel.effectiveClock(SolarSystem.EARTH, C_EARTH / 4, 0, 6000);
        assertEquals(12780, Math.floorMod((long) Math.round(eff), 24000L), 30.0);
    }

    @Test
    void moonFaceTowardEarthIsLitAtFullMoonAndDarkAtNewMoon() {
        // a face voltada para a Terra (lon = π): no dia 0 à meia-noite (lua cheia) o sol está a pino sobre ela; meio período orbital depois
        // (lua nova) o sol está atrás da Lua e a face fica na noite. A órbita inclinada deixa o sol ~5° fora da vertical.
        double[] local = new double[3];
        SunModel.localDirection(SolarSystem.MOON, C_MOON / 2, 0, 18_000, local);
        assertTrue(local[1] > 0.99, "sol deveria estar a pino na lua cheia, up=" + local[1]);
        SunModel.localDirection(SolarSystem.MOON, C_MOON / 2, 0, 18_000 + SolarSystem.ORBITAL_PERIOD_TICKS / 2, local);
        assertTrue(local[1] < -0.99, "sol deveria estar abaixo do horizonte na lua nova, up=" + local[1]);
    }

    @Test
    void theLunarDayLastsOneOrbitalPeriod() {
        // o "dia" na Lua (nascer do sol a nascer do sol num ponto) dura o ciclo de fases inteiro: o período orbital (~4,12 dias do jogo)
        double period = SolarSystem.ORBITAL_PERIOD_TICKS;
        for (double[] spot : new double[][] {{0, 0}, {C_MOON / 2, 0}, {C_MOON / 4, 2000}, {-C_MOON / 8, -1500}}) {
            double[] local = new double[3];
            double[] later = new double[3];
            // o sol local se repete exatamente a cada ciclo de fases
            for (double clock : new double[] {0, 7777, 50_000, 123_456}) {
                SunModel.localDirection(SolarSystem.MOON, spot[0], spot[1], clock, local);
                SunModel.localDirection(SolarSystem.MOON, spot[0], spot[1], clock + period, later);
                for (int i = 0; i < 3; i++) {
                    assertEquals(local[i], later[i], 1e-9);
                }
            }
            // e há um único nascer por ciclo (o sol não "tremeluz" no horizonte)
            int rises = 0;
            double firstRise = Double.NaN;
            double lastRise = Double.NaN;
            double prevUp = Double.NaN;
            for (double clock = 0; clock < 4 * period; clock += 50) {
                SunModel.localDirection(SolarSystem.MOON, spot[0], spot[1], clock, local);
                if (!Double.isNaN(prevUp) && prevUp <= 0 && local[1] > 0) {
                    rises++;
                    if (Double.isNaN(firstRise)) {
                        firstRise = clock;
                    }
                    lastRise = clock;
                }
                prevUp = local[1];
            }
            assertEquals(4, rises, "nascer do sol em " + spot[0] + "," + spot[1]);
            assertEquals(3 * period, lastRise - firstRise, 200);
        }
    }

    @Test
    void theLunarDayIsOneEffectiveDayStretchedOverOneOrbitalPeriod() {
        // o relógio efetivo na Lua dá uma volta de 24000 ticks (um dia do vanilla) a cada período orbital
        double total = 0;
        double prev = SunModel.effectiveClock(SolarSystem.MOON, 0, 0, 40_000);
        for (double clock = 40_050; clock <= 40_000 + SolarSystem.ORBITAL_PERIOD_TICKS; clock += 50) {
            double eff = SunModel.effectiveClock(SolarSystem.MOON, 0, 0, clock);
            total += Math.IEEEremainder(eff - prev, 24_000);
            prev = eff;
        }
        assertEquals(24_000, total, 300);
    }

    @Test
    void effectiveClockOnTheMoonGivesTheSameSunHeightAsTheEarthSpawnAtThatTime() {
        // definição do horário efetivo: o sol do spawn da Terra no tick efetivo tem a mesma altura que o sol local da Lua
        double[] moonSun = new double[3];
        double[] earthSun = new double[3];
        for (double[] spot : new double[][] {{0, 0}, {C_MOON / 2, 0}, {C_MOON / 3, 800}, {-C_MOON / 5, -1900}}) {
            for (double clock = 0; clock < 2 * SolarSystem.ORBITAL_PERIOD_TICKS; clock += 977) {
                SunModel.localDirection(SolarSystem.MOON, spot[0], spot[1], clock, moonSun);
                double eff = SunModel.effectiveClock(SolarSystem.MOON, spot[0], spot[1], clock);
                SunModel.localDirection(SolarSystem.EARTH, 0, 0, eff, earthSun);
                assertEquals(moonSun[1], earthSun[1], 1e-6, "altura do sol em " + spot[0] + "," + spot[1] + " no tick " + clock);
            }
        }
    }

    @Test
    void earthSpawnIsNotAffectedByTheMoonMotion() {
        for (double clock : new double[] {0, 5000, 18_000, 114_000, 1_000_000.5}) {
            assertEquals(clock, SunModel.effectiveClock(SolarSystem.EARTH, 0, 0, clock), 1e-6);
        }
    }

    @Test
    void effectiveClockIsContinuousAcrossMidnight() {
        double prev = SunModel.effectiveClock(SolarSystem.EARTH, 5000, 300, 23_900);
        for (double clock = 23_910; clock < 24_400; clock += 10) {
            double eff = SunModel.effectiveClock(SolarSystem.EARTH, 5000, 300, clock);
            assertTrue(Math.abs(eff - prev - 10) < 30, "salto em " + clock + ": " + prev + " -> " + eff);
            prev = eff;
        }
    }

    @Test
    void moonDayTimelineCutsTheLightAroundTheHorizonOfTheEffectiveClock() throws Exception {
        // o sol do ponto, levado ao tick efetivo, cruza o horizonte onde o timeline da Lua corta a luz do céu: os keyframes de
        // claro/escuro ficam a ~5° acima/abaixo do horizonte
        try (var in = SunModelTest.class.getResourceAsStream("/data/spacehorizons/timeline/moon_day.json")) {
            assertTrue(in != null, "timeline da Lua ausente");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var frames = json.getAsJsonObject("tracks").getAsJsonObject("minecraft:visual/sky_light_factor").getAsJsonArray("keyframes");
            double[] local = new double[3];
            double[] altitudesDeg = {5, -5, -5, 5};
            assertEquals(4, frames.size());
            for (int i = 0; i < 4; i++) {
                double tick = frames.get(i).getAsJsonObject().get("ticks").getAsDouble();
                // ponto do spawn: o tick efetivo é o próprio relógio
                SunModel.localDirection(SolarSystem.EARTH, 0, 0, tick, local);
                assertEquals(altitudesDeg[i], Math.toDegrees(Math.asin(local[1])), 0.3, "keyframe " + tick);
            }
        }
    }
}
