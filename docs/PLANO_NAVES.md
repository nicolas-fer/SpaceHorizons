# Plano das naves do SpaceHorizons (v1)

Base: `docs/PLANO_SOBREVIVENCIA.md`, `docs/DESIGN_FOGUETE.md` e `docs/pesquisa/foguete-4-dimensao-espaco.md`. Este plano estende o foguete de blocos para **naves grandes, andáveis por dentro**, que operam no espaço. O céu virtual (4.1, 4.2) e o estacionamento do foguete no espaço (seção 4.3) já existem para o `RocketEntity`; o resto não foi implementado. Os números de escala (Terra R ≈ 104.303, Lua R ≈ 26.076 a 600.000 de distância) vêm da escala 10× em andamento.

## 1. Decisões fixas e visão geral

- Naves estilo Star Trek / Star Wars, **sempre construídas livremente com blocos**, com blocos especiais: núcleo/ponte (`ship_bridge`), motor de impulso (`ship_impulse_engine`), janela (`ship_window`), futuros motor de dobra, escudo, porta de hangar.
- O jogador **anda dentro** da nave.
- **Caminho C**: no espaço a nave é feita de **blocos reais parados** numa área própria (estaleiro) e o que se move é a posição/atitude **virtuais** dela na física orbital. O céu é desenhado a partir desse estado virtual.
- Fases: (1) espaço com interior andável e janelas; (2) decolagem e pouso em planetas; (3) dobra, escudos, hangar para atracar o foguete.

### Por que o caminho C (comparação rápida)

| Opção | Interior andável | Colisão do jogador | Custo | Problema para nós |
|---|---|---|---|---|
| A. "Contraption" estilo Create (entidade com retrato, hoje o `RocketEntity`) | Só com colisor de entidade próprio | Create reimplementa colisão jogador × contraption por frame, tem fama de falhar a alta velocidade e com muitos blocos | Baixo para render, alto para colisão | Nossas velocidades orbitais (centenas de blocos/s) e nave grande destroem esse modelo; o foguete atual evita o problema porque o piloto vai sentado |
| B. Estilo Valkyrien Skies 2 / Create Aeronautics (Sable): blocos reais num "plot"/shipyard longe do mundo, transformada mundo↔nave, física própria e colisão por motor de física | Sim | Resolvida por um motor de física (VS2 usa motor próprio; Aeronautics/Sable, motor de "sub-levels" nativo) | Muito alto (motor de física inteiro, mixins profundos) | Dependência enorme e frágil entre versões; não existe para 26.x |
| **C. Nosso caminho** | Sim | **Nenhuma colisão especial**: os blocos estão parados e o jogador anda no mundo normal | Médio | Precisa de região de chunks própria e céu virtual (o que já temos pronto em grande parte) |

Pesquisa na web (limitada: wikis/páginas de mods retornaram pouco detalhe de implementação): VS2 e Sable têm blocos reais armazenados em chunks reservados (os "ship chunks"/plot) e aplicam uma transformada ao desenhar e ao colidir; os complementos "Collision damage" e "Entity damage" do ecossistema mostram que colisão nave×entidade, e nave×mundo, é onde mora a complexidade (cálculo próprio por tick após o passo de física). **A diferença do nosso caso**: a nave nunca precisa colidir com terreno enquanto está no espaço, então podemos manter o benefício central do estaleiro (blocos reais, andar normal) **sem** a transformada nem o motor de colisão. A nave só vira "objeto móvel" (entidade, como o foguete) nas atmosferas, onde o piloto e a tripulação ficam presos/sentados, que é exatamente o regime que já funciona. Fontes consultadas: páginas de mods no CurseForge/Modrinth/modpackindex sobre VS2 e Sable (por exemplo "Aerophysics", "Aeronautics entity damage", "Sable cleanup": o último confirma que desmontar uma sub-nave devolve os blocos ao mundo na posição atual, análogo ao nosso "entidade → blocos"). Confiança média; o código-fonte do VS2 não foi lido.

## 2. Modelo de dados

### 2.1 Identidade e registro

- `ShipId` (UUID). `ShipRecord` (persistido em `ShipRegistry extends SavedData` no Overworld, como o resto do estado do mod): `id`, nome, dono, `Phase` (`ASSEMBLY_GROUND`, `ENTITY_FLIGHT`, `SPACE_YARD`, `LANDED_BLOCKS`), região do estaleiro (se houver), `ShipDimensions` (caixa local em blocos), `RocketCensus`-equivalente (`ShipCensus`), estado orbital, atitude, combustível e tripulação.
- **Dois tipos de verdade por fase**: em `ENTITY_FLIGHT` a verdade é o retrato na entidade (`RocketPortrait`, igual hoje); em `SPACE_YARD` a verdade são os blocos no estaleiro; em `LANDED_BLOCKS` os blocos no chão do planeta (o `ShipRecord` guarda só um marcador, âncora e caixa).

### 2.2 Onde ficam os blocos: dimensão do estaleiro

**Achado importante**: a dimensão `spacehorizons:space` é `height 16, min_y 0` (uma seção só; ver `data/.../dimension_type/space.json`). Ela serve para o foguete voar (entidade em Y ≈ 20.000 não precisa de bloco), mas **não comporta uma nave de 30 blocos de altura**.

Proposta: **nova dimensão `spacehorizons:shipyard`** (tipo próprio, `height 1024`, `min_y 0`, void flat, `has_skylight false`, sem clima, `ambient_light` baixa, sem spawns, sem relógio). Coordenadas de mundo do estaleiro são **só um mapa de células**, sem relação com o espaço virtual. A dimensão `space` continua a existir para foguetes, EVA e entidades (aproveita todo `SpaceDimension`, `FrameBridge`, `Zones`).
Alternativa (decisão aberta D1): aumentar `space` para 384+ de altura e alocar o estaleiro nela, perto de Y baixo. Contra: muda um mundo já criado e o mapeamento `Y_BIAS = 20000`; a dimensão separada é mais limpa e permite luz de interior própria.

### 2.3 Alocação de regiões sem colisão

- Grade de **células** de `CELL = 1024 × 1024` blocos em XZ (64×64 chunks), altura 1024. Tamanho máximo da nave: **128 × 128 × 256 blocos** na fase 1 (cabe com folga e dá margem de 448 blocos de vazio entre naves; protege contra vazamento de luz/atualizações e de ticks de blocos vizinhos).
- `ShipYardAllocator`: índice sequencial em espiral (`cellIndex → (cx, cz)`), `Set<cellIndex>` livres reaproveitáveis ao destruir a nave (com *tombstone* de uma sessão para evitar reutilização com NBT residual). Origem da nave = centro da célula; o **cockpit/ponte é a âncora local (0,0,0)**.
- Mapeamento nave↔yard: `yardPos = cellOrigin + (localPos − anchor)`, apenas translação (**sem rotação**): a nave no estaleiro nunca roda; só o céu roda (ver seção 4).
- Sem rotação no estaleiro também elimina o problema de estados de bloco/NBT rotacionados ao entrar no espaço (só o pouso no planeta rotaciona, igual ao foguete: `state.rotate`, yaw a 90°).

### 2.4 Manter carregado

