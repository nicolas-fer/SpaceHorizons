# Relatório técnico 3c: dimensão de espaço vazia e desenho de Terra/Lua de dentro dela

Legenda: **[CONF]** foi lido no código (fontes do MC 26.3 patched, NeoForge 26.3.0.76 ou o mod). **[INF]** é dedução ou estimativa. Nada foi executado. Os arquivos extraídos ficaram só no scratchpad e `git status` do projeto está limpo.

## 0. Resumo e decisões recomendadas

1. **Registro.** `spacehorizons:space` entra no mesmo lugar da Lua, o world preset. Tipo `spacehorizons:space`, gerador `minecraft:flat` vazio, altura 16, `has_skylight:true`, `has_ceiling:true` (desliga o clima) e `skybox:"overworld"`. É tudo JSON, sem código de worldgen.
2. **Eixos.** Os eixos da dimensão coincidem com a base local da Terra no spawn (x leste, y cima, z norte), mais um viés de +20.000 em Y. Com isso o sol, as estrelas e o resto do céu vanilla funcionam sem inclinação nem rotação extra.
3. **Renderização.** `BodyRenderer` já é quase genérico. Falta trocar "câmera = (planeta, x, y, z)" por um `SkyObserver`. Mudam uns 6 arquivos de cliente e nenhum shader.
4. **Chunks.** Gerar chunk vazio é barato. Mas o custo fixo por chunk continua, e o NeoForge força `getChunk` bloqueante em todo `Entity.setPosRaw`. Mitigações: gerador vazio, altura 16, tickets à frente da nave, "gating" e `chunkSource.move(player)` explícito. Medir antes de fechar. O plano B é a origem flutuante.
5. **Achados laterais** (ambos [CONF]):
   - `has_skylight:true` liga `canHaveWeather()`. Hoje a chuva do mundo escurece a Lua: estrelas vão a 0 e `sky_light_factor` cai a 0,24.
   - Oxigênio, gravidade e `TravelServer` tratam "sem planeta" como ar respirável, g=1 e sem viagem. No espaço precisam de um ramo próprio.

---

## 1. A dimensão

### 1.1 Como a Lua é registrada hoje [CONF]

- **Não existe `data/spacehorizons/dimension/`.** A Lua mora dentro do world preset, em `worldgen/world_preset/earth.json`:
  - `"spacehorizons:moon": {type: spacehorizons:moon, generator: {type: spacehorizons:planet, planet: spacehorizons:moon, biome_source: {type: spacehorizons:planet, biomes: {regolith|mare|highland|crater_floor → spacehorizons:lunar}}}}`.
  - O preset aparece na tela de criar mundo via `data/minecraft/tags/worldgen/world_preset/normal.json`.
- **Tipos de gerador** vêm de `WorldgenSetup`: `planet` → `PlanetChunkGenerator.CODEC`, `vanilla_planet` → `PlanetNoiseChunkGenerator`, e biome_source `planet`.
- **`dimension_type/moon.json`:** `has_skylight:true`, `has_ceiling:false`, `skybox:"none"`, `min_y -64`, `height 384`, `default_clock: minecraft:overworld`, `timelines:[spacehorizons:moon_day]`.
- **`Planets.forLevel(level)`** resolve pela chave do **tipo** de dimensão (`level.dimensionTypeRegistration().unwrapKey()`), com `BY_ID = Map.of(EARTH, MOON)`. Servidor e cliente chegam à mesma resposta sem pacote.
- **`PlanetChunkGenerator.buildTerrain`** é analítico: um laço por coluna de `MIN_Y` até o topo, sem estruturas nem cavernas.

**Mundo existente vs. novo (decisão de registro).** `WorldDimensions.bake(baseDimensions)` [CONF] une as chaves do registro de datapack `LEVEL_STEM` com as do `level.dat`. Para cada chave, `baseDimensions.getOptional(key)` tem **prioridade**, e o `level.dat` só entra como reserva.

| Opção | Efeito [CONF] |
|---|---|
| (a) Só no preset (como a Lua) | Só mundos criados depois. Mundos antigos não ganham a dimensão. O autoteste cria mundo novo a cada rodada (`spacehorizons.autotest.fresh`), então funciona. |
| (b) `data/spacehorizons/dimension/space.json` | Vale para todos os mundos, inclusive os antigos e os vanilla. `checkStability` marca entradas não-vanilla como `Lifecycle.experimental()`. O aviso de "configurações experimentais" em mundos vanilla é consequência esperada [INF]. |

**Recomendo (a)**, por consistência com a Lua, e tratar a ausência como degradação suave. `FlightGuide` já recebe o predicado `available`, e `TravelTransfers.findLevel` devolve null. Se for preciso migrar mundos de teste, use (b) só em dev.

### 1.2 Mínimo para `spacehorizons:space`

`data/spacehorizons/dimension_type/space.json` (campos conferidos contra `DimensionType.createDirectCodec`):

```json
{
  "ambient_light": 0.0,
  "attributes": {
    "minecraft:audio/background_music": { "creative": {"max_delay":24000,"min_delay":12000,"sound":"minecraft:music.creative"},
                                          "default":  {"max_delay":24000,"min_delay":12000,"sound":"minecraft:music.game"} },
    "minecraft:gameplay/bed_rule": { "can_set_spawn":"never","can_sleep":"never","error_message":{"translate":"block.minecraft.bed.no_sleep"} },
    "minecraft:gameplay/respawn_anchor_works": false,
    "minecraft:gameplay/straw_bed_rule": { "can_set_spawn":"never","can_sleep":"never","error_message":{"translate":"block.minecraft.bed.no_sleep"} },
    "minecraft:visual/ambient_light_color": "#0a0a0a",
    "minecraft:visual/cloud_color": "#00000000",
    "minecraft:visual/fog_color": "#000000",
    "minecraft:visual/fog_start_distance": 8192.0,
    "minecraft:visual/fog_end_distance": 16384.0,
    "minecraft:visual/sky_color": "#000000",
    "minecraft:visual/sky_light_color": "#ffffff",
    "minecraft:visual/sky_light_factor": 1.0,
    "minecraft:visual/star_brightness": 1.0,
    "minecraft:visual/sunrise_sunset_color": "#00000000"
  },
  "coordinate_scale": 1.0,
  "default_clock": "minecraft:overworld",
  "has_ceiling": true,
  "has_ender_dragon_fight": false,
  "has_skylight": true,
  "height": 16,
  "infiniburn": "#minecraft:infiniburn_overworld",
  "logical_height": 16,
  "min_y": 0,
  "monster_spawn_block_light_limit": 0,
  "monster_spawn_light_level": {"type":"minecraft:uniform","max_inclusive":7,"min_inclusive":0},
  "skybox": "overworld"
}
```

