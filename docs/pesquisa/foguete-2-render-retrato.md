# Relatório: renderizar o retrato de blocos como entidade (MC 26.3, NeoForge 26.3.0.76)

Legenda: **[C]** confirmado lendo o código (fontes do MC patched e do NeoForge extraídas no scratchpad, mais os fontes do mod). **[V]** verificado compilando ou rodando um teste fora do projeto. **[I]** inferência.

O projeto não foi tocado (`git status` limpo). O esboço de código foi compilado com `javac` 25 contra `minecraft-patched-26.3.0.76.jar`, o neoforge-universal, o FML e as classes do mod. Resultado: 0 erros.

## 0. Respostas curtas

1. **Registro e ciclo.** `EntityRenderersEvent.RegisterRenderers#registerEntityRenderer(EntityType, EntityRendererProvider)`. O ciclo tem quatro fases: extract (cria o render state), submit (nós para um coletor), prepare (monta vértices) e execute (desenha). A API é nova (`com.mojang.renderpearl.api`, `SubmitNodeCollector`, `FeatureRenderer`). [C]
2. **Mais eficiente para ~1.000 blocos.**
   - Nenhum dos quatro caminhos vanilla serve: FallingBlock, minecart, BlockDisplay e pistão são modo imediato.
   - O melhor é assar uma vez e reproduzir (Tier 1) ou subir um VBO estático (Tier 2).
   - Os dois dão "malha uma vez, só matriz por frame". No Tier 1 a matriz é aplicada vértice a vértice na CPU, o que é barato. No Tier 2 a matriz vai para a GPU. [C]
3. **Culling.** Precisa de um `BlockAndTintGetter` falso e de `new ModelBlockRenderer(ao, true, blockColors)`. O `MovingBlockFeatureRenderer` do vanilla usa `cull=false`, então ele não serve de modelo. [C]
4. **Luz.** `state.lightCoords` já vem da sonda em `entity.getLightProbePosition`. Para a luz dentro do retrato, o fake level sobrescreve `getBrightness(LightLayer, BlockPos)`. O lightmap global cuida do dia e da noite sozinho. [C]
5. **Curvatura.** Qualquer coisa que passe por `core/entity`, `core/block` ou `core/item` é curvada vértice a vértice, inclusive o foguete (Tier 1). Em altitude alta o foguete "incha" em xz por (R+h)/R. Pipelines próprios (Tier 2) não são curvados e precisam de colocação rígida na CPU (código abaixo). [C][V]

**Recomendação.**
- Protótipos 3a-1 e 3a-2 (parado e subida vertical): Tier 1.
- 3b (roll e altitude de até 5.000): decidir por prints, com Tier 2 mais `RocketPlacement` como destino.
- Vidro (translúcido): sempre Tier 1.

## 1. Registro e ciclo do EntityRenderer [C]

```java
modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
    e -> e.registerEntityRenderer(ModEntities.ROCKET.get(), RocketRenderer::new));
```

- O evento é do barramento do mod e só dispara no cliente. Ele chama `EntityRenderers.register(type, provider)`.
- `EntityRendererProvider<T extends Entity>.create(Context)` devolve `EntityRenderer<T, ?>`.
- `Context` expõe `getEntityRenderDispatcher()`, `getBlockModelResolver()`, `getItemModelResolver()`, `getResourceManager()`, `getModelSet()`, `getFont()`, `getSprites()`, `getAtlas(Identifier)` e `bakeLayer(ModelLayerLocation)`.
- `Context` não expõe o `BlockEntityRenderDispatcher`. Use `Minecraft.getInstance().getBlockEntityRenderDispatcher()`.
- `EntityRenderDispatcher.onResourceManagerReload` recria todos os renderers a cada recarga de recursos. O construtor roda de novo e o atlas pode ter mudado.
- `EntityRenderers.validateRegistrations()` exige renderer para todo `EntityType`. Use `NoopRenderer` onde não houver.

Ciclo por frame, tudo no render thread:

| Fase | O que acontece |
|---|---|
| extract | `LevelExtractor.extractVisibleEntities` percorre `level.entitiesForRendering()`. Cada entidade passa por `isEntityVisible` e então por `EntityRenderer.createRenderState(entity, partial)`. |
| submit | `LevelRenderer.submitEntities` chama `EntityRenderDispatcher.submit(state, camera, state.x - camX, state.y - camY, state.z - camZ, poseStack, collector)`. Ele translada em double para (entidade - câmera + `getRenderOffset(state)`) e chama `renderer.submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`. |
| prepare | `FeatureRenderDispatcher.prepareFrame` chama `FeatureRenderer.prepareGroup` de cada tipo. Os vértices vão para um `StagedVertexBuffer` compartilhado, com um `upload()` por frame. |
| execute | Passe "main": terreno sólido, evento `RenderLevelStageEvent.AfterOpaqueBlocks`, `featureFrame.executeSolid(renderPass)`, evento `AfterOpaqueFeatures`, e depois o translúcido (clássico ou OIT). |

