# Plano de sobrevivência do SpaceHorizons (v3)

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