- Enquanto houver jogador ou entidade de tripulação dentro da nave (ou no "raio de interesse"), ticket `ENDER_PEARL`-like próprio (`ShipChunkTickets`) sobre a **caixa da nave**, raio 0; sem ninguém, os tickets caem após 5 min (tick lento) e a nave "congela" (seu estado orbital continua avançando pelo `ShipRegistry`, ver 2.5).
- Uma nave grande tem muitos chunks: 128×128 = 8×8×(256/16) = 64 chunks-coluna; custo aceitável, mas o plano limita o número de naves ativas e mostra isso no spike (S2).
- O estaleiro é `isAlwaysTicking` só para a entidade de jogadores; block entities (máquinas, baús) ticam enquanto os chunks estiverem carregados, igual a qualquer dimensão.

### 2.5 Estado orbital virtual e atitude

- **Reaproveita o estado do foguete**: `FlightState(body, r, v, fuelMb, heat)` de `space.flight.*`, e o **mesmo** `FlightIntegrator.step`, `SoiTracker`, `Zones`, `Orbit`. A nave no espaço é uma entidade *virtual*: não há entidade no mundo, só `ShipRecord.flight`.
- Atitude: quaternion inercial `Quaternionf` (igual ao `RocketFlight`/`AttitudeMath`), controlada por `AttitudeControl` e SAS. Para o céu usa-se o quaternion sincronizado ao cliente (`ShipStateSyncPayload`).
- Avanço do estado: `ShipFlightTicker` (servidor) integra **todo tick** cada nave `SPACE_YARD` ativa; naves sem ninguém dentro usam passo grosso (n ticks de uma vez, Kepler analítico por `Kepler`/`Orbit` quando sem empuxo) para a física continuar sem custo.
- **Referencial**: a nave está sempre no referencial dela; o **mundo virtual (Terra/Lua/sol) se move em torno** dela do ponto de vista do render. Não há dilatação de coordenadas: o `FlightState` mantém precisão `double` relativa ao corpo dominante (R_Terra ≈ 104 k, bem dentro de `double`) e agora é INERCIAL (referencial I, em que a Terra gira e a Lua orbita em círculo kepleriano, ver `space/InertialFrame`); as dimensões, o render e o estaleiro continuam no referencial preso à Terra (E).

## 3. Transições

Linha do tempo de uma nave: **chão (blocos) → montagem (entidade) → subida → entrada no espaço (entidade → blocos no estaleiro) → voo virtual → descida (blocos → entidade) → pouso (entidade → blocos no chão)**.

### 3.1 Montagem no chão (fase 2; na fase 1 já nasce no estaleiro pelo criativo)

- Estender `RocketAssembler`/`RocketAssembly` para o `ShipAssembler`: BFS a partir do bloco `ship_bridge` (em vez de `rocket_cockpit`), teto maior (decisão D2: 8.192 blocos; o foguete tem 1.024), tag `spacehorizons:ship_parts` aceitando qualquer bloco sólido não proibido (`rocket_forbidden` reaproveitada). Mesmos erros tipados (`RocketException`) e validação antes de remover.
- Limite do pacote de spawn (~1 MiB): o `RocketPortrait` denso não escala para 8.192 blocos com block entities. Para naves grandes o retrato vai **esparso** (só blocos não ar, por seção) e as block entities **não** vão no spawn (só no save); cliente renderiza só o necessário (ver 4.3). **Spike S3.** **Resultado do S3: o retrato denso com block entities cabe no pacote até onde foi medido (117 KB para ~8.000 blocos e 74 baús; limite de ~600 baús cheios), mas o retrato do foguete só aceita 64 de lado; ver "Resultado do S3".**
- Sem criativo de entidade grande no solo: na fase 2 uma nave que não passa pelo teto do pacote pode decolar **pelo caminho "estaleiro direto"** (decisão D5: construir direto no estaleiro e "descer" até um planeta só vira nave-entidade na descida, não na subida).

### 3.2 Subida e entrada no espaço (entidade → blocos no estaleiro)

1. A nave sobe como `RocketEntity` (reuso total: `RocketFlightController`, `FlightMachine`, `ChunkGate`, paraquedas etc.; a tripulação vai presa ao casco por `positionRider` via assentos). A "gravidade artificial" é só o piloto sentado; tripulação extra sentada em assentos (`ShipSeat`, uma entidade invisível por assento, igual aos "seats" do Create).
2. Ao cruzar `Zones.shouldLeave` (altitude de saída: Terra 5.000 blocos): em vez de teleportar a entidade para a dimensão `space`, o servidor executa `ShipYardEnter`:
   - aloca a célula (2.3) e **escreve os blocos do retrato** no `shipyard` com a mesma rotina de reposição do foguete (dois passes: sólidos, presos), sem rotacionar (a atitude inercial vira atitude do céu, ver 4.2);
   - repõe block entities com NBT (baús, tanques) e **cada** `RocketFuelTankBlockEntity` mantém seu `mB`;
   - reposiciona cada passageiro e entidade de bordo: `local = R⁻¹(rocketAttitudeLocal) · (pos − pivô)` + âncora → posição no estaleiro (`shipyardPos`); a vista do jogador mantém a mesma orientação *relativa à nave* (yaw/pitch relativos à ponte). Uma transferência por `TravelTransferPayload` (existente) sem tela de carregamento;
   - estado orbital: o `FlightState` da entidade vira o `ShipRecord.flight`; a entidade é removida;
   - **fluidos e redstone**: fluidos em blocos (água, lava) ficam como estão (são blocos); o ticket de líquidos é agendado normalmente. Redstone: sinal permanece (blocos reais); sinais ligados no instante da conversão podem piscar um tick (aceito; o `UPDATE_SUPPRESS_DROPS` + um passe final de `markAndNotifyBlock` já usados no foguete mitigam). Tanques e baús guardam o inventário no NBT (testado com cada block entity no autoteste, como pede o plano de sobrevivência).
3. Os jogadores **fora** da nave nesse instante (que não estavam sentados): são EVA (3.5).

### 3.3 Voo no espaço

Só o `ShipRecord.flight` muda (3.2, 2.5). Os jogadores andam normal no estaleiro. Nada de teleporte nem de entidades se mexendo; é por isso que não há colisão.

### 3.4 Descida e pouso (blocos → entidade → blocos)

- `Zones.shouldEnter` (e `SoiTracker`) indica a atmosfera de destino. Antes de entrar: aviso "tripulação em assentos" no painel; jogadores sem assento num intervalo de ~60 ticks são **forçados a sentar** no assento livre mais próximo ou, se não houver, ficam como **passageiros "presos"** (sem movimento, sem entrada): decisão D4. Alternativa mais fácil na fase 2: recusar a descida enquanto alguém não estiver sentado/em estação.
- `ShipYardLeave`: lê os blocos da caixa do estaleiro, monta um `RocketPortrait`/retrato esparso, recria a entidade na dimensão do planeta (`FrameBridge.toDimension`), reposiciona tripulação nos assentos, limpa a região do estaleiro (setBlock AIR sem drops, depois libera a célula). **Validar tudo antes de remover** (espaço no destino carregado, tamanho do pacote).
- Pouso: igual ao foguete (`LandingRules.contact`, desmontagem com `RocketAssembler` invertido, pré-verificação do espaço, yaw 90°). Nave grande exige pernas/trem de pouso (`ship_landing_gear`); pouso de **barriga** só em terreno plano (decisão D6).
- **Batida**: como no foguete (blocos viram itens), mas com limite de itens (nave grande: caixas "ShipWreck" em vez de milhares de itens; decisão D7).

