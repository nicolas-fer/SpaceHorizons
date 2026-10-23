# Plano de sobrevivência do SpaceHorizons (v3)

> **Progressão por tiers** (20/10/2026): este plano é o **Tier 1** de `docs/PLANO_TIERS.md` (foguetes reais, Lua e Marte).
> Marte e a separação de estágios (etapa 6) seguem lá; tanques e motores viram multiblocos e a montagem ganha guias
> (painel de projeto e plantas holográficas), também descritos lá.

## Decisões

- História média: diários e terminais em estruturas abandonadas, contada também por conquistas.
- Dificuldade equilibrada: umas horas de jogo até a Lua. Escala atual: Terra R ≈ 104.304 (10x a original), Lua a 600.000, velocidade orbital 91 b/t, Isp 1.600 (ve 128 b/t); TLI de ~19 min e Δv de 26,6 (ver README, "Escala").
- Foguete construído com blocos livres que sobe inteiro.
- Voo pilotável estilo "KSP-lite": gravidade real simples do corpo dominante e níveis de ajuda (assistido, normal, manual).
- Um estágio só na primeira versão; separação de estágios depois.
- O piloto automático atual vira um módulo de computador de bordo desbloqueável. `/spacehorizons fly|goto` ficam para criativo e testes.

## Três zonas de voo

1. **Atmosfera**: na dimensão do planeta, em velocidade baixa (decolagem, reentrada).
2. **Espaço**: dimensão vazia (sem chunks a gerar), a partir de ~5.000 blocos de altitude. Terra e Lua desenhadas pela malha orbital e pelo renderizador de corpos.
3. **Aproximação e pouso**: de volta na dimensão do corpo de destino.

As passagens usam a transferência sem tela de carregamento que já existe e a troca de corpo pela esfera de influência.

## Etapas

| # | Etapa | Tamanho | Conteúdo |
|---|-------|---------|----------|
| 0 | Fundação | P | Registro de itens, blocos e receitas; texturas provisórias geradas por código; sistema de vácuo e oxigênio. |
| 1 | Materiais e combustível | M | Aço, circuitos, placas, combustível, refinaria simples; conquistas guiando a progressão. |
| 2 | Traje e oxigênio | M | Capacete, peitoral e tanque que gastam e recarregam; dano e frio no vácuo sem traje. |
| 3a | Foguete de blocos | G | Montagem, retrato virando entidade, pouso de volta a blocos (detalhado abaixo). |
| 3b | Voo pilotável | G | Comandos do cliente, simulação no servidor, gravidade + empuxo, SAS, painel (velocidade, altitude, apoastro/periastro, "queime agora"), níveis de ajuda. |
| 3c | Espaço, órbitas e pouso | G | Dimensão de espaço, passagens entre zonas, pouso na Lua com freio, reentrada leve com paraquedas. |
| 4 | Base e retorno | M | Salas seladas com oxigênio, gerador, energia simples, recursos lunares (gelo, hélio-3, titânio), combustível produzido na Lua. |
| 5 | História | M | Estruturas abandonadas na Lua com diários e terminais; trama do sinal lunar. |
| 6 | Novos mundos e estágios | M a G | Terceiro corpo (Marte), separação de estágios, balanceamento. |

**Marco jogável:** fim da 3c, ir à Lua e voltar pilotando em sobrevivência.

**Ordem:** 0 → 1 → 2 → 3a → 3b → 3c → 4 → 5 → 6, mas a 3a e a 3c começam por protótipos técnicos antes do conteúdo.

## Etapa 3a em detalhe (base: como o Create faz "contraptions")

Referência lida: Create, branch `mc1.21.1/dev` (commit `7ff2760`). Não existe Create para 26.x; os nomes de render são da 1.21.1 e precisam ser traduzidos para o Blaze3D do 26.3.