Entrada no preset (e opcionalmente em `dimension/space.json`):

```json
"spacehorizons:space": {
  "type": "spacehorizons:space",
  "generator": { "type": "minecraft:flat",
    "settings": { "biome": "spacehorizons:space", "features": false, "lakes": false,
                  "layers": [], "structure_overrides": [] } }
}
```

`worldgen/biome/space.json` é uma cópia de `lunar.json`: `has_precipitation:false`, listas de spawn vazias, `sky_color #000000`.

**Por que cada escolha:**

- **`height:16`, `min_y:0`.** O codec aceita no mínimo 16 [CONF]. Uma só seção por chunk reduz tudo que escala com o número de seções:
  - `LevelChunk` e `PalettedContainer`;
  - `collectPossibleBiomes` (9 chunks × seções, no passo TERRAIN);
  - o laço de biomas em `applyBiomeDecoration`;
  - a luz;
  - o pacote de chunk e os heightmaps, cujos bits caem de 9 para 5.

  O ganho é certo. O efeito em tempo ainda não foi medido [INF]. Validar em runtime que nada no vanilla, NeoForge ou DH assume mais de uma seção [INF].
- **`has_ceiling:true`** [CONF]:
  - `Level.canHaveWeather() = hasSkyLight && !hasCeiling && dimension != END`.
  - Com clima ligado, `EnvironmentAttributeSystem.addDynamicLayers` adiciona `WeatherAttributes`.
  - Em chuva, `STAR_BRIGHTNESS := 0` e `SKY_LIGHT_FACTOR → 0,24`. Em trovoada, os mesmos atributos com mais peso.
  - `hasCeiling()` só é lido em 5 lugares: `PlayerSpawnFinder`, `NaturalSpawner`, `MapItem` (2) e `Level.canHaveWeather`. Num espaço vazio é inofensivo.
  - O estado do clima é do servidor (`server.getWeatherData()`). Portanto **`moon.json` hoje sofre o mesmo efeito**: a chuva da Terra apaga as estrelas e escurece a luz da Lua [INF sobre o efeito visível]. Vale aplicar `has_ceiling:true` na Lua também.
- **`has_skylight:true`.** O céu 15 vem de graça. `SkyLightSectionStorage.getLightValue` devolve 15 acima da última seção e em colunas sem dados [CONF]. A nave em y=20.000+ sai plenamente iluminada pelo `sky_light_factor 1.0`, sem depender de chunks carregados.
- **Sem nuvens.** `cloud_color` com alpha 0 faz `LevelExtractor` pular `cloudHeight`, e `LevelRenderer.prepareTranslucents` só prepara nuvens com `alpha(cloudColor) > 0` [CONF].
- **Sem chuva.** Bioma sem precipitação mais `canHaveWeather()=false`.
- **`skybox:"overworld"` é necessário.** `LevelRenderer.addSkyPass` só cria o passe de céu, e só dispara `RenderLevelStageEvent.AfterSky` (onde o BodyRenderer roda), se `skybox != NONE` [CONF, LevelRenderer:381]. A Lua usa `none` e o mod sintetiza `OVERWORLD` em `AtmosphereFog.onExtractRenderState` (ramo `!model.present() && skybox==NONE`). Esse ramo exige `AtmosphereSample`, que não existe fora de planeta. Declarar `overworld` direto no JSON evita depender dele.

### 1.3 Gerador vazio

**Recomendo `minecraft:flat` com `layers:[]`** (data-only). O custo por etapa do `ChunkPyramid` [CONF], para flat void:

| Etapa | Onde roda | Trabalho |
|---|---|---|
| STRUCTURE_STARTS | worldgen | `createStructures` itera `state.possibleStructureSets()`. `FlatLevelSource.createState` usa `createForFlat` com o stream de `structure_overrides` (vazio), então é laço vazio. |
| STRUCTURE_REFERENCES | worldgen | `createReferences` faz laço fixo 17×17 de `level.getChunk` + `getAllStarts()` (vazio). |
| BIOMES | `Util.backgroundExecutor` | `fillBiomesFromNoise` com `FixedBiomeSource`. |
| TERRAIN | worldgen | `buildTerrain` do Flat sem camadas só faz `getOrCreateHeightmapUnprimed` ×2, mais `primeHeightmaps`. |
| FEATURES | worldgen | `applyBiomeDecoration` agrupa **o registro STRUCTURE inteiro por passo a cada chunk** (stream) e varre 9 chunks em busca de biomas. Sem features, mas o custo fixo existe. |
| INITIALIZE_LIGHT, LIGHT | thread de luz | 2 hops. |
| SPAWN | worldgen | `spawnOriginalMobs` vazio. |
| FULL | **main thread** | `CompletableFuture.supplyAsync(..., mainThreadExecutor)`: `new LevelChunk`, `runPostLoad`, `registerTickContainerInLevel`, `NeoForge ChunkEvent.Load`. |

Na main thread também correm `ChunkGenerationTask.create`, `DistanceManager.runAllUpdates` e `ChunkHolder.updateFutures`.