### 3.5 EVA (jogador fora da nave no espaço)

**Escolha: referencial da nave primeiro, virtual só ao se afastar.**

- Nos primeiros **N = 48 blocos** do casco o jogador está no estaleiro; sair pela "eclusa" (`ship_airlock`) o leva a uma **plataforma de EVA** (região vazia dentro da célula, em volta da nave, 48 blocos de margem). Ali anda em microgravidade (impulso por jato de capacete do traje, já existe `PlanetGravity = 0` no espaço e o sistema de oxigênio). Como a nave está parada no estaleiro, o EVA próximo é trivial (mundo normal).
- Para além desse raio o jogador **não pode** ir pelo estaleiro (a célula acaba): a "correia de segurança" conduz a uma transferência: o jogador vira **entidade livre na dimensão `space`**, com `FrameBridge` calculando posição e velocidade a partir da nave e do referencial inercial (`spaceDimToSpace`). O céu dele deixa de depender da nave e passa a usar `SkyObserver.forSpace` normal (já existe).
- Para ver a nave de fora (EVA longe) a nave aparece como **modelo** na posição relativa (4.3), e o jogador pode voltar a "entrar" ao se aproximar a menos de N blocos (de volta ao estaleiro).
- Na fase 1 basta o primeiro nível (andar até a eclusa e a plataforma; sem EVA livre). **A EVA livre é fase 2/3.**

## 4. Render

### 4.1 O céu pelo observador virtual

- Reaproveita `SkyObserver`: hoje `forPlanet` e `forSpace` com `localToSpace` constante. Novo `SkyObserver.forShip(clock, posVirtual, shipAttitude)` com **`localToSpace = R_atitude · SpaceDimension.LOCAL_TO_SPACE`**, `pos = FlightState` da nave convertido para espaço absoluto em E (`FrameBridge.absoluteInEarthFixed`: o estado é inercial e a posição absoluta vem de I→E no relógio; a Terra, o sol e a Lua de `BodyRenderer` já estão em E). `BodyRenderer`, `BodySky.fromSpacePoint`, `meshToLocal`, `sun()` já recebem tudo daqui, então Terra, Lua, sol e estrelas giram com a atitude sem mexer neles.
- No cliente, em `SpaceSkyClient`, **o quarto estado de dimensão**: quando `level` é `shipyard`, usar `ShipRenderClient.currentObserver()` (último `ShipStateSyncPayload` interpolado), em vez do spawn da Terra.

### 4.2 Sol e estrelas do vanilla não ficarem fixos

- O `SpaceSkyClient.onExtractRenderState` hoje chama `SpaceSun.update(level, EARTH, 0, 0)` com giro nulo. Para a nave: calcular `sunAngle` e `starAngle` a partir do observador virtual e **aplicar a atitude ao céu**: o vanilla só aceita um ângulo escalar de sol/estrelas, então precisa de um **mixin** (ou evento de render equivalente) que multiplique a matriz do céu por `R_atitude⁻¹` (quaternion completo, não só ângulo). Já existe `SkyRendererMixin` (cancela a lua) e `SpaceSun.clearView`: estender. Spike S1 verifica se o sol/estrelas do vanilla aceitam essa matriz no Blaze3D do 26.3 sem substituir o céu inteiro. **Plano B**: desenhar estrelas e sol por conta própria (esfera de estrelas já é um campo conhecido; o disco do sol já sai de `SunModel`), desligando o céu do vanilla na dimensão do estaleiro.
- `BodyRenderer` (Terra, Lua) ganha a atitude de graça por `localToSpace`.
- **Resultado do S1 (aprovado): o caminho do mixin funcionou, sem plano B.** Implementado: `SkyObserver.forShip(clock, posE, quaternion)` (`localToSpace = R · LOCAL_TO_SPACE`, ortonormal), `SkyObserver.vanillaStarsToLocal` (rotação do referencial do spawn, onde o vanilla desenha o céu, para os eixos locais) e `SpaceSun.updateForObserver`. O céu do vanilla aceita a atitude completa SEM substituir o céu: (a) **estrelas**: o `SkyRendererMixin.renderStars` já multiplicava a pose por `SpaceSun.starRotation()`; agora essa matriz é um quaternion completo (`Mᵀ·LOCAL_TO_SPACE`), identidade na atitude identidade; (b) **sol**: o disco do vanilla só gira em altura (`sunAngle`) + o giro em torno da vertical que o mixin `renderSun` já aplicava (`sunTilt`); qualquer direção cabe nesses dois ângulos (`SunModel.vanillaSkyAngle` + `azimuthTilt`), então o sol cai exatamente na direção de `observer.sun()`, a mesma que ilumina as esferas. Um único valor de atitude por frame (`SkyAttitude.update` na extração; `BodyRenderer` lê o mesmo), em `double`, sem tremor. Comando de dev (op): `/spacehorizons skytest <yaw> <pitch> <roll>` (graus, eixos locais), `skytest spin <graus/s> [x|y|z]`, `skytest off`; estado só no cliente, enviado por `SkyTestPayload`. Arquivos: `client/space/SkyAttitude`, `SkyTestClient`, `space/SkyTestCommand`, `SkyTestPayload`.
- **Verificado** (autoteste `skytest`, `-PshOnly=skytest`): com o sol logo abaixo do horizonte (relógio 22.500) o terminador da Terra fica na calota visível e o lado claro aponta para o sol em identidade, yaw 90, pitch 45, roll 90 e combinação; a posição do disco bate com o `observer.sun()` medida em pixels; prova de equivalência pixel a pixel (atitude yaw 90 com câmera a +90 dá o mesmo quadro que identidade: 1 pixel diferente em 212.000, estrelas, sol e Terra); giro contínuo de 60 graus/s (6 prints em 3 s: ~30 graus a cada 0,5 s no log da atitude) e Terra, estrelas e sol girando juntos; ligar/desligar sem salto (prints idênticos); travessia Terra para espaço com identidade sem tela de carregamento e sem salto. Limitações: (1) o disco do sol é o quadrado do vanilla e seu giro próprio (roll) acompanha o ângulo/tilt, então com algumas atitudes aparece como losango; para a nave final trocar por disco redondo próprio (pequeno: já temos a direção) ou compensar o roll; (2) com atitude não identidade, a troca de dimensão para o planeta mostra o salto inerente (o céu do planeta não leva a atitude da nave); (3) o brilho do nascer/pôr do sol (zerado no espaço) e as estrelas fixas em relação ao sol valem só enquanto o céu do espaço for este; (4) o tremor foi verificado por consistência numérica (teste de passo constante do giro e coincidência dos prints), não por vídeo quadro a quadro.

### 4.3 Outras naves e foguetes