Detalhes que importam:

- `createRenderState(T, float)` é `public final`. Ele faz `createRenderState()` (abstrato, objeto novo a cada frame), `extractRenderState(T, S, float)`, `finalizeRenderState` (sombra) e o hook `RenderStateExtensions.onUpdateEntityRenderState`.
- O render state deve guardar só referências imutáveis e valores interpolados.
- Em `submit`, o PoseStack nunca vê coordenadas absolutas. Isso é bom para planetas de raio ~10.000.
- O foguete que carrega o jogador é extraído mesmo sem passar no frustum, por causa de `hasIndirectPassenger`. Ele ainda passa pelo teste de seção abaixo.

**Visibilidade (relevante para 3c).** `LevelExtractor.isEntityVisible` exige, além do frustum: `level.isOutsideBuildHeight(blockPos.getY()) || LevelRenderer.isSectionCompiledAndVisible(blockPos, fade)`.

- `isSectionCompiledAndVisible` devolve `false` se a seção estiver `UNCOMPILED`.
- `earth.json` e `moon.json` têm `min_y=-64` e `height=384`. Acima de y=319 o foguete é sempre visível, então os voos até 5.000 não sofrem com isso.
- Dentro da faixa de altura de uma dimensão sem chunks carregados, a entidade some. O espaço precisa de chunks vazios carregados ao redor do foguete, ou de um mixin em `isEntityVisible`.

## 2. Renderizar BlockStates arbitrários [C]

### Os quatro caminhos do vanilla

- **FallingBlock e pistão.** Usam `collector.submitMovingBlock(poseStack, MovingBlockRenderState, outlineColor)`.
  - `MovingBlockRenderState` é um `BlockAndTintGetter` de um bloco só: `getBlockState(pos)` devolve o bloco apenas em `blockPos`.
  - Cada submit vira um `MovingBlockFeatureRenderer.Submit`. Em `buildGroup`, a cada frame, ele cria `new ModelBlockRenderer(ao, false, blockColors)` e chama `tesselateBlock` por bloco.
  - Resultado: re-tesselação com AO a cada frame, `cull=false` e sem vizinhos.
  - Os RenderTypes são `RenderTypes.solidMovingBlock()`, `cutoutMovingBlock()` e `translucentMovingBlock()`, sobre `RenderPipelines.SOLID_BLOCK`, `CUTOUT_BLOCK` e `TRANSLUCENT_BLOCK`. Isso é o shader `core/block`, formato `DefaultVertexFormat.BLOCK`, com `Position`, `Color`, `UV0` e `UV2`, 28 bytes por vértice.
  - A luz vem de `lightEngine` e `blockPos` reais. O pistão passa `level.getLightEngine()`.
- **Minecart e BlockDisplay.** Usam `BlockModelResolver.update(BlockModelRenderState, BlockState, BlockDisplayContext)` no extract, que resolve as partes uma vez. Depois `BlockModelRenderState.submit(poseStack, collector, light, overlay, outline)` chama `submitBlockModel(...)`.
  - `BlockModelFeatureRenderer.putPartQuads` percorre todas as `Direction` sem culling.
  - O bloco tem luz única, cor plana (só o tint) e sem AO.
  - Há 1 submit por bloco e a pipeline é a de item (`core/item`).
- **Modelo imediato.** `RenderTypeFeatureRenderer` e `StagedVertexBuffer` regeneram os vértices todo frame.
  - `PreparedRenderType.draw` só liga o `DynamicTransforms` do `RenderType.prepare()`, que escreve `RenderSystem.getModelViewMatrixCopy()` (só a rotação da câmera).
  - A matriz de cada entidade é aplicada vértice a vértice na CPU, pelo PoseStack.
- **Modelo retido.** Só o terreno é retido (`SectionMesh`, `SectionCompiler`). `SectionCompiler` é o melhor modelo de código: classe pública, construtor `(ao, cutoutLeaves, BlockStateModelSet, FluidStateModelSet, BlockColors)`. Usa `ModelBlockRenderer(ao, true, colors)`, `BlockModelLighter.enableCaching()` e `clearCache()` (cache `ThreadLocal`), `BufferBuilder` e `putBlockBakedQuad(x, y, z, quad, instance)`.

### Comparação para ~1.000 blocos

| Caminho | Custo por frame | AO, culling e luz | Veredito |
|---|---|---|---|
| `submitMovingBlock` | 1.000 tesselações com AO | `cull=false`, luz do mundo real | não |
| `BlockModelRenderState` | 1.000 submits e ~6.000 quads | luz única, sem AO, sem culling | só protótipo |
| **Tier 1:** assar uma vez e `submitCustomGeometry` | 1 submit por camada, reproduz ~1.500 a 4.000 quads com culling, ~0,1 a 0,4 ms [I] | tudo assado | recomendado |
| **Tier 2:** VBO estático com `FeatureRenderer` próprio | ~0 CPU de vértice, 1 draw por camada | tudo assado, luz via `ColorModulator` | destino para 3b/3c |