**Alternativa só se o perfil pedir:** um `SpaceChunkGenerator extends ChunkGenerator`, no molde de `PlanetChunkGenerator`, que sobrescreve como no-op `createReferences`, `applyBiomeDecoration`, `createStructures` e `spawnOriginalMobs`. Duas ressalvas:
- Se o gerador não sobrescrever `createState`, o padrão é `createForNormal` com todos os structure sets. O `PlanetChunkGenerator` não sobrescreve. Copiar o `FlatLevelSource.createState` (`ChunkGeneratorStructureState.createForFlat(..., Stream.empty())`) evita cálculo de anéis de fortaleza sobre um bioma fixo [INF sobre o custo].
- Pular `fillBiomesFromNoise` deixaria o bioma `plains`, que tem precipitação. O bioma tem que ser preenchido, de forma síncrona.

### 1.4 Eixos e viés de Y (`SpaceDimension`)

**Restrições de Y [CONF]:**
- A altura da dimensão tem teto de 4064: `Y_SIZE = 4096 - 32`, `min_y ∈ [-2032, 2031]`.
- `Entity.checkBelowWorld` mata (ou dá 4 de dano) abaixo de `minY - 64`.
- O disco escuro do céu aparece com olhos abaixo de y=63 (`ClientLevel.getHorizonHeight`).
- Entidades acima da altura de construção são normais no vanilla: `Level.getBlockState` devolve `VOID_AIR` sem tocar chunk (`isInValidBounds`). O clamp de posição de entidade é ±2e7 em Y e ±3.0000512e7 em XZ.

O espaço precisa de ±15.430 em Y (polos da Terra com altitude 5.000) e ±5.230 da inclinação lunar. Logo o mapeamento direto não cabe, e um viés é obrigatório.

```java
public final class SpaceDimension {
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space");
    public static final double Y_BIAS = 20_000.0;
    /** espaço = M · local. Eixos da dimensão = base local da Terra no spawn: leste=+Z, cima=+X, norte=+Y (SpaceFrame.localBasis(EARTH,0,0)). */
    public static final double[] LOCAL_TO_SPACE = {0,1,0,  0,0,1,  1,0,0};   // det = +1

    public static void toSpace(double x, double y, double z, double[] out)   { out[0] = y - Y_BIAS; out[1] = z; out[2] = x; }
    public static void fromSpace(double sx, double sy, double sz, double[] out) { out[0] = sz; out[1] = sx + Y_BIAS; out[2] = sy; }

    public static boolean is(Level level) {
        return level.dimensionTypeRegistration().unwrapKey().filter(k -> k.identifier().equals(TYPE)).isPresent();
    }
}
```

**Efeito dos eixos [CONF por derivação]:**
- No espaço, a posição da nave é `(x_d, y_d, z_d) = (sz, sx + 20000, sy)`.
- Com `SpaceSun.update(level, Planets.EARTH, 0, 0)` o sol local é o do vanilla no spawn (`-sin a, cos a, 0`), a inclinação é 0 e a rotação das estrelas é identidade.
- A subida vertical sobre (0,0) da Terra continua vertical no espaço.
- Só `sy` e `sz` mudam o chunk. Um voo radial ao longo de `sx` não cruza chunks [INF, vantagem de bônus].

**Armadilha:** `BlockPos.asLong()` guarda só 12 bits de Y (±2048). `SectionPos.asLong` usa 20 bits para Y, então o gerenciador de entidades funciona. Qualquer mod ou mixin que empacote `entity.blockPosition()` num long quebra acima de y≈2047 [INF; o vanilla guarda com `isInValidBounds`].

---

## 2. Desenho dos corpos de dentro do espaço

### 2.1 O que depende de "estar num planeta" (grep completo)

| Componente | Dependência | Em dimensão sem planeta | Ação |
|---|---|---|---|
| `CurvatureState.current()` e shaders (`curvature.glsl`, `TerrainTransformMixin`, `DynamicTransformMixin`, `CloudRendererMixin`, `SodiumCurvature`) | `Planets.forLevel` | `Frame.OFF`, raio 0, o shader devolve `pos` intacto [CONF] | Nada |
| `PlanetMeshRenderer` | `Planets.forLevel` | `reset()` e retorna | Nada (ver 2.5) |
| `AtmosphereLimbRenderer.onAfterSky` | `AtmosphereSample.of` | retorna | Nada (o halo vem do BodyRenderer) |
| `AtmosphereFog` (fog, cor da fog, extract) | `AtmosphereSample` | fog vanilla; `extract` chama `SpaceSun.clearView()` | Hook de espaço (2.3) |
| `BodyRenderer` | `Planets.forLevel` e `SolarSystem.bodyOf` | **retorna: nada é desenhado** | `SkyObserver` |
| `BodyClient.onExtractRenderState` | `Planets.forLevel(...).isEmpty()` | não esconde a lua vanilla | Ramo de espaço mais mixin |
| `SpaceSun.effectiveTicks` | `Planets.forLevel` | passa direto | Nada |
| `AttributeTrackSamplerMixin` | idem | passa direto | Nada |
| `DhIntegration`, `DhCurvatureClient` | `Planets.forLevel` | ignoram o nível | Ver riscos |
| `PlanetWrap`, `PlanetMapService` | idem | retornam | Nada |
| `PlanetGravity.factorOf` | `.orElse(1.0)` | **g=1 no espaço** | Corrigir (0.0) |
| `OxygenSetup:101,142`, `OxygenHud:69`, `CompressorLogic.isBreathable`, `OxygenCompressorBlockEntity:130` | `.orElse(false)` ou `orElse(true)` | **respirável no espaço** | Corrigir |
| `TravelServer.onPlayerTick` | `Planets.forLevel`; se `body==null`, `restore()` e retorna | sem Autopilot, sem boost, sem esfera de influência | Ramo de espaço |
| `Autopilot.start` | idem | "Este mundo não é um planeta" | Opcional |
| `ServerGamePacketListenerImplMixin` | idem | checagem "moved too quickly" ligada | Estender ao espaço (EVA) |