- **Já implementado para o foguete (`RocketEntity`)**: no espaço o foguete fica ESTACIONADO num ponto fixo da dimensão (`rocket/SpaceParking`) e o céu do piloto sai de `SkyObserver.forShip` na posição real do voo (sincronizada à parte, `client/space/ShipView`), com atitude identidade. É o caminho C com o foguete no papel da nave: a posição/atitude virtuais são o estado de voo, a entidade é um objeto parado. Ver `docs/DESIGN_FOGUETE.md`, "Estacionamento no espaço".
- **Limitação atual**: quem não está num foguete na dimensão de espaço (`goto space`, voo criativo) vê o céu da posição real e não vê o foguete estacionado no lugar certo (ele está no ponto fixo, a ~8 milhões de blocos). Desenhar os outros foguetes na posição relativa (abaixo) fica para depois: precisa de um renderer que os posicione em `(r_outro − r_minha)`; o `RocketEntity` já sincroniza a posição real (`VIRTUAL_POS`) de cada um, então a base existe.

- Cada outra nave/foguete visível no espaço (mesma vizinhança orbital) é desenhado como **modelo**: retrato da `RocketEntity` via `RocketMeshCache`/`PortraitMesh` (Tier 1), posicionado na posição relativa `(r_outro − r_minha)` rotacionada para os eixos locais do observador (`spaceToLocal`) e escalada por distância (impostor para > 2 km). Para naves em `SPACE_YARD` de outro dono: `ShipPortraitSync` envia um retrato **esparso/LOD** (casco externo apenas, sem interior) ao cliente quando passam de X blocos de distância virtual.
- Nenhum corpo aparece "dentro" do estaleiro; é só render no céu.

### 4.4 Luz do sol nos blocos da nave (opcional, fase posterior)

- Interior com luz de bloco; janelas deixam passar luz do sol? Fase 3: uma `SunlightMask` calcula por face externa o ângulo com a direção do sol (atitude × `SunModel.directionInSpace`) e alimenta um `sky_light` por seção ou um mixin leve de iluminação. Antes disso, a nave tem `has_skylight false` e o interior depende de lâmpadas (decisão D8).

## 5. Controles e multiplayer

- **Piloto**: `ship_bridge` tem um assento; sentar liga o modo piloto. O cliente reaproveita `RocketKeys`, `RocketPilot`, `RocketThrottle`, `RocketPanel`/`RocketPanelModel` e o SAS por pacote `RocketControlPayload` (renomear para `FlightControlPayload` ou reutilizar com `ShipId`). Teclas: as do foguete (W/S arfagem, A/D guinada, Q/E rolagem, Shift/Ctrl acelerador, T SAS) **mais** controles de translação (RCS/atracação) fase 2.
- **Câmera**: `RocketCamera`/`CockpitCamera` **não** rodam a câmera pela atitude (o jogador está no mundo parado). A atitude vira o rolar do céu (4.1), então é só `SkyObserver`; a câmera continua a normal do jogador (olhar livre). Piloto vê o painel e a janela.
- **Vários jogadores**: um piloto por nave (o que está no assento); os demais andam. Painel de telemetria no console de comando também por estação (fase 2). Atores adicionais (artilheiro, engenheiro) fase 3. O estado de voo vive no servidor e sincroniza por `ShipStateSyncPayload` (≈ a cada 2 ticks, só para quem está dentro/perto).
- **Servidor dedicado**: tudo roda no servidor (sem classes do cliente fora de `client/ship`). A dimensão do estaleiro precisa estar no *world preset* (como a `space`); para mundos existentes sem ela, o comando `/spacehorizons ship` recusa com mensagem (igual ao aviso do foguete).

## 6. Física

- **Massa e empuxo pela contagem de blocos**: `ShipCensus` análogo a `RocketCensus` (ponte, motores de impulso, tanques, casco, estrutura, janelas…), `ShipSpecs.statsFor(census)` devolvendo o mesmo `RocketStats` (massa seca, empuxo, Isp, capacidade de combustível, k de arrasto) para `FlightIntegrator`, `Tsiolkovsky`, TWR, painel. Reutilizar `RocketSpecs` com novos tipos de bloco (motor de impulso: empuxo maior e Isp menor; motor de dobra: fase 3).
- **A nave inteira é um referencial inercial local; a aceleração NÃO é aplicada aos jogadores dentro.** Por quê: (a) a nave no estaleiro está **parada**, então não há como "empurrar" o jogador sem simular impulso por entidade a cada tick, o que reintroduz a colisão que o caminho C evita; (b) jogabilidade: andar numa nave que acelera a centenas de blocos/s² seria inviável; é o "gravidade artificial/compensador inercial" de Star Trek e Star Wars (também de The Expanse sem g). (c) evita enjoo e problemas de rede (nenhum movimento de jogador devido ao empuxo). Custo: não há sensação de aceleração em si; compensar com **câmera shake** e som do motor (`RocketEngineSound`, `RocketEffects` adaptados), luz de painel e os números no painel.
- Gravidade interna: **fixa, para baixo da nave** (`PlanetGravity` do estaleiro = 0,08 b/t², escolha do jogo; decisão D9: ligável/desligável por bloco "gerador de gravidade" mais tarde).
- Atmosfera dentro da nave: o ar da cabine usa o mesmo sistema de oxigênio (`RocketCabinOxygen` é só do foguete; nave grande precisa de **volume selado** real: flood fill de ar a partir de um bloco `ship_life_support`, com vazamento por buraco no casco). Fase 2 (fora de escopo da fase 1; na fase 1 usa-se "o interior inteiro é respirável" por marcação simples).

## 7. Multiplayer, desempenho, riscos e incógnitas

### Desempenho
- Naves no estaleiro custam chunks carregados (64+ por nave grande) e block entities ticando. Limites: nº de naves ativas por servidor, `ShipChunkTickets` com expiração, `ShipFlightTicker` com passo grosso para naves vazias.
- Pacote de conversão grande: gravar a nave em lotes por seção em vários ticks com "porta" de entrada fechada e jogador congelado ("tela de salto", ~1 s) em vez de bloquear o tick; fase 1 aceita um hitch.
- Render de naves alheias: retrato esparso + LOD. Interior da própria nave é mundo normal (sem custo especial).

### Riscos e incógnitas
1. **Céu virtual com rotação completa** no Blaze3D 26.3 (a matriz do sol/estrelas do vanilla): ver S1.
2. **Altura da dimensão e luz**: dimensão separada de altura 1024 e `has_skylight false` pode ter surpresas (heightmaps, luz em seções vazias, `chunk sections` x 64). S2.
3. **Pacote/ NBT de nave grande** na transição entidade↔blocos (limite de 1 MiB). S3: medido (ver "Resultado do S3"); o risco que sobra é o retrato de 64 de lado e os muitos baús cheios.
4. **Perda de itens/entidades** na conversão (mesma classe de bug dos trens do Create).
5. **Servidor dedicado**: tickets de chunk e salvamento do `ShipRegistry` (nunca testado).
6. **Mundos existentes**: precisa do world preset novo; documentar migração (D1).
7. **Escala**: pesquisas anteriores definem altitude de saída 5.000 (Terra); com a escala 10× e raio 104.303, rever `Zones.exitAltitude`, SOI e `TransferPlanner` (fora do escopo deste plano, mas a nave herda).
8. **Jogador desconectado dentro de nave**: ao reconectar na fase `SPACE_YARD` precisa reentrar no estaleiro; se a nave pousou/foi destruída enquanto ele estava fora, reposicionar para `ShipRecord.lastSafePos`.
9. **Duas naves atracadas**: fase 3 (porta de hangar com o foguete): exige que o foguete vire "módulo" da nave (unir regiões).

