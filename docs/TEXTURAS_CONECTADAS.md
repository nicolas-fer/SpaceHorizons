# Texturas conectadas (vidro contínuo, tanques multibloco)

Mecanismo próprio do mod, sem mod externo, para blocos cuja moldura some nas faces que encostam em outro bloco "do mesmo grupo".
Hoje o usa `ship_window` (parede de vidro contínua, moldura só no contorno). Foi pensado para os tanques multibloco (colunas
retangulares) do próximo trabalho (`docs/PLANO_TIERS.md`).

## Como funciona

1. **Estado do bloco**: seis booleanos `north/east/south/west/up/down` (`BlockStateProperties`), "o vizinho desse lado se conecta".
   `content/connected/ConnectedFaces` tem tudo que o bloco precisa: declarar as propriedades, calcular na colocação
   (`getStateForPlacement`), atualizar quando um vizinho muda (`updateShape`) e uma regra de conexão (`Rule`, padrão
   `sameBlock()`).
2. **Aparência**: o blockstate é um `multipart`. Cada uma das 6 faces é UM quadrado, com uma de 16 texturas conforme quais das 4 bordas
   da face estão "abertas" (encostam num vizinho conectado). Um canto só abre se as duas bordas que o tocam abrem. Tudo gerado por
   `tools/gen_connected_block.py` a partir da textura que já existe.
3. **Sem PNG novo à mão**: as 16 variantes (`textures/block/<nome>_ct/t00..t15.png`) são montadas por recorte da própria textura
   (moldura onde a borda está fechada, o interior da textura repetido onde está aberta). Não as edite: regere com o script.
   Se a arte do bloco mudar, rode o script de novo.

Por que uma textura por combinação e não "pedaços" por face: a primeira versão fatiava cada face em 9 pedaços (modelos só com UV, sem PNG
derivado). Funciona, mas o vidro é translúcido e os pedaços se encostam com um pixel de sobreposição, o que deixava uma grade fina e
escura em cada emenda (visto nos prints). Um quadrado por face não tem emenda.

## Como reutilizar num tanque (ou outro bloco)

1. No bloco (qualquer superclasse), copie o padrão de `ShipWindowBlock`:
   ```java
   private static final ConnectedFaces.Rule RULE = ConnectedFaces.sameBlock();   // ou uma regra própria
   // construtor
   registerDefaultState(ConnectedFaces.disconnected(stateDefinition.any()));
   // createBlockStateDefinition
   ConnectedFaces.addProperties(builder);
   // getStateForPlacement
   return ConnectedFaces.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), RULE);
   // updateShape
   return ConnectedFaces.update(state, directionToNeighbour, neighbourState, RULE);
   ```
   Se o bloco já tem outras propriedades (por exemplo `facing`), some as dele às seis; o `multipart` só olha as seis.
2. **Regra de conexão**. Para tanques que formam colunas, use uma regra que só liga na vertical:
   `(self, neighbor, side) -> neighbor.is(self.getBlock()) && side.getAxis() == Direction.Axis.Y`. Para blocos de vários tipos que
   formam um só grupo (tanque e válvula), use uma tag: `neighbor.is(ModTags.TANK_PARTS)`. Para um multibloco retangular, a regra
   é a de sempre (mesmo bloco) e o formato retangular sai sozinho: bordas abertas só entre vizinhos.
3. **Gere o blockstate e os modelos**:
   ```
   python tools/gen_connected_block.py --name rocket_fuel_tank --texture spacehorizons:block/rocket_fuel_tank_side \
       --texture-end spacehorizons:block/rocket_fuel_tank_top --frame 2 --render-type solid
   ```
   `--texture` vale para as 4 faces laterais e `--texture-end` (opcional) para cima e para baixo; as duas devem ser 16x16 com a
   moldura de `--frame` pixels em volta. O script sobrescreve `blockstates/<nome>.json`: se o bloco já tinha um blockstate à mão
   (tanque com `facing`, por exemplo), gere com outro `--name` e una as partes. Antes de gerar, a regra de conexão decide quais
   propriedades ficam `true`; o script não precisa saber dela.
4. **Quem escreve blocos por código** (construtores de nave, comandos) deve chamar `ConnectedFaces.connectAround(level, pos, bloco,
   regra)` depois de colocar, porque `setBlock` não calcula o estado do próprio bloco (`ShipBuilder.connectWindows` é o exemplo).
   Colocação do jogador e quebra de vizinhos já funcionam sozinhas (placement e `updateShape`).
5. O modelo de **item** continua um cubo normal escrito à mão (`models/block/<nome>.json`).

## O que fica de fora, de propósito

- Cantos internos por diagonal: o canto de uma face só olha as duas bordas que o tocam, não o bloco diagonal. Num recorte em L de
  vidro aparece um pontinho de moldura no canto côncavo. Corrigir exige 47 variantes por face (mais 3 propriedades por face).
- A regra de conexão é por lado do bloco, não por face "coplanar": uma janela ao lado de outra mais recuada liga mesmo assim.
- O estado guarda a conexão: um bloco colocado com `/setblock` aparece desconectado até um vizinho mudar ou o construtor chamar
  `connectAround`.

## Retrato do foguete e estaleiro

O estado inteiro (com as seis propriedades) viaja no retrato e nas conversões de e para o estaleiro (`RocketToYardJob`, `YardToRocketJob`
copiam o `BlockState`), então a aparência persiste. O retrato desenha o modelo do blockstate como qualquer outro bloco.