### Tier 1 em detalhe

- `PoseStack.Pose` é copiado no `submitCustomGeometry`. O lambda `CustomGeometryRenderer.render(PoseStack.Pose, VertexConsumer)` roda no `CustomFeatureRenderer.buildGroup`, no prepare do mesmo frame.
- O submit é `BatchableSubmit` com `batchKey = renderType`. Todos os foguetes e geometrias custom do mesmo RenderType caem no mesmo draw.
- O vidro usa `translucentMovingBlock()`, que tem `sortOnUpload` e OIT. A ordenação e o OIT já vêm prontos do vanilla.
- No `BufferBuilder`, o `addVertex` de 11 argumentos tem caminho rápido para o formato `BLOCK` quando o formato é a instância `DefaultVertexFormat.BLOCK` (comparação por identidade). Overlay e normal são ignorados nesse caminho.

### Tier 2 em detalhe

- NeoForge expõe `RegisterFeatureRenderersEvent`, que é postado em `NeoForge.EVENT_BUS` pelo construtor do `FeatureRenderDispatcher`.
- O `FeatureRendererType` vem de `FeatureRendererType.create(name)`.
- `SubmitNode` é a interface do nó.
- O envio do nó é `collector.submitSpecial(RenderPhaseKeys.SOLID, node)`. O `SubmitNodeStorage` implementa isso.
- API de GPU confirmada:
  - `RenderSystem.getDevice().createBuffer(Supplier<String>, usage, ByteBuffer)`, com `GpuBuffer.USAGE_VERTEX`.
  - `RenderPass.setPipeline`, `setUniform(String, GpuBufferSlice)`, `setUniform(String, GpuTextureView, GpuSampler)`, `setVertexBuffer`, `setIndexBuffer` e `drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)`.
  - `RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)`, com `requestIndexCount(n)` antes do passe, `getBuffer()` e `type()`.
  - `RenderSystem.getDynamicUniforms().writeTransform(Matrix4f modelView, Vector4f colorModulator, Vector3f modelOffset, Matrix4f textureMatrix)`.
  - `RenderSystem.getCompiledPipelineNullable(RenderPipeline)` e `bindDefaultUniforms(RenderPass)`.
- `FeatureFrameContext(options, font, blockStateModelSet, blockColors, textureManager, atlasManager, lightmap, stagedVertexBuffer)`.
- A convenção de `groupIndex` em `executeGroup` é a mesma ordem de `prepareGroup`. É assim que o `RenderTypeFeatureRenderer` faz.
- O `UBO` de `DynamicTransforms` tem 176 bytes por causa do mixin do mod (`CurvatureState.TRANSFORM_UBO_SIZE`).
- Alternativa sem `FeatureRenderer`: desenhar direto no `RenderLevelStageEvent.AfterOpaqueFeatures`, que entrega um `RenderPass` (`getRenderPass()`). Buffers precisam crescer no `PrepareRenderBuffersEvent`.
- `UberGpuBuffer` e `TlsfAllocator` existem, mas são exagero para esta escala.

## 3. Faces escondidas (culling) [C]

- `ModelBlockRenderer(boolean ambientOcclusion, boolean cull, BlockColors)`. Com `cull=true`, `shouldRenderFace` chama `Block.shouldRenderFace(level, pos, state, neighborState, direction)`.
- Essa função lê `neighborState.getFaceOcclusionShape`, `state.skipRendering` e `hidesNeighborFace`, sem tocar no resto do level.
- Um fake level precisa implementar só:
  - `getBlockState(BlockPos)`, `getFluidState(BlockPos)` e `getBlockEntity(BlockPos)`.
  - `getHeight()` e `getMinY()`.
  - `getLightEngine()` e `cardinalLighting()`.
  - `getBlockTint(BlockPos, ColorResolver)`.
  - `getModelData` já tem default (`IBlockGetterExtension`).
- O NeoForge `EnhancedBlockModelLighter` usa só `getBlockState`, `cardinalLighting()` e `this.cache.getLightCoords`. O `Cache` chama `BrightnessGetter.DEFAULT`, que lê `level.getBrightness(LightLayer, pos)`.

Fora da caixa do retrato o fake level devolve ar. As faces externas aparecem e as coladas somem.

Use um array denso em vez de `Map<BlockPos, BlockState>`: o AO consulta vizinhos centenas de vezes por bloco.

## 4. Luz [C]

**Luz do mundo na posição do foguete:**

- `EntityRenderer.getPackedLightCoords(entity, partial)` é `public final`. Ele usa `BlockPos.containing(entity.getLightProbePosition(partial))` com `getBlockLightLevel` e `getSkyLightLevel` (protected, sobrescrevíveis).
- O resultado vai para `state.lightCoords`, por padrão em `extractRenderState`.
- O `getLightProbePosition` padrão é a posição dos olhos. No `RocketEntityBase` o esboço sobrescreve para o centro.
- Funções cruas:
  - `level.getBrightness(LightLayer.SKY, pos)` e `level.getBrightness(LightLayer.BLOCK, pos)`.
  - `LightCoordsUtil.getLightCoords(level, pos)`, que trata `emissiveRendering` e emissão própria.
  - `LightCoordsUtil.pack(block, sky)` = `block<<4 | sky<<20`.