### Spikes antes de comprometer (nesta ordem)
- **S1 (céu virtual):** na dimensão `space` atual, um comando `/spacehorizons skytest <yaw> <pitch> <roll>` fixa uma atitude falsa e gira sol/estrelas/Terra/Lua; critério: Terra e sol giram juntos de forma coerente, sem tremor, sem salto na troca de dimensão. Sem isso, o caminho C não existe. **Resultado: APROVADO** (mixin, sem plano B; detalhes e limitações na seção 4.2).
- **S2 (estaleiro):** criar a dimensão `shipyard` e uma nave de 64×64×64 colada por comando em duas células distantes; jogar, andar, acender luz, fechar e abrir o mundo; checar chunks, tickets, luz, desempenho de 2 naves. **Resultado: APROVADO** (54 verificações, 0 falhas; detalhes abaixo).

#### Resultado do S2 (estaleiro)

**Implementado** (pacote `com.spacehorizons.ship`, cliente em `client/ship`, teste em `client/devtest/ShipDevtest` + `AutoTest.shipYardScenario`):
- Dimensão `spacehorizons:shipyard` (`dimension_type/shipyard.json`: `min_y 0`, **`height 384`** em vez dos 1024 do rascunho, pois a nave máxima (256) + 32 de vão embaixo + 96 em cima cabe; `has_skylight false`, `has_ceiling false`, sem clima; gerador flat vazio; bioma `spacehorizons:space`, sem spawns) registrada no world preset `earth`. Só mundos novos a têm; nos antigos `ship ...` recusa com mensagem.
- `ShipYardAllocator` (pura, testada): espiral quadrada de células de 1024×1024; liberada → quarentena até a sessão seguinte (tombstone). `ShipYard`: caixa centrada na célula e alinhada a chunks, piso em Y=32. `ShipRegistry` (SavedData no Overworld, id `spacehorizons:ships`): naves (`ShipRecord`, id inteiro sequencial), alocador e ponto de retorno por jogador. `ShipChunkTickets`: um ticket (tipo próprio, sem expiração, não persistido) de raio 1 por coluna de chunk da caixa, enquanto houver jogador na célula e por 5 min depois (`DEFAULT_RELEASE_TICKS = 6000`; o teste reduz para 100). `ShipBuilder`: nave de teste (casco oco, faixa de janelas em volta, janela no teto 17×17, vidro no piso 9×9, lanternas do mar no piso e no teto, cabine com porta, baú, fornalha acesa, lâmpada de redstone com alavanca). Comandos (op) `/spacehorizons ship create [40..128] | list | goto <id> | leave | info`.
- Céu: `SkyObserver.of` ganhou o ramo do estaleiro (`ShipSkyClient.observer`: posição virtual fixa de teste a 30.000 de altitude sobre o spawn da Terra, atitude identidade ou a do `skytest`), e `SpaceSkyClient` trata o estaleiro como espaço (sol/estrelas do vanilla, sem névoa, sem lua do vanilla). Reaproveitou o S1 sem mudanças.

**Decisões confirmadas:** D1 dimensão nova `shipyard` (funciona; com `has_skylight false` não houve surpresa de heightmap ou luz); D2 limites 128×128×256 (a folga mínima entre caixas é de 448 blocos, coberta por teste); D8 interior só com luz de bloco (luz de céu 0 dentro, no servidor e no cliente, mesmo olhando o espaço pelas janelas); D9 gravidade normal (atributo 0,08: `PlanetGravity` devolve 1 fora de planeta e de `space`, então não precisou de código; o jogador subiu 8 blocos e caiu no piso em 14 ticks); o interior é respirável porque o `OxygenSetup` só considera vácuo planeta e `space`.

**Medições** (autoteste `-PshOnly=shipyard`, mundo novo, cliente + servidor integrado na mesma JVM, outros clientes de Minecraft rodando na máquina: números são ordem de grandeza; ver "Problemas" para o que foi e o que não foi medido):
- Criar uma nave de 64³ (24.099 blocos, 16 colunas de chunk): 92 a 396 ms (a primeira inclui o aquecimento); duas ficam a 960 blocos de vazio uma da outra (células 0 e 1).
- Chunks carregados no estaleiro: 784 depois da nave 1, 1.568 com as duas seguradas sem ninguém (784 por nave: 16 colunas da caixa com tick de bloco + anel de raio 1; os 24 níveis de seção vazios são baratos), 2.601 a 3.385 com o jogador dentro (o raio de visão dele soma). Com os tickets soltos: 0.
- Tempo de tick do servidor (média de 100 ticks): Terra sem naves 5,5 ms (overworld 5,0); jogador dentro de uma nave com as duas seguradas 2,7 ms total, dos quais 2,1 ms são da dimensão estaleiro (a Terra descarrega quando ninguém está nela); jogador na Terra com as duas naves seguradas e vazias 9,1 ms, estaleiro só 0,11 ms (o resto é a Terra e o ruído da máquina, a diferença contra a linha de base de 5,5 ms não foi atribuída às naves). Custo de uma nave vazia segurada: ~0,05 a 0,1 ms/tick.
- Memória (heap após GC): 1.035 MB antes, 1.043 MB depois de criar as duas naves, 1.136 MB com as duas seguradas, 1.124 MB soltas: variação dentro do ruído (os chunks do estaleiro são quase só ar). Não é uma medida de custo por nave confiável.
- Fechar (salvando, com o jogador dentro da nave 1) e reabrir o mundo: 1,6 a 2,5 s para fechar e ~5 s para o jogador voltar (a abertura para no aviso padrão "Worlds using Experimental Settings", que o teste confirma com o 2º botão; é comportamento do preset do mod, não das naves).

**Verificado** (todas no log como `SHIPCHECK PASSOU`): dimensão presente no mundo novo; 2 naves, células distintas, folga de 960; entrar (`goto`), andar até a porta e para dentro da cabine com movimento de verdade, abrir a porta e o baú (tela com os 115 itens), lâmpada de redstone ligada/desligada pela alavanca três vezes (luz de bloco 0 → 13 no servidor e no cliente); fornalha acesa e queimando (lingotes saindo); gravidade; céu pelo vidro do piso (Terra de 51 graus de raio), pelas janelas (sol e Lua, achados por varredura do relógio e conferidos por raio de visão que atravessa o vidro) e prints; nave 2 independente (lâmpada apagada, baú intacto) com a 1 ainda segurada; `ship leave` volta ao ponto de origem (2,5 blocos de erro: o ponto é salvo com a posição de quando se entrou); depois de 100 ticks sem ninguém os tickets caem e os chunks descarregam (1.568 → 0); reentrar carrega do disco com baú (54 diamantes após tirar 10), lâmpada, alavanca, porta e fornalha como ficaram; fechar e reabrir o mundo com o jogador dentro: continua na nave 1, as 2 naves, o ticket volta sozinho, itens e blocos idênticos, `ship leave` ainda leva à Terra (o ponto de retorno também persiste). Testes unitários: `ShipYardAllocatorTest`, `ShipYardTest`, `ShipRegistryTest` (espiral única e adjacente em 5.000 índices, quarentena entre sessões, ida e volta do codec, caixa dentro da célula).