### Montagem
- Busca em largura a partir do cockpit, com conjunto de visitados e fila única. Regra única: entra tudo que está ligado por face e pertence à tag de blocos do foguete (sem super cola, chassi ou `canStickTo`).
- Blocos presos que precisam de apoio (escada, tocha) entram junto com o bloco de apoio ou a montagem é recusada.
- Limites: máximo de blocos (proposta: 1.024; o Create usa 2.048) e máximo de iterações. Blocos inamovíveis (bedrock, dureza -1, tag própria) recusam a montagem.
- Cada bloco vira `posição local → StructureBlockInfo(pos, BlockState, NBT)`; NBT de block entity por `saveWithFullMetadata` sem x/y/z.
- **Validar tudo antes de remover qualquer bloco** (chunks carregados, tamanho, tamanho do pacote). Erros tipados e explicados ao jogador.

### Remoção dos blocos
- Dois passes: primeiro os blocos presos, depois os sólidos.
- `removeBlockEntity` e `setBlock(AIR)` com `UPDATE_MOVE_BY_PISTON | UPDATE_SUPPRESS_DROPS | UPDATE_KNOWN_SHAPE | UPDATE_CLIENTS | UPDATE_IMMEDIATE` (sem atualizar vizinhos).
- Um passe final único de `markAndNotifyBlock` depois que tudo virou ar.

### Entidade
- Entidade própria com spawn complexo: retrato em NBT com paleta (`Pos` como long, `State` como índice). No pacote de spawn vai o `getUpdateTag` dos block entities; no save vai o NBT completo.
- Limite de tamanho do pacote (~1 MiB): foguete grande demais é recusado na montagem, com erro claro (o Create deixa a entidade sumir no cliente).
- Hitbox derivada dos limites da estrutura.
- Diferente do Create: a rotação é pilotada, então yaw/pitch/roll são sincronizados explicitamente.
- O retrato inteiro fica no save da entidade: se o servidor cair em voo, o foguete não se perde.

### Renderização
- Mundo virtual leve (`BlockAndTintGetter`) com os blocos locais; malha montada **uma vez** por camada de render e guardada.
- Por frame, só a matriz da entidade (posição e rotação, com interpolação).
- Luz amostrada do mundo real na posição do foguete (no espaço, luz do sol pelo SunModel).
- Block entities com renderer próprio (baús, placas) desenhados à parte.
- Sem Flywheel: copiar só o caminho de fallback do Create, reimplementado na API de render do 26.3.
- A entidade continua visível alguns ticks depois do pouso, para não piscar enquanto os blocos reaparecem.

### Colisão
- Sem o colisor de entidades do Create: o jogador vai sentado.
- Outras entidades não empurram o foguete.
- Checagem simples de caixa contra o terreno na direção do movimento, para detectar batida e pouso.

### Assento e controles
- O jogador monta direto na entidade do foguete (sem entidade de assento), posicionado pelo bloco do cockpit.
- O cliente manda as teclas pilotando por um pacote próprio (acelerador, giro, SAS); o servidor aplica. Frequência maior que a do trem do Create (5 ticks), porque é voo.
- Posição de desembarque calculada e guardada.

### Pouso (desmontagem)
- Yaw arredondado a 90°, só rotação em Y, `state.rotate`.
- Dois passes na ordem inversa (sólidos primeiro, presos depois), NBT reposto com x/y/z do destino, flag contra execução dupla.
- Diferente do Create, que destrói o que estiver no lugar: **pré-verificar o espaço**; se ocupado, só substituir blocos substituíveis (grama, neve) ou recusar o pouso.
- Registrar no log qualquer bloco perdido.

### Troca de dimensão
- Estratégia do trem do Create: serializar a entidade, tirar o jogador do assento, criar a entidade nova na dimensão de destino, teleportar o jogador (sem tela de carregamento, pelo sistema atual) e re-sentá-lo quando estiver na mesma dimensão, só então descartar a antiga.
- Carregar o chunk de destino antes (ticket).
- Todo o estado necessário depois da travessia fica no NBT.

### Protótipos da 3a, em ordem
1. Montar, virar entidade e pousar no mesmo lugar, sem voar.
2. Subir e descer na vertical com o jogador sentado.
3. Atravessar para outra dimensão e voltar.

Cada protótipo com cenário próprio no autoteste (`-PshOnly=`) e prints.

## Etapa 5: a trama do sinal lunar (feita)

Código em `com.spacehorizons.story` (cliente: `client/story`), teste `StoryTest`, autoteste `-PshOnly=story` (`StoryDevtest`). História média: nove registros, contados por diários (item) e terminais (bloco) e acompanhados por seis conquistas.