**No vértice.** O `UV2` é `ivec2`. Em empacotamento normal, `u = bloco*16` e `v = céu*16`. O vsh faz `sample_lightmap(Sampler2, UV2)`, que lê `texture(lightMap, clamp(uv/256 + 0.5/16, ...))`.

**O lightmap é global.** A textura 16×16 é refeita a cada tick por `Lightmap.render(LightmapRenderState)`, a partir dos atributos de ambiente (`SKY_LIGHT_FACTOR`, `SKY_LIGHT_COLOR`, `BLOCK_LIGHT_TINT`, `AMBIENT_LIGHT_COLOR`, gama e visão noturna). Assar níveis de luz no vértice já dá dia, noite e gama de graça. O `AttributeTrackSamplerMixin` do mod faz o relógio efetivo seguir o `SunModel` no ponto do visualizador.

**Luz interna do retrato.** O fake level sobrescreve `getBrightness(LightLayer, BlockPos)`, que é `default` em `BlockAndLightGetter`. O esboço usa um BFS de céu e de bloco por célula, em ~1.000 células, e devolve céu 15 fora da caixa. O interior fechado escurece sozinho.

**Reaplicar a luz do mundo por frame (Tier 1).**
- Bloco: `max(assado, mundo)`.
- Céu: `assado * céuDoMundo / 15`.
- O vértice guarda os dois como shorts em escala 0..240.

**Espaço vazio [C].**
- `SkyLightSectionStorage.getLightValue` devolve 15 quando não há dados na coluna ou acima da última seção. Sem chunks carregados, `getBrightness(SKY)` dá 15 e a de bloco dá 0.
- Se a dimensão do espaço tiver `has_skylight=false`, o listener é `DummyLightLayerEventListener` e devolve 0. O foguete ficaria preto. Ou a dimensão liga `has_skylight`, ou o renderer ignora a sonda e usa luz fixa ou `SpaceSun`.

**Sombreamento direcional [C].** O `CardinalLighting` (baixo 0,5, cima 1,0, N/S 0,8, L/O 0,6) é assado nas cores do vértice, em eixos locais do retrato. Quando o foguete inclina, as faces "de cima" continuam claras. Aceitável na v1.

Refinamento [I]: assar com `cardinalLighting()` todo 1,0 e, no replay, aplicar por quad o blend `Σ n_a² · brilho(face de sinal n_a)` com a normal rotada pelo pose.

## 5. Curvatura [C][V]

**Mecanismo do mod:**

- `CurvatureShaders.WORLD_VERTEX_SHADERS` inclui `core/entity`, `core/item`, `core/block`, `core/particle`, `core/text` e outros.
- `patchWorldVertex` reescreve `ModelViewMat * vec4(Position|pos, 1.0)` para `ModelViewMat * vec4(spaceHorizonsWorldCurve(...), 1.0)`.
- `spaceHorizonsWorldCurve` faz `mix(pos, spaceHorizonsCurve(pos), smoothstep(8.0, 48.0, length(pos)))`.
- O `DynamicTransformMixin` acrescenta raio e altitude da câmera a todo `DynamicGpuData.Transform.write`. O `DynamicGpuDataMixin` aumenta o UBO.
- Vale para toda `writeTransform`, vanilla ou nossa.

**Consequências para o foguete:**

- **Tier 1** (`core/block` via RenderTypes `*MovingBlock`): curvatura automática, sem código extra.
  - O erro de curvar só os vértices é desprezível: o quad mede 1 bloco e a sagita é ~1e-4.
  - O foguete acompanha a esfera, inclusive a inclinação do "para cima" com a distância (θ = dist/R).
- **No espaço sem planeta**, `CurvatureState.current()` devolve `Frame.OFF` (raio 0) e o shader devolve `pos` intacto.
- **Altitude.** O mapa leva (x, z) de sea level e a altura h entra em `(R+h)·sin(θ)/dist`, então tudo na altura da câmera é esticado em xz por (R+h)/R.
  - Terra (R=10430): 1,029 a 300, 1,144 a 1.500, 1,479 a 5.000.
  - Lua (R=2608): 1,38 a 1.000 e 2,0 a 2.608.
  - A transição vai de 0 (menos de 8 blocos da câmera) a 100% (mais de 48). Quem pilota não vê distorção perto, mas a ponta distante do foguete incha.
  - É consistente com o resto do mundo (nuvens, outros jogadores), mas feio para um corpo rígido grande visto de fora.
- **Tier 2** (shader próprio em `spacehorizons:rocket/rocket_block`) não é remendado. Precisa de colocação rígida na CPU (`RocketPlacement`): curva só o centro com `CurvatureMath.curve` e gira o corpo de θ em torno de k = (dz, 0, -dx). Derivação: d(posição)/d(altura) = (dx·sinθ, cosθ, dz·sinθ), que é o "para cima" girado de θ.