**Problemas e limitações:**
1. Uma das 4 execuções do autoteste (a 2ª) mostrou blocos da cabine e do vidro do piso ausentes no servidor (baú, fornalha, lâmpada, alavanca e o vidro do piso viraram ar com a nave carregada), seguida de falha ao reabrir ("Lock is no longer valid"). A 1ª (todas as verificações da nave passaram; o roteiro de recarga ainda tinha um bug do teste, corrigido depois), a 3ª e a 4ª (54 de 54) não mostraram a perda; a verificação de integridade (`SHIPINTEGRIDADE`: 24.099 blocos não-ar e marcadores presentes) só existe a partir da 3ª e foi idêntica em todos os passos dela e da 4ª. A 2ª coincidiu com um cliente anterior encerrado à força que pode ter continuado a escrever no mesmo `run/saves`; não foi possível provar isso nem reproduzir a perda. **Fica registrado como risco aberto**: se aparecer de novo, investigar a escrita de chunks do estaleiro.
2. O céu do estaleiro usa uma posição virtual FIXA de teste; ligar ao estado de voo da nave (sincronização) é a fase 1c/1d.
3. As janelas deixam ver o espaço mas o interior é escuro de propósito (sem luz de céu, D8): uma nave grande precisará de bastante iluminação; o teste só põe lanternas no piso e no teto.
4. `ship create` constrói no tick do servidor (92 a 396 ms para 64³); para naves grandes ou conversão (S3) é preciso lotes.
5. O `ShipChunkTickets` não persiste: depois de reiniciar o servidor a nave só volta a ser segurada quando alguém entra na célula dela (confirmado com o jogador dentro; uma nave vazia fica descarregada, como projetado).
6. Não verificado: servidor dedicado, mais de um jogador, jogador que se desconecta dentro da nave (o teste de recarga cobre só o jogador único voltando ao mesmo lugar), naves de 128×128×256, tempo de criação e chunks com várias naves acima de 2, e o custo de memória por nave de forma isolada.
- **S3 (conversão):** `RocketEntity` com retrato de ~4.000 blocos ↔ blocos no `shipyard`: inventários intactos, NBT íntegro, tempo da conversão, tamanho do pacote. **Resultado: APROVADO** (151 verificações, 0 falhas; detalhes e limites abaixo).

#### Resultado do S3 (conversão entidade ↔ blocos)

**Implementado** (pacote `com.spacehorizons.ship`; teste em `client/devtest/ShipConvertDevtest` + `AutoTest.shipConvertScenario`, `-PshOnly=shipconvert`):
- `ShipConversions` (fila e laço, um job por vez, roda no fim de cada tick do servidor) com `ConversionBudget` (orçamento por tick: **blocos por tick e teto de ms por tick**, padrão 1.500 e 20 ms, `/spacehorizons ship budget <blocos> [ms]`). Jobs em lotes (D10): `RocketToYardJob` (entidade → estaleiro: aloca a célula, carrega as colunas, coloca os blocos com sólidos antes dos presos, carrega cada block entity com o NBT e confere NBT ao carregar, avisa os vizinhos, confere tudo contra o romaneio e só então leva os passageiros e remove a entidade), `YardToRocketJob` (estaleiro → entidade: lê a caixa por seções de chunk, monta o retrato e a entidade num tick só, leva os jogadores — o primeiro senta no cockpit —, remove os blocos em lotes e libera a célula) e `ClearBoxJob` (limpa a caixa e esquece a nave: recuperação e cancelamento).
- O foguete fica **congelado** durante a conversão (`RocketEntity.tick` não roda o voo; uma linha em `RocketEntity`). A entidade continua sendo a verdade até o último passo: se algo falha ou há perda, a caixa é limpa e nada se perde.
- `ShipFlightSnapshot` no `ShipRecord` (com `Phase` READY/FILLING/EMPTYING/REMOVING): dimensão e centro de onde o foguete veio, **o NBT inteiro da entidade sem o retrato** (voo, atitude, acelerador, SAS, aquecimento, combustível, posição virtual), tamanho do retrato, cockpit, passageiros e o romaneio de entrada.
- `ShipManifest` (o romaneio, puro e testado): blocos e block entities, hash dos estados e hash dos NBT (somas de misturas por posição, independentes da ordem), blocos por tipo, itens por id (recursivo no NBT, inclusive dentro de itens), combustível. O relatório de perdas diz o que sumiu (`item minecraft:diamond: 67 -> 64 (perdeu 3)`) e, com os hashes por posição, ONDE. Fornalhas (progresso de cozimento) ficam fora do hash e da contagem (mudam sozinhas): são conferidas de forma estrita na colocação, no mesmo tick, e contadas à parte.
- Combustível: a conversão reparte o combustível da entidade pelos tanques (como o pouso) e a volta soma os tanques (o componente de cada tanque, `fuel_mb`/`rocket_tank_mb`, sobrevive).
- Passageiros: `ShipFrames` (posição `R⁻¹·(pos − centro) + pivô`, vista girada por R⁻¹); o piloto aparece em pé no chão livre mais perto do cockpit (que é um bloco cheio) e a volta o senta no foguete.
- Comandos (op): `ship fromrocket` (o foguete que o jogador olha, ou o mais perto a menos de 64 blocos), `ship torocket <id>`, `ship genrocket <blocos>` (gera o foguete de teste), `ship budget`, `ship convstatus`.
- Foguete de teste (`ShipTestRocket`): casco oco de aço com piso, janelas e lanternas, cockpit, motores, tanques com cargas diferentes, fornalha acesa com progresso, barril e baús cheios (27 pilhas: nome, durabilidade, o item do tanque com o componente do mod) nas paredes. A montagem do jogo recusa mais de 1.024 blocos, então o retrato é montado por código.
- Guarda do pacote: a volta a foguete **recusa** se o retrato passar de 1 MiB no pacote de spawn (`ShipConversions.maxPacketBytes`), a nave fica no estaleiro.

**Medidas** (autoteste, mundo novo, cliente + servidor integrado na mesma JVM com outros clientes de Minecraft rodando na máquina: o tick de linha de base variou de 5 a 9,5 ms; os números são ordem de grandeza e variam muito de uma execução para a outra, daí as faixas; a primeira conversão de cada execução paga o aquecimento do JIT):