### A história

Há anos o **Observatório Aurora** capta um pulso na faixa do hidrogênio (1.420 MHz) que vem da face da Lua voltada para a Terra, a cada 7 min 12 s. A astrônoma **Helena Voss** e o engenheiro **Tomás Reyes** descobrem que o pulso carrega um mapa de cinco pontos, não conseguem verba e constroem eles mesmos um foguete. Na Lua montam a **Base Selene-1**; o ar acaba antes do combustível (Reyes errou a conta), Voss segue a pé para o primeiro ponto do mapa e acha a **sonda Vigia-3**, caída, que não origina o sinal: só o retransmite. A telemetria dela diz que o feixe vem de **Marte**; Voss deixa um recado final e parte para lá. O jogador percorre o caminho ao contrário: Terra, base, sonda, e fica com o gancho do Tier 2.

| # | Registro | Onde | Tipo | O que diz / entrega |
|---|---|---|---|---|
| 1 | `voss_01` "Dia 1: o pulso" | Observatório (Terra) | diário | O pulso, a cadência, a direção: a Lua |
| 2 | `voss_02` "Dia 9: o mapa" | Observatório | diário | O mapa de cinco pontos; sem verba, vão eles |
| 3 | `aurora_terminal` | Observatório | terminal | **Planta orbital** + **bússola da base lunar** |
| 4 | `reyes_01` "Dia 14: Selene-1" | Base Selene-1 (Lua) | diário | Chegada, a base em onze dias |
| 5 | `reyes_02` "Dia 31: o ar" | Base | diário | O ar acabou; o sinal NÃO vem da base, parece destroço |
| 6 | `selene_terminal` | Base | terminal | **Planta lunar** (destrava no menu) + **bússola da sonda** |
| 7 | `voss_03` "Dia 44: o eco" | Sonda Vigia-3 (Lua) | diário | A sonda só repete um eco |
| 8 | `vigia_terminal` | Sonda | terminal | O feixe vem de Marte (x 3000, z -1500) + **bússola de Marte** |
| 9 | `mars_hook` "Para quem vier depois" | Sonda | diário | Gancho do Tier 2: motores, combustíveis e materiais de outro mundo |

Conquistas (corrente sob `root`, gatilho próprio `spacehorizons:story_entry` com o id do registro): `story_observatory` (ler `voss_01`) → `story_coordinates` (terminal da Terra) → `story_selene` (ler `reyes_01`) → `story_lunar_plans` (terminal da base) → `story_probe` (terminal da sonda) → `story_mars` (ler `mars_hook`).

### Peças

- **Item `journal_entry`**: um livro por registro; componente `spacehorizons:journal_entry` (string, o id do registro). Clique direito abre `StoryScreen` (texto do lang, `story.spacehorizons.<id>.title|author|body`, pt-BR e en_us); no servidor marca como lido e dispara a conquista.
- **Bloco `data_terminal`**: o estado `record` (`aurora`, `selene`, `vigia`) diz o registro (sem block entity: o gerador o desenha direto e o loot table copia o estado). Clique abre a tela com o texto e a lista de anexos; a PRIMEIRA vez de cada jogador entrega a recompensa (`StoryProgress.claim`, regra pura testada), as seguintes só mostram o registro.
- **Progresso**: Data Attachment `spacehorizons:story_progress` (lidos e levados), salvo, copiado na morte e sincronizado com o próprio cliente.
- **Planta lunar**: o terminal da base entrega o item `rocket_blueprint` com `Blueprints.LUNAR` e, ao levar o anexo, o menu das plantas (`BlueprintScreen` e `BlueprintChoicePayload`, servidor) deixa de trancá-la em sobrevivência (`StoryProgress.unlocksBlueprint`). No criativo continua livre.
- **Bússolas**: bússolas do vanilla com `lodestone_tracker` sem ímã (apontam para uma posição de OUTRA dimensão: só funcionam nela). Terra → Lua: base; base → sonda; sonda → Marte (3000, -1500).
- **Comandos de dev** (`/spacehorizons story`): `where|goto earth_station|moon_base|moon_probe`, `give <diário>`, `progress`, `reset`.