**Mapa da Terra no cliente.** `PlanetMapService.onPlayerLoggedIn` envia todos os mapas prontos, independente da dimensão do jogador [CONF]. Quem entra direto no espaço recebe o mapa da Terra. Falta confirmar que o bake já terminou nessa hora; senão a esfera cai no terreno analítico até o pacote chegar [INF].

### 2.2 Céu: o que fazer em cada peça

- **Sol e estrelas.**
  - Com `SpaceSun.update(level, EARTH, 0, 0)`, `skyAngle` e `starAngle` ficam corretos e `starRotation` é identidade.
  - O `SkyRendererMixin` e o resto do pipeline vanilla funcionam sem mudança.
- **Lua vanilla.**
  - `BodyClient` a manda para o nadir (`moonAngle = π`). Num planeta o chão cobre, mas no espaço o quad ficaria visível olhando para baixo.
  - Adicionar `@Inject(method="renderMoon", at=HEAD, cancellable=true)` em `SkyRendererMixin`. A assinatura [CONF] é `renderMoon(RenderPass, MoonPhase, float, PoseStack)`, onde `MoonPhase` é `net.minecraft.world.level.MoonPhase`.
  - `rainBrightness` é o alpha de sol e lua juntos [CONF]. Zerar esconderia o sol também, então a lua tem que ser cancelada.
- **Disco escuro e fog.**
  - Com Y ≥ 4.570 não há disco escuro.
  - A fog vanilla precisa do ramo "vácuo" de `AtmosphereFog.onRenderFog` (tudo em `Float.MAX_VALUE`) para o espaço.
- **`AtmosphereFog.onExtractRenderState` fora de planeta chama `SpaceSun.clearView()`.** O handler de espaço roda depois (`EventPriority.LOW`). `clearView` zera inclinação e rotação (que é o desejado), mas **não** zera `skyAngle` e `starAngle`, então o handler de espaço precisa atualizá-los.
- **Alternativa de controle total (opcional):** NeoForge tem `CustomSkyboxRenderer.renderSky(levelState, skyState, modelViewMatrix, skyFog)`, registrado por `RegisterCustomEnvironmentEffectRendererEvent.registerSkyboxRenderer` e selecionado pelo atributo `NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX`. O id do atributo no JSON (`neoforge:custom_skybox`) é inferido. `addSkyPass` continua exigindo `skybox != NONE`. Não é necessário para o primeiro protótipo.

### 2.3 Esboço de código

**`SkyObserver`** (novo, `client/space`), a única abstração que o BodyRenderer precisa:

```java
/** De onde se olha o céu: o chão de um planeta (câmera em x,y,z da dimensão dele) ou o espaço aberto. */
record SkyObserver(@Nullable SolarSystem.Body home, double clock, double[] pos, double[] localToSpace) {

    static Optional<SkyObserver> of(ClientLevel level, CameraRenderState cam, double clock) {
        Optional<PlanetDefinition> planet = Planets.forLevel(level);
        if (planet.isPresent()) {
            SolarSystem.Body body = SolarSystem.bodyOf(planet.get()).orElse(null);
            if (body == null) return Optional.empty();
            double[] s = new double[3];
            SpaceFrame.toSpace(body, clock, cam.pos.x, cam.pos.y, cam.pos.z, s);
            double[] basis = new double[9];                         // linhas: leste, cima, norte (fixos no corpo)
            SpaceFrame.localBasis(body, cam.pos.x, cam.pos.z, basis);
            double[] r = body.poseAt(clock).rotation();
            double[] m = new double[9];                             // espaço = R_pose · basisᵀ · local
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                double v = 0; for (int k = 0; k < 3; k++) v += r[i * 3 + k] * basis[j * 3 + k];
                m[i * 3 + j] = v;
            }
            return Optional.of(new SkyObserver(body, clock, s, m));
        }
        if (SpaceDimension.is(level)) {
            double[] s = new double[3];
            SpaceDimension.toSpace(cam.pos.x, cam.pos.y, cam.pos.z, s);
            return Optional.of(new SkyObserver(null, clock, s, SpaceDimension.LOCAL_TO_SPACE));
        }
        return Optional.empty();
    }

    void spaceToLocal(double vx, double vy, double vz, double[] out) {      // Mᵀ·v
        double[] m = localToSpace;
        out[0] = m[0]*vx + m[3]*vy + m[6]*vz;
        out[1] = m[1]*vx + m[4]*vy + m[7]*vz;
        out[2] = m[2]*vx + m[5]*vy + m[8]*vz;
    }

    BodySky.Apparent apparent(SolarSystem.Body target) {
        BodySky.Apparent a = BodySky.fromSpacePoint(target, clock, pos[0], pos[1], pos[2]);   // direção nos eixos do espaço
        double[] d = new double[3];
        spaceToLocal(a.x(), a.y(), a.z(), d);
        return new BodySky.Apparent(d[0], d[1], d[2], a.distance(), a.angularRadius());
    }

    /** Eixos fixos em target → eixos locais da câmera (= SpaceFrame.meshToLocal quando observer é um planeta). */
    void meshToLocal(SolarSystem.Body target, double[] out) {
        double[] r = target.poseAt(clock).rotation(), col = new double[3];
        for (int j = 0; j < 3; j++) {
            spaceToLocal(r[j], r[3 + j], r[6 + j], col);
            out[j] = col[0]; out[3 + j] = col[1]; out[6 + j] = col[2];
        }
    }

    void sun(float[] out) {
        double[] s = new double[3], l = new double[3];
        SunModel.directionInSpace(clock, s);
        spaceToLocal(s[0], s[1], s[2], l);
        out[0] = (float) l[0]; out[1] = (float) l[1]; out[2] = (float) l[2];
    }
}
```

`BodySky.fromSpacePoint` e `SunModel.directionInSpace` já existem [CONF]. O ajuste em `BodyRenderer.renderInternal`:

```java
double clock = SpaceSun.clock(level);
SkyObserver obs = SkyObserver.of(level, camera, clock).orElse(null);
if (obs == null) return;
...
// o planeta atual só entra de longe; no espaço obs.home() == null: todos entram
if (body == obs.home() && camY - home.planet().seaLevel() < PlanetMeshProjection.altitudeStart(home.planet().radius())) continue;
BodySky.Apparent apparent = obs.apparent(body);          // antes: BodySky.fromDimension(current, clock, camX, camY, camZ, body)
...
obs.meshToLocal(candidate.body(), meshBasis);            // antes: SpaceFrame.meshToLocal(observer, clock, camX, camZ, ...)
obs.sun(sun);                                            // antes: SpaceSun.localAt(level, planet, camX, camZ, sun)
double fade = obs.home() == null ? 1.0 : BodySky.daylightFade(...);
// prefetch(): para obs.home()==null, pedir a esfera dos DOIS corpos, com a distância real a cada um
// halo: self = (body == obs.home())  → no espaço os halos de todos os corpos
```

O resto de `draw()` (UBO, pass, `AtmosphereClient.drawHalo`) fica igual. O pipeline é o mesmo, o depth é próprio por corpo e vai do mais longe ao mais perto.

**Hooks do céu de espaço** (novo `SpaceSkyClient`, ligado junto de `AtmosphereClient.init`):

```java
static void onExtract(ExtractLevelRenderStateEvent e) {                 // EventPriority.LOW
    if (!SpaceDimension.is(e.getLevel())) { SpaceSun.inSpace = false; return; }
    SpaceSun.inSpace = true;
    SkyRenderState sky = e.getRenderState().skyRenderState;
    SpaceSun.update(e.getLevel(), Planets.EARTH, 0.0, 0.0);              // eixos da dimensão == base local do spawn
    sky.sunAngle = SpaceSun.skyAngle();
    sky.starAngle = SpaceSun.starAngle();
    sky.rainBrightness = 1.0F;  sky.starBrightness = 1.0F;
    sky.skyColor = new Vector3f();  sky.sunriseAndSunsetColor = new Vector4f();
    sky.moonAngle = (float) Math.PI;                                     // + renderMoon cancelado no mixin
}
static void onRenderFog(ViewportEvent.RenderFog e) {                     // mesmo ramo "vácuo" de AtmosphereFog
    if (e.getEnvironment() instanceof AtmosphericFogEnvironment && SpaceDimension.is(e.getCamera().entity().level())) {
        FogData f = e.getFogData();
        f.environmentalStart = f.environmentalEnd = f.renderDistanceStart = f.renderDistanceEnd = Float.MAX_VALUE;
    }
}
```

### 2.4 O que NÃO muda

- Shaders `space/body.*`, `planetmesh/*` e `atmosphere/limb.*`.
- `BodyMeshCache` e `BodyMeshGeometry`.
- `AtmosphereLimbRenderer.drawShell`: o halo da Terra vem do BodyRenderer com `visibility 1.0` e `skipDisc`.
- Toda a curvatura.

### 2.5 Qualidade na entrada (resolução da Terra a ~5.000 de altitude)

`requiredSegments = 2πR / (1,5 · pixelAngle · distância)`, com `pixelAngle ≈ 0,001297` (FOV 70, 1080p). Para a Terra (R = 10.430):

| Altitude | Distância ao centro | Segmentos pedidos | Célula real (teto 512 = 128 blocos) |
|---|---|---|---|
| 5.000 | 15.430 | ~2.180 | ~6,4 px |
| ~14.600 | 25.000 | ~1.350 | ~3,9 px |
| ~34.600 | 45.000 | ~750 | ~2,2 px |
| ~49.600 | 60.000 | ~560 | ~1,7 px |

- A malha orbital atual, adaptativa e com 50 mil vértices, é mais fina que a esfera de 512 segmentos nessa faixa.
- **Há dois saltos possíveis na troca de dimensão a 5.000:**
  - Resolução, pela tabela acima.
  - A carta (azimutal vs. lat/lon). `PlanetMeshProjection.weight` ainda vale ~0,13 a 5.000 (`smoothstep(3000, 12000)`). A malha só coincide exatamente com a esfera a partir de `ALTITUDE_FULL = 12.000`, fora do alcance do DH [CONF].
- **Mitigações em ordem:**
  1. `MAX_SEGMENTS=1024`: 525 mil vértices, 8,4 MB de vértices mais 12,6 MB de índices, célula de 64 blocos.
  2. Uma abstração `PlanetView(planet, camX/Y/Z locais, viewRotation local)` que permita ao `PlanetMeshRenderer` rodar com câmera virtual no espaço, usando `SkyObserver` e `SpaceFrame.fromSpace`. Esforço médio. Faz o corpo mais próximo usar a malha adaptativa também no espaço.
- **Tradeoff do ponto de transição:**
  - Subir a transição para ≥12.000 elimina o salto de carta.
  - Mas mantém a nave mais tempo na dimensão curva da Terra, onde o servidor gera terreno real. Esse é exatamente o cenário que travou o Autopilot.
  - Recomendo ficar em ~5.000 com a mitigação 1 já e a 2 numa segunda fase.

---

## 3. Escala e precisão

- **Limites do mundo [CONF]:** `isInWorldBoundsHorizontal` é ±30.000.000, o clamp de `Entity.setPos` é ±3.0000512e7. O espaço usa |x|, |z| ≲ 7·10⁴ e `y_d` ≤ ~9·10⁴, ou seja 0,3% do limite. Está ok.
- **Servidor e rede [CONF]:**
  - Posições são `double`.
  - `VecDeltaCodec` usa ponto fixo 1/4096 em `short`, então deltas ≥ ±8 blocos viram sync absoluto de posição.
  - Em y≈8e4, `round(x·4096)` ≈ 3,3e8 cabe com folga.
  - Uma nave a 30–75 blocos/tick vai mandar sync absoluto todo tick: ~60 B/tick, aceitável.