| Retrato | Blocos / block entities / itens | Pacote do retrato | Entidade → blocos (1.500/tick, teto 20 ms) | Blocos → entidade |
|---|---|---|---|---|
| pequeno | 501 / 18 / 10.851 | 24.555 B (2,3% de 1 MiB) | início síncrono 5–77 ms, 3 ticks, job 57–115 ms (máx 33–54 ms num tick), 0,20–0,32 s de relógio | 1–2 ticks, job 5–17 ms, 0,10–0,12 s |
| médio | 4.003 / 50 / 33.969 | 77.918 B (7,4%) | início 11–15 ms, 10 ticks, job 54–124 ms (máx 35–53 ms), 0,54 s | 5 ticks, job 37–68 ms (máx 16–56 ms), 0,29–0,51 s |
| grande | 7.997 / 74 / 50.559 | 117.163 B (11,2%) | início 46 ms, 18 ticks, job 149 ms (máx 99 ms no primeiro tick), 0,99 s | 11 ticks, job 54 ms (máx 22 ms), 0,61 s |

- **Pacote de sincronização**: o retrato de ~4.000 blocos com 50 block entities ocupa **77.918 bytes** e o de ~8.000 com 74 ocupa **117.163 bytes**; os dois chegaram ao cliente inteiros (blocos conferidos na entidade do cliente) pelo `writeSpawnData` normal, **sem precisar de envio em partes**. Pelos três pontos medidos o custo é de ~1,65 KB por block entity de baú cheio (extrapolação: o teto de 1 MiB cairia em ~600 baús cheios; os blocos em si custam pouco porque a grade vai em repetições). Acima do teto o `YardToRocketJob` recusa (guarda testada com o teto baixado a 10.000 B: a conversão falha com a mensagem, a nave segue intacta no estaleiro, nenhuma entidade aparece; com o teto normal volta sem perdas).
- **Tick do servidor durante a conversão** (média do tick completo, amostrado nos ticks do job): 6,9–9,6 ms entidade → blocos e 4,0–9,3 ms blocos → entidade (um tick isolado de 16,6 ms), contra a linha de base de 5–9,5 ms; picos de 10 a 23 ms. Com o piloto, a volta teve média de 60 ms e **pico de 271 ms: é a troca de dimensão do jogador (carregar o Overworld e enviar os chunks), não a conversão** (sem piloto o pico foi de 14,5 ms). O orçamento de 20 ms por tick não é um teto rígido: um tick de job chegou a 54 ms (fases indivisíveis: carregar uma coluna de chunk, o passo `BUILD` que monta o retrato e a entidade e teleporta o jogador, aquecimento).
- **Sem lotes (um tick só)** para comparar com a D10: 4.003 blocos entidade → blocos levaram **24 a 245 ms num tick só** (duas execuções) e 7.997 blocos **130 a 213 ms** (0,5 a 5 ticks de atraso); a volta, 17 a 32 ms (4.003) e 44 a 68 ms (7.997). Em lotes: 400 blocos/tick gastam 32 ticks (1,6 s) com no máximo 32 ms por tick e média do servidor 8,1 ms; 100 blocos/tick, 123 ticks (6,2 s, de uma execução anterior); o padrão (1.500/tick + 20 ms), 10 ticks (0,54 s) para 4.003 blocos.
- **D10**: o hitch de ~1 s que o plano temia **não existe**: a conversão inteira de 8.000 blocos com dezenas de baús cheios leva 0,6 a 1 s de relógio em lotes sem que o tick do servidor saia da faixa normal, e mesmo num tick só custa de meio a cinco ticks. Recomendação: manter os lotes (padrão 1.500 blocos e 20 ms por tick) e dispensar a "tela de salto" de 1 s por causa da conversão; o único hitch real é o da troca de dimensão do jogador.

**Verificado** (todas no log como `SHIPCHECK PASSOU`; 151 passaram, 0 falharam), para ~500, ~8.000 e ~4.000 blocos (este com o piloto sentado), e ~4.000 de novo com 400 blocos/tick e num tick só: o retrato chegou ao cliente inteiro; entidade → blocos sem perdas (romaneio) e sem erros; a entidade removida; no estaleiro os **itens dos baús e do barril exatos** (contagem calculada à parte na geração: 10.851, 33.969 e 50.559 itens, por id), **todas as células iguais ao retrato** estado a estado e o combustível dos tanques igual (4.400, 14.560 e 27.360 mB, o piso do combustível da entidade); o piloto levado para dentro da nave, em pé perto do cockpit; **anda de verdade** dentro dela (mais de 5 blocos, 9,85 na primeira execução) e **abre um baú** (tela com as 27 pilhas, soma igual à do servidor, print do baú com o item do tanque do mod); **fecha e reabre o mundo com a nave no estaleiro** (jogador no estaleiro, nave READY com snapshot, itens, células e combustível idênticos); blocos → entidade sem perdas em relação à entrada, na posição original, com o combustível da entidade igual à soma dos tanques, o retrato de novo no cliente, o piloto de volta **sentado** no foguete, a nave fora do registro, a célula liberada, tickets 0 e a caixa do estaleiro vazia; o foguete **desmonta no chão** (`RocketLanding`) com os blocos todos iguais ao retrato original, os itens exatos e o combustível igual. **Recarga no meio da conversão** (nave em FILLING com blocos pela metade): ao reabrir, a recuperação limpou 2.000 blocos e a nave, a entidade seguiu inteira (4.003 blocos) e uma conversão nova fez a ida, a volta e o pouso sem perdas. Testes unitários: `ShipManifestTest` (itens no NBT, hashes sem ordem, fornalhas, perdas e posições), `ConversionBudgetTest` (um trabalho se divide em ticks sem perder nem repetir), `ShipFramesTest` (referencial e vista), `ShipRecordSnapshotTest` (snapshot, fase e registros antigos).

**Problemas e limitações:**
1. **O retrato do foguete só aceita 64 de lado e 64³ células** (`RocketAssembly.MAX_EXTENT`, `PortraitGrid.MAX_VOLUME`, paleta de até 1.025 estados, até 1.024 block entities na leitura): a nave da D2 (128×128×256, 8.192 blocos) não volta a entidade com a estrutura de hoje. Os casos testados cabem porque são cascos ocos. Para naves maiores é preciso o retrato esparso do 3.1; não foi mexido (fora do escopo, e o limite de pacote pesa mais com muitos baús do que com a quantidade de blocos).
2. Sem envio em partes: com ~600 block entities de baús cheios o retrato estoura 1 MiB (extrapolação) e a volta a foguete é recusada pela guarda; o envio em partes (como `PlanetMapChunks`) fica para quando alguma nave real precisar.
3. A conversão só foi exercitada com o foguete pousado (`LANDED`) e atitude identidade; o estado de voo em órbita viaja no NBT da entidade (restaurado pelo mesmo `load` da troca de dimensão), mas o caso espacial e a rotação do passageiro com atitude qualquer só têm teste unitário da matemática.
4. Só passageiros e jogadores dentro da caixa são levados; entidades soltas (itens no chão, mobs) dentro da nave não viajam. Mais de um jogador: o primeiro senta, os outros ficam ao lado (não testado com dois clientes).
5. A recuperação depois de um fechamento no meio foi testada em `FILLING`; `REMOVING` (blocos → entidade, depois de criar a entidade) e `EMPTYING` têm o mesmo caminho de código mas não foram exercitados em jogo.
6. Uma nave editada no estaleiro volta com as edições (o retrato sai dos blocos da caixa); o relatório lista as diferenças em relação à entrada, que nesse caso são esperadas.
7. O foguete de teste é um casco oco com poucos tipos de bloco; uma nave densa (milhares de blocos sólidos e de tipos variados, redstone ativa, fluidos) não foi medida. O custo de um tick só variou muito entre execuções com a mesma configuração (4.003 blocos: 24 ms numa, 245 ms noutra): a causa não foi isolada (a luz dos blocos emissores e as outras JVMs na máquina são os suspeitos).
8. Servidor dedicado, mais de um jogador, e o jogador que se desconecta no meio de uma conversão não foram verificados.