**Verificação numérica [V].** Com `CurvatureMath` real e um foguete 4×20×4:

| Centro e altitude | Erro rígido vs por-vértice | Fator (R+h)/R |
|---|---|---|
| (300, 5, 400), alt 75 | 0,023 blocos | 1,007 |
| (-1200, 30, 50), alt 100 | 0,027 | 1,010 |
| (40, 0, 0), alt 70 | 0,022 | 1,007 |
| (0, 20, -2000), alt 90 | 0,020 | 1,009 |
| (900, 2930, 900), alt 3.000 | 0,816 | 1,288 |

Abaixo de ~100 de altitude as duas abordagens coincidem. A 3.000, o rígido difere ~0,8 bloco do por-vértice, que estica.

**Frustum.** O `Frustum` de `EntityRenderer.shouldRender` é plano. O foguete que ultrapassa a esfera pode ser cortado cedo ou tarde. O esboço usa uma esfera que cobre o retrato em qualquer pose (`getBoundingBoxForCulling`).

## 6. Esboço de código (compila contra o 26.3)

Arquivos completos, fora do projeto:
`C:\Users\nicol\AppData\Local\Temp\claude\E--git-Personal-Games-MineMods-SpaceHorizons\a4e627e3-157c-4438-91cf-69e4d19a2311\scratchpad\sketch\src\com\spacehorizons\client\rocket\*.java` (13 arquivos) e `...\sketch\res\rocket\rocket_block.vsh`. O teste numérico está em `...\scratchpad\check\TiltCheck.java`. As fontes do MC e do NeoForge extraídas ficam em `...\scratchpad\mc\` e `...\scratchpad\nf\`.

### 6.1 RocketRenderer (Tier 1)

```java
public class RocketRenderer extends EntityRenderer<RocketEntityBase, RocketRenderState> {
    private static final RocketMeshCache MESHES = new RocketMeshCache();