- **Render de entidades [INF, comportamento padrão do 26.x]:** a posição é subtraída da câmera em `double` antes de virar `float`. Entidade a 80.000 não perde precisão visível.
- **`BodyRenderer` [CONF pelo código, estimativa de ulp [INF]]:**
  - Vetores câmera→centro chegam a 4·10⁵ (teto de `SCALED_SPACE_DISTANCE`). O ulp de `float` aí é ~0,03 bloco, irrelevante frente a raios de 2.608 e 10.430.
  - Vértices da esfera chegam a 10.500, com ulp ~0,001.
  - No espaço a maior distância é ~7·10⁴, então o scaled space nem é acionado.
- **Depth:** reverse-Z em `D32_FLOAT`, near 16 e far 10⁶, depth limpo por corpo. Sem z-fighting entre corpos.
- **`limb.fsh`** já usa `cross(center, dir)` para evitar cancelamento. Sem mudança.
- **Roll da câmera (3b) [CONF]:** `Camera` suporta roll via `ViewportEvent.ComputeCameraAngles` (`setRotation(yaw, pitch, roll)`, ordem YXZ), e `cameraState.viewRotationMatrix` o inclui. Como o `BodyRenderer` usa essa matriz, ele acompanha o roll sem mudança. Euler YXZ tem gimbal em loopings, então a 3b provavelmente vai querer sobrescrever `Camera.rotation` por quaternion.

---

## 4. Chunks: dimensão vazia com entidade rápida

### 4.1 O que está confirmado

1. **`Entity.setPosRaw`** (patch do NeoForge, `Entity.java:3983`, comentário "Neo: ensure target chunk is loaded") chama `level.getChunk(x>>4, z>>4)` em toda mudança de posição de entidade já adicionada no servidor. Esse método é `getChunk(x, z, FULL)` com `loadOrGenerate=true`.
2. **`ServerChunkCache.getChunk`** na main thread adiciona ticket `UNKNOWN`, roda `runDistanceManagerUpdates` e **bloqueia** em `managedBlock(future::isDone)` até o FULL. Mesmo com `loadOrGenerate=false` (`BlockCollisions`, `EntityFluidInteraction.hasFluidAndLoaded`) ele espera chunks já em andamento.
3. **Pirâmide de geração:**
   - FULL depende de STRUCTURE_STARTS num raio de 8 (somado, ~11 chunks até o nível 44).
   - `ChunkGenerationTask.create` aloca `StaticCache2D` de `(2·raio+1)²` e chama `acquireGeneration` em cada, na **main thread**. Com raio 11 são ~529 chamadas por chunk [INF, calculado do `ChunkPyramid`; `ChunkLevel.RADIUS_AROUND_FULL_CHUNK` confirma em runtime].
4. **Tickets [CONF]:**
   - Nível = `33 - raio`.
   - `PORTAL`: timeout 300, flags 15 (persiste).
   - `ENDER_PEARL`: timeout 40, flags 14 (carrega, simula, não persiste).
   - `UNKNOWN`: timeout 1.
   - O vanilla usa `ServerPlayer.placeEnderPearlTicket` (ENDER_PEARL raio 2) e `Entity.placePortalTicket` (PORTAL raio 3).
5. **Entidade não-jogador só ticka no servidor se `inEntityTickingRange(chunk)`** (`ServerLevel.tick`:456–457). `isAlwaysTicking()` só põe a entidade na `entityTickList`, o que `ServerPlayer` já tem por `Player.isAlwaysTicking()==true`. A nave precisa de ticket com nível ≤ 31 no próprio chunk.
6. **Os tickets do jogador só andam com `ChunkMap.move(player)`**, que é chamado de handlers de pacote e de `setCamera`. `move` reprocessa por mudança de **SectionPos 3D** (inclui Y). Uma nave dirigida pelo servidor, sem pacotes de movimento do cliente, **não atualiza** tickets nem tracking sozinha [INF a partir de quais pontos chamam `move`]. O servidor precisa chamar `level.getChunkSource().move(player)` a cada tick após mover a nave.
7. **Cliente [CONF]:**
   - `ClientLevel.tickEntities` só roda entidades em `tickingEntities`.
   - Uma entidade entra em `tickingEntities` ao chegar o chunk dela (`onChunkLoaded → startTicking`) ou se `isAlwaysTicking()`.
   - Passageiros são pulados (`!entity.isPassenger()`) e só ticam via `tickNonPassenger` do veículo. Se a nave não ticka no cliente, o piloto sentado também não.
   - `LocalPlayer.tick` só checa `hasClientLoaded()`.
   - `PlayerChunkSender`: máximo 64 chunks/tick, ritmo dado pelo cliente.
8. **Evidência do mod** (`run/logs`, `ServerSampler`, durante o Autopilot sobre a Terra com terreno real):

   | Janela | Avisos | Server thread esperando em `Entity.teleportSetPosition → Entity.setPosRaw → Level.getChunk → managedBlock` |
   |---|---|---|
   | `2026-10-15-14.log.gz` (17:34–17:35) | "Can't keep up" de 2007 ms e 6400 ms | 50%, 81%, 64% das amostras ocupadas |
   | `2026-10-15-13.log.gz` (18:17–18:18) | 2009 ms e 5574 ms | 43%, 71%, 50% |
   | `2026-10-15-12.log.gz` (18:36), já com o gating | nenhum | 4%, 10%, 23% |

   O commit `2fab36f` mediu a ida à Lua passar de ~60 s para ~85 s com o gating.

### 4.2 O chunk vazio é barato? Sim em CPU de worldgen, não em custo fixo