### Lugares e colocação (blocos do vanilla e do mod, sem NBT)

Desenhos em `SiteLayouts` como dado puro (`SiteLayout`): caixas, "desgaste" sorteado por posição (determinístico, então um chunk não precisa dos vizinhos), baús com tabelas de saque `story/<lugar>` (os diários) e o terminal. `StoryStamper` desenha só dentro do chunk em geração; abaixo da laje preenche fundação (até 24 blocos) e acima limpa árvores e rocha.

- **Observatório Aurora** (Terra, 17 x 15): salão de tijolos rachados com teto furado, vidros quebrados, estantes, teias, terminal no fundo, baú com `voss_01` e `voss_02`; antena parabólica de cobre oxidado e mastro. **Estrutura do vanilla**: tipo `spacehorizons:story_site` (`StoryStructure` + `StoryPiece`), `structure_set` de espalhamento aleatório (espaçamento 32 chunks, separação 12: cerca de uma por 512 x 512 blocos), biomas de terra firme (`tags/worldgen/biome/has_story_station`). A estrutura só nasce onde o relevo sob a pegada varia até 10 blocos e acima do mar; a laje fica na média. O gerador da Terra já suprime estruturas a 640 blocos da costura em X. **Só com "gerar estruturas" ligado** no mundo (o vanilla pula estruturas se estiver desligado).
- **Base Selene-1** (Lua, 23 x 17): módulo de controle de casco de foguete (terminal, baú com `reyes_01` e `reyes_02`, fornalha, estante), corredor, módulo de laboratório rompido e a plataforma de lançamento onde a expedição pousou, com restos do foguete.
- **Destroços da sonda Vigia-3** (Lua, 17 x 17): pátio chamuscado, fragmento de casco (terminal, baú com `voss_03` e `mars_hook`, RTG) e o prato da antena caído.
- **Placement próprio na Lua**: o gerador paramétrico da Lua não usa estruturas do vanilla, e os terminais entregam coordenadas e bússolas, então a base e a sonda têm posição FIXA, igual em todo mundo e **independente de "gerar estruturas"**. `StorySites` parte de um ponto nominal perto do ponto da Lua voltado para a Terra (x -81.919, z 0,5, sobre a costura; a base fica ao leste, a ~560 blocos, fora do alcance dos chunks espelhados; a sonda ~590 blocos adiante) e escolhe, entre os chunks vizinhos (±6), o de relevo mais plano sob a pegada (função analítica de relevo da Lua, a mesma dos chunks); a laje fica na média das alturas. `PlanetChunkGenerator.applyBiomeDecoration` chama `StoryWorldgen.stampMoon` depois da decoração e desenha o pedaço de cada lugar que cai no chunk. Vale para chunks gerados DEPOIS desta versão (mundos antigos não ganham lugares nos chunks já gerados).
- **Marte**: o gancho é só texto e a bússola; nada é gerado em Marte (etapa do sistema solar).

### Limites

Estrutura da Terra depende de "gerar estruturas" e de achar terra firme plana (comando `story where earth_station` mostra onde; a conquista dá a pista "antena parabólica enferrujada"); os registros 1 a 9 não têm som, voz nem animação; arte provisória (terminal: tela da ponte recolorida; diário: livro recolorido, `tools/derive_story_art.py`); pouso de foguete sobre a plataforma da base não testado.

## Verificação

- Testes unitários para a lógica pura: órbita (apoastro e periastro), massa e empuxo vindos dos blocos, volume selado, montagem e rotação do retrato.
- Autoteste com cenários de lançamento, órbita, transferência, pouso e retorno.
- Ciclo montar → voar → pousar testado com cada tipo de block entity (baú, placa, inventários), porque é onde o Create teve perda de dados.
- Jogar sempre em sobrevivência.

## Riscos

- Retrato de blocos virando entidade (render no Blaze3D do 26.3, troca de dimensão). Plano B: foguete de tamanho fixo por módulos.
- Sensação de controle em multiplayer por latência; primeira versão para um jogador.
- Desenhar Terra e Lua de dentro da dimensão de espaço (o renderizador de corpos hoje assume que você está num planeta).
- Pendências antigas: servidor dedicado, Sodium e Iris, arte final.