    public RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        MESHES.clear();            // renderers recriados a cada recarga de recursos: UVs do atlas antigo morreram
        this.shadowRadius = 0.0F;
    }

    @Override public RocketRenderState createRenderState() { return new RocketRenderState(); }

    @Override
    public void extractRenderState(RocketEntityBase rocket, RocketRenderState state, float partialTick) {
        super.extractRenderState(rocket, state, partialTick);   // x,y,z interpolados, lightCoords, nomes
        RocketPortrait portrait = rocket.portrait();
        state.mesh = MESHES.get(portrait);                      // null enquanto assa em worker
        rocket.orientation(partialTick, state.orientation);     // slerp prev -> atual
        state.pivot.set(rocket.pivotLocal());

        state.blockEntities.clear();
        if (rocket.level() instanceof ClientLevel level && state.mesh != null) {
            var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            Vec3 cam = this.entityRenderDispatcher.camera != null ? this.entityRenderDispatcher.camera.position() : Vec3.ZERO;
            for (RocketPortrait.LoadedBlockEntity loaded : portrait.loadBlockEntities(level.registryAccess())) {
                // tryExtractRenderState recusa BE sem level; o renderer em si aceita (ChestRenderer tem o ramo "sem level")
                BlockEntityRenderer<BlockEntity, BlockEntityRenderState> r = dispatcher.getRenderer(loaded.blockEntity());
                if (r != null) {
                    BlockEntityRenderState s = r.createRenderState();
                    r.extractRenderState(loaded.blockEntity(), s, partialTick, cam, null);
                    s.lightCoords = state.lightCoords;
                    state.blockEntities.add(new RocketRenderState.BlockEntityDraw(s, loaded.localPos()));
                }
            }
        }
    }

    @Override
    public void submit(RocketRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.mesh != null) {
            poseStack.pushPose();                               // poseStack = (entidade - câmera) em double -> float
            poseStack.rotate(state.orientation);
            poseStack.translate(-state.pivot.x, -state.pivot.y, -state.pivot.z);
            for (PortraitMesh.Layer layer : state.mesh.layers()) {
                collector.submitCustomGeometry(poseStack, layer.renderType(),
                        (pose, buffer) -> layer.replay(pose, buffer, state.lightCoords));
            }
            var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            for (var draw : state.blockEntities) {
                poseStack.pushPose();
                poseStack.translate(draw.localPos().getX(), draw.localPos().getY(), draw.localPos().getZ());
                dispatcher.submit(draw.state(), poseStack, collector, camera);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);       // nome, corda
    }

    /** A hitbox é alinhada aos eixos e não gira: culling por esfera que cobre o retrato em qualquer pose. */
    @Override
    protected AABB getBoundingBoxForCulling(RocketEntityBase rocket, float partialTick) {
        RocketPortrait p = rocket.portrait();
        double r = 0.5 * Math.sqrt((double) p.sizeX * p.sizeX + (double) p.sizeY * p.sizeY + (double) p.sizeZ * p.sizeZ) + 1.0;
        Vec3 c = rocket.getPosition(partialTick);
        return new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }
}
```

`RocketEntityBase` expõe `portrait()`, `orientation(float, Quaternionf)`, `pivotLocal()` e sobrescreve `getLightProbePosition(float)` para `getPosition(partial)`.

### 6.2 Cache assíncrono

```java
@Nullable PortraitMesh get(RocketPortrait portrait) {
    CompletableFuture<PortraitMesh> f = meshes.get(portrait.id());
    if (f == null) {
        Minecraft mc = Minecraft.getInstance();
        boolean ao = mc.options.ambientOcclusion().get();       // lido aqui; o worker só recebe valores
        boolean cutoutLeaves = mc.options.cutoutLeaves().get();
        BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
        BlockColors colors = mc.getBlockColors();
        f = CompletableFuture.supplyAsync(() -> PortraitMesh.bake(portrait, ao, cutoutLeaves, models, colors), Util.backgroundExecutor());
        meshes.put(portrait.id(), f);
    }
    return f.isDone() && !f.isCompletedExceptionally() ? f.join() : null;
}
```

Limpar o cache só ao sair do mundo (`ClientPlayerNetworkEvent.LoggingOut`) e no construtor do renderer. A troca de dimensão não limpa: não há re-assar na travessia.

### 6.3 Fake level (trechos principais)

```java
final class PortraitLevel implements BlockAndTintGetter {
    // sky[]/block[]: BFS sobre a caixa do retrato; céu entra pelas células de borda não opacas,
    // cai sem perder força e perde 1 por passo lateral; luz de bloco semeia getLightEmission(this, pos)
    @Override public BlockState getBlockState(BlockPos p) { return portrait.get(p); }
    @Override public FluidState getFluidState(BlockPos p) { return portrait.get(p).getFluidState(); }
    @Override public @Nullable BlockEntity getBlockEntity(BlockPos p) { return null; }
    @Override public int getBrightness(LightLayer layer, BlockPos pos) {   // default de BlockAndLightGetter
        if (!portrait.inside(pos.getX(), pos.getY(), pos.getZ())) return layer == LightLayer.SKY ? 15 : 0;
        int i = index(pos.getX(), pos.getY(), pos.getZ());
        return layer == LightLayer.SKY ? sky[i] : block[i];
    }
    @Override public LevelLightEngine getLightEngine() { return LevelLightEngine.EMPTY; }
    @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }
    @Override public int getBlockTint(BlockPos p, ColorResolver c) { return -1; }
    @Override public int getHeight() { return portrait.sizeY; }
    @Override public int getMinY() { return 0; }
}
```

### 6.4 Assar e reproduzir (Tier 1)

```java
public static PortraitMesh bake(RocketPortrait portrait, boolean ao, boolean cutoutLeaves, BlockStateModelSet models, BlockColors colors) {
    PortraitLevel level = new PortraitLevel(portrait);
    ModelBlockRenderer renderer = new ModelBlockRenderer(ao, true, colors);   // cull = true (o MovingBlockFeatureRenderer usa false)
    Map<ChunkSectionLayer, Builder> builders = new EnumMap<>(ChunkSectionLayer.class);
    BlockModelLighter.enableCaching();                                        // cache ThreadLocal, como SectionCompiler.compile
    try {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < portrait.sizeY; y++) for (int z = 0; z < portrait.sizeZ; z++) for (int x = 0; x < portrait.sizeX; x++) {
            BlockState state = portrait.get(x, y, z);
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
            pos.set(x, y, z);
            boolean opaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, state);
            BlockQuadOutput out = (qx, qy, qz, quad, inst) -> builders
                .computeIfAbsent(opaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer(), l -> new Builder())
                .add(qx, qy, qz, quad, inst);
            renderer.tesselateBlock(out, x, y, z, level, pos, state, models.get(state), state.getSeed(pos));
        }
    } finally { BlockModelLighter.clearCache(); }
    // ... Layer por ChunkSectionLayer, com RenderTypes.solidMovingBlock / cutoutMovingBlock / translucentMovingBlock
}

// Builder.add == VertexConsumer.putBlockBakedQuad gravando em arrays (4 vértices por quad):
//   pos = quad.position(v) + (x,y,z);  uv = UVPair.unpackU/V(quad.packedUV(v))
//   cor = ARGB.multiply(inst.getColor(v), quad.bakedColors().color(v))
//   luz = inst.getLightCoordsWithEmission(v, quad.materialInfo().lightEmission())