- O Flat vazio faz zero trabalho de terreno (tabela da seção 1.3). O custo dominante da Terra (ruído vanilla, 14–23 s de travada) desaparece.
- Sobram: 9 passos do pipeline com hops entre threads, o FULL na main thread com evento do NeoForge, as ~529 aquisições de holder e o envio ao cliente.
- **Estimativa [INF, não medido]** com velocidades do plano (`g=0,08 b/tick²`, `GM ≈ 8,7·10⁶`, órbita baixa ≈ 26 b/tick, transferência à Lua ≈ 34 b/tick):

  | Velocidade | Chunks/tick | Chunks novos/tick com visão 2 (faixa de 5) | Main thread/tick se 0,1–0,3 ms por chunk |
  |---|---|---|---|
  | 300 b/s | 0,9 | ~5 | ~0,5–1,4 ms |
  | 600 b/s | 1,9 | ~9 | ~1–3 ms |
  | 1200 b/s | 3,75 | ~19 | ~2–6 ms |
  | 2400 b/s | 7,5 | ~38 | ~4–11 ms |

  Faixa realista do jogo: ≤ ~35 b/tick (≈700 b/s). Perto da Lua o referencial inercial da Terra soma ~14 b/tick do movimento orbital dela (`60000 · 2π · 7/192000`).
- O problema real é o **`getChunk` bloqueante**: se o chunk de destino ainda não estiver FULL quando `setPosRaw` roda, o servidor espera a cadeia inteira (alguns ms por chunk, dezenas de ms em atraso).

### 4.3 Recomendações, em ordem de custo/benefício

1. **Gerador flat vazio, altura 16** (seção 1). Só gerador próprio se o perfil acusar `createReferences` ou `applyBiomeDecoration`.
2. **Tickets à frente da nave, como o Autopilot** (precedente [CONF]: `GATE_STEPS = {1,2,4}`, `REQUEST_STEPS = {8,16,32,64}`). Use `ENDER_PEARL` (não persiste, expira em 40 ticks), não `PORTAL` (persiste em disco). Raio 0 basta para os chunks à frente, porque o forçado do NeoForge só exige FULL; raio 2 só no chunk atual, que precisa ser entity-ticking.

   ```java
   /** true => segurar o passo (um tick à frente ainda não está FULL). */
   static boolean warmAhead(ServerLevel level, Vec3 pos, Vec3 velPerTick) {
       boolean missing = false;
       for (int k : new int[] {1, 2, 4, 8, 16, 32}) {
           int cx = Mth.floor(pos.x + velPerTick.x * k) >> 4, cz = Mth.floor(pos.z + velPerTick.z * k) >> 4;
           if (!level.hasChunk(cx, cz)) {
               level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(cx, cz), k <= 2 ? 2 : 0);
               missing |= k <= 4;
           }
       }
       return missing;
   }
   ```

   Gating: se `missing`, congelar o passo da simulação da nave (como `Autopilot.holdForChunks`, com teto de 200 ticks e aviso no log). Com a velocidade limitada pelo motor, o gating raramente dispara.
3. **`level.getChunkSource().move(player)`** a cada tick, após mover a nave, para os tickets e o tracking do passageiro acompanharem.
4. **Cliente:** a nave deve sobrescrever `isAlwaysTicking() { return true; }` para o `ClientLevel` tickar nave e piloto sem depender do ritmo de chegada de chunks.
5. **Distâncias:** raio de visão 2–3 e simulação 2 enquanto houver jogador no espaço. Precedente: `Autopilot.adjustDistances` usa `PlayerList.setViewDistance` e `setSimulationDistance`, que são globais. Por dimensão exigiria mixin em `ChunkMap` [INF]. O `SAMPLER` mostra `tickChunk`, `NaturalSpawner` e `forEachEntityTickingChunk` como custo contínuo, que cai com raio pequeno e altura 16.
6. **Sem mobs no espaço:** spawn lists vazias no bioma. Considerar `doMobSpawning`, que é global.
7. **Medir** (4.5) antes de tocar em 8.
8. **Plano B, origem flutuante (KSP-style):**
   - A nave e o piloto ficam parados num ponto âncora da dimensão. A posição real e a velocidade são estado `double` do servidor, sincronizado por payload ou entity data.
   - O `SkyObserver` lê a posição virtual em vez de `camera.pos`.
   - Zero chunk novo e zero risco de float.
   - **Custos:** uma nave por âncora (multi-nave exige células de âncora e proxies de render), sem estações em coordenadas reais e EVA limitado ao raio carregado.
   - A API de `SpaceDimension` e `SkyObserver` já encapsula o mapeamento. Só muda quem fornece a posição.

   Recomendo M1 (coordenadas reais, como no plano) com 1–5, e cair para a origem flutuante se a medição não fechar o orçamento de MSPT.

### 4.4 Chunks na entrada e na saída

- Subida vertical não cruza chunk (só XZ importa). A reentrada sobre terreno real da Terra é exatamente o caso que travou antes. Exige look-ahead ao longo da trajetória de descida e freio por altitude (3c), com as mesmas regras de 4.3 na dimensão do planeta.
- `ClientPacketListenerMixin` já cancela a tela de carregamento e chama `notifyPlayerLoaded()` em qualquer transferência marcada [CONF]. Nada a mudar para o espaço.

### 4.5 Experimento sugerido (prototipo 3c-0)

Cenário de autoteste (`-PshOnly=voidflight`): uma entidade ou o jogador voando em linha reta no espaço vazio a 300/600/1200/2400 b/s. Variar altura 16 vs 384 e com/sem tickets à frente. Registrar MSPT e chunks/s com o `ServerSampler`. Critério: nenhum "Can't keep up" e `[ESPERANDO]` abaixo de ~5%.

---

## 5. Checklist de mudanças (mínimo para o marco "ver Terra e Lua do espaço")

Dados:
- `dimension_type/space.json`, `worldgen/biome/space.json`, entrada em `worldgen/world_preset/earth.json`.

