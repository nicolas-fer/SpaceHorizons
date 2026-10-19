# Plano das naves do SpaceHorizons (v1)

Base: `docs/PLANO_SOBREVIVENCIA.md`, `docs/DESIGN_FOGUETE.md` e `docs/pesquisa/foguete-4-dimensao-espaco.md`. Este plano estende o foguete de blocos para **naves grandes, andáveis por dentro**, que operam no espaço. Nada aqui foi implementado; os números de escala (Terra R ≈ 104.303, Lua R ≈ 26.076 a 600.000 de distância) vêm da escala 10× em andamento.

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
- Limite do pacote de spawn (~1 MiB): o `RocketPortrait` denso não escala para 8.192 blocos com block entities. Para naves grandes o retrato vai **esparso** (só blocos não ar, por seção) e as block entities **não** vão no spawn (só no save); cliente renderiza só o necessário (ver 4.3). **Spike S3.**
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

### 4.3 Outras naves e foguetes

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
3. **Pacote/ NBT de nave grande** na transição entidade↔blocos (limite de 1 MiB). S3.
4. **Perda de itens/entidades** na conversão (mesma classe de bug dos trens do Create).
5. **Servidor dedicado**: tickets de chunk e salvamento do `ShipRegistry` (nunca testado).
6. **Mundos existentes**: precisa do world preset novo; documentar migração (D1).
7. **Escala**: pesquisas anteriores definem altitude de saída 5.000 (Terra); com a escala 10× e raio 104.303, rever `Zones.exitAltitude`, SOI e `TransferPlanner` (fora do escopo deste plano, mas a nave herda).
8. **Jogador desconectado dentro de nave**: ao reconectar na fase `SPACE_YARD` precisa reentrar no estaleiro; se a nave pousou/foi destruída enquanto ele estava fora, reposicionar para `ShipRecord.lastSafePos`.
9. **Duas naves atracadas**: fase 3 (porta de hangar com o foguete): exige que o foguete vire "módulo" da nave (unir regiões).

### Spikes antes de comprometer (nesta ordem)
- **S1 (céu virtual):** na dimensão `space` atual, um comando `/spacehorizons skytest <yaw> <pitch> <roll>` fixa uma atitude falsa e gira sol/estrelas/Terra/Lua; critério: Terra e sol giram juntos de forma coerente, sem tremor, sem salto na troca de dimensão. Sem isso, o caminho C não existe.
- **S2 (estaleiro):** criar a dimensão `shipyard` e uma nave de 64×64×64 colada por comando em duas células distantes; jogar, andar, acender luz, fechar e abrir o mundo; checar chunks, tickets, luz, desempenho de 2 naves.
- **S3 (conversão):** `RocketEntity` com retrato de ~4.000 blocos ↔ blocos no `shipyard`: inventários intactos, NBT íntegro, tempo da conversão, tamanho do pacote.

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
- **D10**: aceita-se o hitch de ~1 s na conversão entidade → blocos na fase 2 (versus lote em vários ticks)?