public void replay(PoseStack.Pose pose, VertexConsumer out, int worldLight) {
    Vector3f t = new Vector3f();
    int worldSky = LightCoordsUtil.sky(worldLight), worldBlock = worldLight & 0xFFFF;
    for (int i = 0; i < color.length; i++) {
        pose.pose().transformPosition(pos[3*i], pos[3*i+1], pos[3*i+2], t);
        int baked = light[i];
        int lightOut = Math.max(baked & 0xFFFF, worldBlock) | ((baked >>> 16) * worldSky / 15 << 16);
        out.addVertex(t.x, t.y, t.z, color[i], uv[2*i], uv[2*i+1], OverlayTexture.NO_OVERLAY, lightOut, 0f, 1f, 0f);
    }
}
```

### 6.5 Tier 2: VBO estático e FeatureRenderer próprio

**Upload (render thread).** Mesmo padrão do `AutoStorageIndexBuffer.ensureStorage`:

```java
ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
try {
    data.put(bytes).flip();
    GpuBuffer buf = RenderSystem.getDevice().createBuffer(() -> "SpaceHorizons rocket", GpuBuffer.USAGE_VERTEX, data);
} finally { MemoryUtil.memFree(data); }
```

Assar com `new BufferBuilder(new ByteBufferBuilder(1<<16), PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK)` e `putBlockBakedQuad`. Só SOLID e CUTOUT; o translúcido fica no Tier 1.

**FeatureRenderer:**

```java
public final class RocketFeatureRenderer implements FeatureRenderer<RocketFeatureRenderer.Submit> {
    public static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("SpaceHorizons Rocket");
    public record Submit(RocketGpuMesh mesh, Matrix4f model, Vector4f colorModulator) implements SubmitNode {
        public FeatureRendererType<Submit> featureType() { return TYPE; }
    }
    private record Prepared(Submit submit, GpuBufferSlice transforms) {}
    private final List<List<Prepared>> groups = new ArrayList<>();

    public static void register(RegisterFeatureRenderersEvent e) { e.register(TYPE, new RocketFeatureRenderer()); }

    @Override public void prepareGroup(FeatureFrameContext ctx, List<Submit> submits, boolean strictlyOrdered) {
        Matrix4f view = RenderSystem.getModelViewMatrixCopy();           // só a rotação da câmera, como RenderType.prepare
        List<Prepared> prepared = new ArrayList<>();
        for (Submit s : submits) {
            // ModelViewMat = view; TextureMat := matriz do modelo (nenhum shader de bloco usa); ColorModulator := escurecimento
            GpuBufferSlice slice = RenderSystem.getDynamicUniforms().writeTransform(view, s.colorModulator(), new Vector3f(), s.model());
            prepared.add(new Prepared(s, slice));
            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(s.mesh().indexCount());
        }
        groups.add(prepared);
    }

    @Override public void executeGroup(FeatureFrameContext ctx, @Nullable OitStage stage, RenderPass pass, int groupIndex,
            List<Submit> submits, boolean strictlyOrdered) {
        if (stage != null) return;
        var cache = RenderSystem.getSamplerCache();
        GpuSampler atlas = cache.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true);
        GpuSampler light = cache.getClampToEdge(FilterMode.LINEAR);
        var idx = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        for (Prepared p : groups.get(groupIndex)) {
            CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(p.submit().mesh().pipeline());
            if (compiled == null) continue;
            pass.setPipeline(compiled);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", p.transforms());
            pass.setUniform("Sampler0", ctx.textureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView(), atlas);
            pass.setUniform("Sampler2", ctx.lightmap(), light);
            pass.setVertexBuffer(0, p.submit().mesh().vertexBuffer().slice());
            pass.setIndexBuffer(idx.getBuffer(), idx.type());
            pass.drawIndexed(p.submit().mesh().indexCount(), 1, 0, 0, 0);
        }
    }
    @Override public void finishExecute(FeatureFrameContext ctx) { groups.clear(); }
}
```

`TextureAtlas.LOCATION_BLOCKS` é `@Deprecated`, mas o próprio vanilla ainda o usa em `LevelRenderer.executeSolid` e `RenderTypes`.

**Pipeline.** O `RenderPipeline.builder(snippet)` copia os shaders do snippet para um `EnumMap`. O `withVertexShader` posterior sobrescreve só o vsh. [C]

```java
static final RenderPipeline SOLID = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
    .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/rocket_solid"))
    .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "rocket/rocket_block"))
    .withColorTargetState(ColorTargetState.DEFAULT).build();