Comum (servidor):
- Novo `space/SpaceDimension`.
- `PlanetGravity.factorOf`: `orElse(SpaceDimension... ? 0.0 : 1.0)`. O modificador é `ADD_MULTIPLIED_TOTAL` com `-1,0`, que zera gravidade e dano de queda.
- Vácuo no espaço em `OxygenSetup:101,142`, `OxygenHud:69` e `CompressorLogic.isBreathable`/`OxygenCompressorBlockEntity:130`. Sugestão de helper: `Planets.forLevel(l).map(p -> OxygenMath.isVacuum(p, y)).orElseGet(() -> SpaceDimension.is(l))`.
- `TravelServer.onPlayerTick`: ramo de espaço (saída para corpo, chamada de `FlightBoost.restore`).
- `ServerGamePacketListenerImplMixin`: desligar a checagem de movimento no espaço.

Cliente:
- `SkyObserver`, patch do `BodyRenderer`, `SpaceSkyClient`, `renderMoon` cancelado em `SkyRendererMixin`.
- Resolução da esfera (`MAX_SEGMENTS`).
- `SpaceSun.inSpace` (flag lida pelo mixin).

---

## 6. Esboço das passagens (relevante a 3c)

`SphereOfInfluence` continua para gravidade. As passagens passam a ser por altitude (função pura, testável):

```java
public final class SpacePassages {
    public static double exitAltitude(PlanetDefinition p) { return 5_000.0 * p.radius() / Planets.EARTH.radius(); } // escala como PlanetMeshProjection
    public static final double HYSTERESIS = 0.8;
    public static boolean leavingPlanet(PlanetDefinition p, double y) { return y - p.seaLevel() > exitAltitude(p); }
    /** Corpo a cuja dimensão o jogador volta, ou null. Usa a posição do corpo no relógio do instante (a Lua anda ~14 b/tick). */
    public static @Nullable SolarSystem.Body enteringBody(double clock, double sx, double sy, double sz) { ... }
}
```

- `BodyTransfer.compute` precisa de uma variante com origem ou destino no espaço aberto: sem `groundVelocity` (referencial inercial da Terra) e com conversão pelos eixos de `SpaceDimension`. `BodyTransfer.target` hoje é `Body`, então o espaço pede um record irmão ou `target` nulo.
- `TravelTransfers.findLevel` pede `findSpace(server)` por tipo de dimensão.
- `transfer(...)` e `TravelTransferPayload` funcionam sem mudança.

---

## 7. Riscos e perguntas em aberto

**Riscos:**

1. **Salto na entrada a ~5.000** (resolução e carta; seção 2.5) e **mapa da Terra** no cliente para quem entra direto no espaço (se o bake ainda não terminou).
2. **Custo fixo do chunk não medido.** Os números da seção 4.2 são ordens de grandeza. Se a medição reprovar, o plano B (origem flutuante) muda a arquitetura de 3b e 3c.
3. **`Entity.setPosRaw` é patch do NeoForge.** Pode mudar entre versões. Qualquer atualização exige reler esse ponto.
4. **Altura 16** nunca foi exercitada pelo mod. Pode haver suposição de mais de uma seção em vanilla, NeoForge, DH ou Sodium [INF].
5. **DH numa dimensão vazia:** `DhIntegration.LevelLoad` ignora níveis sem planeta, então o DH geraria LODs a partir do flat void. Provável CPU e bancos SQLite desperdiçados. Registrar um gerador de LOD vazio (`registerWorldGeneratorOverride`, mesmo padrão do `PlanetLodGenerator`) é a saída natural [INF; DH não foi testado].
6. **EVA no espaço:** o vanilla aplica atrito de ar (0,91/0,98) e movimento de voo. Sem atrito e sem gravidade, o controle fica estranho. Isso é 3b, mas decide `PlanetGravity` e movimento.
7. **`BlockPos.asLong` de entidade acima de y≈2047** em qualquer mod ou mixin de terceiros (o vanilla protege com `isInValidBounds`).
8. **Reentrada** sobre a Terra real é o ponto onde o travamento original voltaria. Exige look-ahead e controle de velocidade por altitude (3c).
9. **Dimension JSON vs. preset:** (b) marca o registro como experimental em todos os mundos. (a) deixa mundos antigos sem espaço (degrada para o Terra↔Lua direto atual).
10. **`has_skylight:true` na Lua** liga o clima hoje (achado 5 do resumo).

**Perguntas em aberto:**

1. Velocidade máxima prevista para a nave na 3b? Define se M1 serve ou se já nasce em origem flutuante.
2. Multi-nave e estações no espaço entram na 3c ou ficam para depois? Define a escolha M1 vs. M2.
3. Altitude de saída por corpo (a conta `5.000·R/R_Terra` dá 1.250 para a Lua) e a histerese de 0,8 estão ok?
4. Eixos alinhados ao spawn da Terra e `Y_BIAS = 20.000`: aceitável para o dono do plano (convenção de coordenadas visível no F3)?
5. Aplicar `has_ceiling:true` também na Lua agora (corrige o clima)?
6. Quer a segunda fase do 2.5 (câmera virtual no `PlanetMeshRenderer`) já na 3c, ou basta `MAX_SEGMENTS=1024` até validar o salto na prática?

**Arquivos do projeto relevantes:** `src/main/java/com/spacehorizons/client/space/{BodyRenderer,BodySky,SpaceSun,BodyClient,BodyMeshCache}.java`, `client/atmosphere/{AtmosphereFog,AtmosphereClient,AtmosphereLimbRenderer,AtmosphereSample}.java`, `client/planetmesh/{PlanetMeshRenderer,PlanetMeshProjection}.java`, `client/curvature/CurvatureState.java`, `mixin/{SkyRendererMixin,AttributeTrackSamplerMixin}.java`, `mixin/travel/*`, `space/{SolarSystem,SpaceFrame,SunModel,SpaceClock}.java`, `space/travel/{TravelServer,TravelTransfers,BodyTransfer,Autopilot,FlightGuide}.java`, `worldgen/{WorldgenSetup,PlanetChunkGenerator,PlanetBiomeSource,PlanetGravity,PlanetWrap}.java`, `planet/Planets.java`, `survival/oxygen/*`, `src/main/resources/data/spacehorizons/{dimension_type,worldgen}/*`.