## 8. Fases, entregas e critérios de aceite

Esforço relativo: P pequeno, M médio, G grande, GG muito grande.

| Fase | Entrega | Esforço | Critério de aceite |
|---|---|---|---|
| 0 | Spikes S1, S2, S3 | M | Cada spike passa seu critério; decisões D1, D2, D5 fechadas |
| 1a | Dimensão `shipyard`, `ShipRegistry`, `ShipYardAllocator`, `ShipChunkTickets`, comando de criativo `/spacehorizons ship create|enter|leave` | M | Duas naves em células diferentes; salvar e carregar mantém o estado; teste unitário do alocador (sem colisão, reaproveitamento) |
| 1b | Blocos: `ship_bridge`, `ship_impulse_engine`, `ship_window`, `ship_seat`, `ship_airlock`; `ShipCensus`/`ShipSpecs` | M | Teste unitário `ShipSpecsTest` (massa/empuxo/Δv por contagem) igual a `RocketSpecsTest` |
| 1c | Estado virtual: `ShipFlight`, `ShipFlightTicker` reaproveitando `FlightIntegrator`, atitude, SAS (`AttitudeControl`), pacote de estado, `SkyObserver.forShip` | G | Órbita circular estável 100 ticks na Terra (teste da física pura); autoteste de piloto de teste no servidor, estilo `FlightDevtest` |
| 1d | Render: céu pelo observador virtual com atitude (resultado do S1), janelas, `SpaceSkyClient` para a dimensão do estaleiro | G | Print do autoteste: Terra à direita, depois girar a atitude 90° e a Terra sai da janela; nenhuma estrela fixa |
| 1e | Pilotar: assento da ponte, `RocketKeys`/`RocketPilot`/`RocketPanel` adaptados, vários jogadores (um piloto, outros andam) | M | Dois clientes: um pilota e o outro anda no interior sem teletransporte; painel mostra apoastro/periastro |
| 2a | Montagem no chão e subida: `ShipAssembler`, entidade com retrato, reuso do voo do foguete, entrada no espaço (entidade → estaleiro, `ShipYardEnter`) | GG | Autoteste: monta, decola, cruza 5.000 blocos, chega ao estaleiro com inventários e tanques idênticos |
| 2b | Descida e pouso (`ShipYardLeave`, desmontagem no chão), trem de pouso, batida simplificada | G | Autoteste de missão: órbita → descida → pouso suave e desmontagem sem perder blocos |
| 2c | EVA próxima ao casco (plataforma) e jogador livre no `space` com volta | G | Sair pela eclusa, flutuar 40 blocos e voltar; fora do raio, o jogador vira entidade livre na dimensão `space` |
| 2d | Outras naves/foguetes como modelo no céu (LOD) | M | Print: foguete a 300 blocos aparece pela janela, na posição relativa certa |
| 3 | Dobra, escudos, hangar para atracar o foguete, luz do sol por orientação, volume de ar selado | GG | Por funcionalidade, cada uma com seu cenário |

Autotestes (estilo `AutoTest`, cenários com `-PshOnly=`): `ship` (fase 1: cria nave, entra, olha o céu, gira a atitude, tira prints), `shipflight` (piloto de teste no servidor, estilo `FlightDevtest`, órbita e SAS), `shipconvert` (entidade ↔ blocos com inventários), `shipmission` (missão completa). Testes unitários puros: alocador, `ShipSpecs`, mapeamento local↔estaleiro, composição da atitude do céu (`SkyObserver.forShip` com `localToSpace` ortonormal, coincide com `forSpace` com atitude identidade).

### Arquivos e pacotes novos sugeridos

- `com.spacehorizons.ship` (comum): `ShipId`, `ShipRecord`, `ShipRegistry` (SavedData), `ShipYardAllocator`, `ShipChunkTickets`, `ShipYardEnter`, `ShipYardLeave`, `ShipAssembler`, `ShipCensus`, `ShipSpecs`, `ShipFlight`, `ShipFlightTicker`, `ShipSeatEntity`, `ShipStateSyncPayload`, `ShipCommands`, `ShipBlockKind`.
- `com.spacehorizons.ship.block`: `ShipBridgeBlock`, `ShipImpulseEngineBlock`, `ShipWindowBlock`, `ShipAirlockBlock`, `ShipSeatBlock`.
- `com.spacehorizons.client.ship`: `ShipRenderClient` (estado interpolado), `ShipSkyClient` (céu virtual e mixin de matriz), `ShipModelRenderer` (outras naves), `ShipPilot` (adaptador de `RocketPilot`), `ShipPanel`.
- `data/spacehorizons/dimension_type/shipyard.json`, `dimension/shipyard.json` e entrada no *world preset*.
- Testes: `src/test/java/com/spacehorizons/ship/*` e `client/devtest/ShipDevtest.java`.
- Reutilizados sem mudança conceitual: `space.flight.*` (inteiro), `rocket.RocketAssembler`/`RocketPortrait`, `RocketFuelTankBlockEntity`, `AttitudeControl`, `ChunkGate` (descida), `RocketKeys`/`RocketPilot`/`RocketPanelModel`, `SkyObserver`, `BodyRenderer`, `SpaceSun`/`SpaceSkyClient`, `PortraitMesh`/`RocketMeshCache`.

## 9. Decisões em aberto (para o usuário)

- **D1**: dimensão nova `shipyard` (recomendada) ou aumentar a altura de `space`? (afeta mundos existentes).
- **D2**: tamanho máximo da nave (proposta 128×128×256 blocos e até 8.192 blocos).
- **D3**: a nave começa já no estaleiro por criativo (fase 1) ou obrigamos montar no chão antes?
- **D4**: tripulação sem assento na descida: forçar assento, recusar a descida, ou presa no lugar?
- **D5**: naves grandes decolam como entidade (exige retrato esparso) ou só se constroem no estaleiro e só descem?
- **D6**: pouso de barriga permitido?
- **D7**: batida de nave grande: o que acontece com os blocos?
- **D8**: luz do sol nas janelas e `skylight` na nave.
- **D9 (decidida em 19/10/2026)**: gravidade interna sempre ligada na fase 1; o bloco gerador de gravidade (sem ele,
  gravidade zero) entra depois como opção.
- **D10**: aceita-se o hitch de ~1 s na conversão entidade → blocos na fase 2 (versus lote em vários ticks)? **S3 mediu: em lotes não há hitch (8.000 blocos em 0,6 a 1 s sem sair da faixa normal do tick); recomendação: lotes.**