static final RenderPipeline CUTOUT = /* igual + .withShaderDefine("ALPHA_CUTOUT", 0.5F) */;
// registrar com modEventBus.addListener(e -> { e.registerOptionalPipeline(SOLID); e.registerOptionalPipeline(CUTOUT); })
```

**Shader** `assets/spacehorizons/shaders/rocket/rocket_block.vsh`. É o `core/block.vsh` do vanilla com uma linha trocada. O fsh é o `core/block.fsh` do vanilla.

```glsl
#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
layout(location = 0) in vec3 Position;  layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;       layout(location = 3) in ivec2 UV2;
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance; layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;              layout(location = 3) out vec2 texCoord0;
void main() {
    vec3 pos = (TextureMat * vec4(Position, 1.0)).xyz;      // modelo: foguete -> mundo relativo à câmera
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);  // sem curvatura: colocação rígida na CPU
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
```

**Colocação rígida (obrigatória no Tier 2):**

```java
static void applyCurvedPlacement(PoseStack poseStack) {
    CurvatureState.Frame f = CurvatureState.current();
    if (f.radius() <= 0.0) return;                           // espaço: sem curvatura
    double cx = poseStack.last().pose().m30(), cy = poseStack.last().pose().m31(), cz = poseStack.last().pose().m32();
    double[] c = new double[3];
    CurvatureMath.curve(f.radius(), f.cameraAltitude(), cx, cy, cz, c);
    poseStack.translate(c[0] - cx, c[1] - cy, c[2] - cz);
    double dist = Math.hypot(cx, cz);
    if (dist > 1.0e-3) poseStack.rotate(new Quaternionf().rotationAxis((float) (dist / f.radius()), (float) (cz / dist), 0f, (float) (-cx / dist)));
}
```

Chamar depois do translate do dispatcher e antes da orientação do foguete. Fora disso, `submit` do Tier 2 vira: `pushPose; applyCurvedPlacement; rotate(orientation); translate(-pivot); collector.submitSpecial(RenderPhaseKeys.SOLID, new Submit(mesh, new Matrix4f(poseStack.last().pose()), new Vector4f(k,k,k,1))); popPose`, com `k = sky/15` da luz do mundo [I].

**Ligação no cliente:**

```java
modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> e.registerEntityRenderer(ROCKET.get(), RocketRenderer::new));
modEventBus.addListener(RocketPipelines::register);                          // Tier 2, IModBusEvent
NeoForge.EVENT_BUS.addListener(RocketFeatureRenderer::register);             // Tier 2, RegisterFeatureRenderersEvent
NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> RocketRenderer.onLoggingOut());
```

## 7. Riscos e perguntas em aberto

**Riscos**
1. **Dimensão do espaço (3c).** O foguete some se a seção não estiver compilada. Chunks vazios carregados resolvem esta questão e a do tick no servidor. Se `has_skylight=false`, a luz fica 0 (foguete preto). [C]
2. **Sombreamento direcional assado.** Em rotação, as faces "de cima" ficam claras no eixo errado. Correção no replay: blend por normal rotada, ou direção do `SunModel`. [I]
3. **Translúcido no Tier 2.** Não ordena por frame e não tem OIT. Vidro fica sempre no Tier 1. [C]
4. **UVs do atlas.** A recarga de recursos invalida o cache (limpar no construtor do renderer). No Tier 2, fechar também os `GpuBuffer`. [C]
5. **Iris e shaderpacks.** Pipeline custom pode não ser reconhecido por eles. O Sodium não toca no caminho de entidades [I]. Aqui o risco é desconhecido.
6. **Block entities.**
   - `BlockEntity.loadStatic(BlockPos, BlockState, CompoundTag, HolderLookup.Provider)` instancia sem level, e `tryExtractRenderState` os recusa. O esboço chama o renderer direto. [C]
   - Baú não anima a tampa. Renderers que exigem level (beacon, gateway) precisam de teste. [I]
7. **Limitações do fake level.** `ModelData` dinâmico fica vazio. Fluidos (`FluidRenderer`) não são desenhados. Tint de bioma fica -1 (branco). [C]
8. **Inchaço por altitude no Tier 1.** Pode ser aceitável no 3a (altitude baixa) e feio no 3b/3c. [V]
9. **Volatilidade da API.** `com.mojang.renderpearl.*` é novo e a NeoForge ainda mexe em `FeatureRenderer`, `RegisterFeatureRenderersEvent` e `RenderPhaseKey`. Fixar a versão. [I]
10. **Hitbox e tracking.** O renderer não resolve a hitbox por pose. O foguete grande precisa de `clientTrackingRange` alto e `updateInterval(1)`. Fora desta frente. [I]

**Perguntas**
1. A orientação será um quaternion sincronizado (4 floats) ou yaw/pitch/roll? Isso define `orientation(partial, dest)`.
2. Aceitamos o inchaço em xz do Tier 1 ou queremos colocação rígida (Tier 2)? Sugiro um cenário no `AutoTest` com prints a 70, 300, 1.500 e 5.000 de altitude (Terra) e na Lua, com tempo de frame.
3. A dimensão do espaço terá `has_skylight` e chunks vazios, ou usamos um mixin em `LevelExtractor.isEntityVisible`?
4. O sombreamento por face deve seguir o sol (`SunModel`) em vez de ficar assado?
5. Sodium e Iris estão no escopo de suporte?
6. Quais tipos de block entity entram no foguete? Baú, placa e inventários são os da lista do plano.

## 8. Ordem sugerida para a 3a (renderização)

1. `RocketPortrait` e `PortraitLevel`, com um teste unitário do BFS de luz extraído para arrays.
2. `PortraitMesh.bake` e `replay` (Tier 1) com o foguete parado.
3. `RocketRenderer` e registro, depois block entities.
4. Prints do cenário `-PshOnly` em altitudes diferentes.
5. Tier 2 e `RocketPlacement` só se os prints do passo 4 mostrarem distorção ou custo. Teste unitário de `RocketPlacement` contra `CurvatureMath` (o `TiltCheck` já serve de base).