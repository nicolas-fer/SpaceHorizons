# Plano: LODs do planeta inteiro com gerador próprio (v1)

Etapa futura de polimento visual e de desempenho, decidida em 19/10/2026 depois da Terra 10× maior. Vem depois da
reentrada com pré-carga do local de pouso e da Lua física, porque estas podem já resolver o que se sente no voo.

## Problema

A Terra tem ~650 milhões de chunks (volta de 655.360 × ~255.000 blocos até 70° de latitude). Hoje o DH gera os LODs da
Terra pelo gerador vanilla completo (`DhIntegration.usesLodOverride` só liga o gerador próprio para planetas que não são
estilo Terra, ou seja, a Lua). Consequências:

- Voando ou em órbita baixa (1.000 a 5.000 de altitude) o horizonte fica incompleto enquanto o DH gera, e a geração do
  DH disputa CPU com o servidor durante o voo do foguete.
- Gerar tudo de verdade (pré-gerador ou gerador do DH) custaria ~1 a 3 dias de CPU e de 1 a 5 TB em disco: inviável.

## Ideia

Gerador híbrido para a Terra: os níveis de detalhe grossos do DH (colunas de 2^detail blocos, a partir de ~2 a 4 km da
câmera) saem do terreno analítico ou do mapa do planeta (`planet/map/PlanetMapTerrain`, `PlanetTerrains`), sem gerar
chunks; perto do jogador o DH continua usando o gerador vanilla com detalhe de blocos.

Estimativa de custo (pela taxa do bake do mapa, ~11.700 amostras/s): planeta inteiro com célula de 128 blocos em ~15 min,
64 em ~1 h, 32 em ~4 h; de 1 a 10 GB. Na prática não é preciso gerar tudo de antemão: o DH pede sob demanda e cada pedido
grosso fica barato.

## Base que já existe

- `dh/PlanetLodGenerator`: implementa `IDhApiWorldGenerator` com `API_DATA_SOURCES`, monta colunas (pedra, subsolo, topo,
  água, ar com luz do céu) a partir de `PlanetTerrain` e `SurfacePalette`. Usado na Lua.
- `dh/DhIntegration`: registra o override por level no evento de carga do DH.
- `planet/map/PlanetMap`: mapa global de alturas, cores e tipos (2048×798 nós, célula de 320 blocos, bake de 139 s em
  cache). Mesmo dado que a malha orbital usa.

## Incógnitas (spike antes de comprometer)

1. **Misturar fontes por nível de detalhe:** o override do DH vale para o level inteiro e tem um tipo de retorno só. É
   preciso descobrir na API 7.2.0 / DH 3.3.4 se dá para:
   - delegar ao gerador vanilla nos níveis finos (por exemplo `getSmallestDataDetailLevel`/`getLargestDataDetailLevel`,
     ou devolver "não sei" para o DH cair no gerador padrão); ou
   - gerar os níveis finos nós mesmos chamando o gerador de chunks do servidor fora do tick (proto-chunk até SURFACE); ou
   - aceitar a fonte grossa só além de um raio, deixando os chunks reais carregados cobrirem o perto.
2. **Fidelidade:** o terreno da Terra é o do Overworld vanilla com a costura periódica em X; a amostra grossa precisa
   bater com o vanilla de perto (altura e cor) para a transição não aparecer. Medir o erro de altura do `PlanetMapTerrain`
   contra chunks reais em alguns biomas (montanhas, oceanos, rios).
3. **Transição** entre o LOD grosso aproximado e o LOD real: DH substitui sozinho quando o dado fino chega? Precisa
   invalidar/regerar?
4. **Resolução do mapa:** 320 blocos pode ser grosso para os níveis médios; talvez amostrar o ruído direto (mais caro) nos
   níveis 6 a 8 e usar o mapa só nos maiores.

## Etapas

1. **Spike (S1):** protótipo do override na Terra só com níveis ≥ um limiar, medir o que o DH faz nos níveis finos;
   decidir a estratégia da incógnita 1. Critério: LOD distante aparece sem chunks gerados e o perto continua vanilla.
2. **Gerador híbrido:** `PlanetLodGenerator` aceita a Terra com fonte grossa (mapa/ruído) e fina (vanilla); limiar
   configurável.
3. **Fidelidade e transição:** medir o erro de altura e cor, ajustar paleta e suavização; sem degrau visível na troca.
4. **Prioridade durante o voo:** com o foguete rápido, pedir só níveis grossos à frente da trajetória; ligar com a
   pausa da geração do DH durante o voo (feita na etapa da reentrada).

## Verificação

- Autoteste novo `lod`: subir a 1.000, 2.500 e 4.900 de altitude em pontos nunca visitados, print após poucos segundos;
  o horizonte deve estar completo (comparar com o estado atual).
- Medir tempo de geração por pedido grosso, uso de disco do DH e tick do servidor durante o voo do foguete (sem
  "Can't keep up").
- Conferir a costura em X (±327.680) e a calota polar.

## Riscos

- A API do DH pode não permitir a mistura por nível; o plano B é gerar os níveis finos nós mesmos (mais trabalho).
- Diferença visível entre o LOD aproximado e o terreno real em biomas com relevo forte.
- Mudanças de versão do DH quebrarem as premissas (já anotadas em `PlanetLodGenerator`).

## Nota: detalhe procedural na malha orbital (polimento)

Antes do gerador híbrido acima, a vista da Terra a 1.000 a 10.000 de altitude (malha orbital abaixo de 5.000, esfera do corpo acima) foi
melhorada só nos shaders: ruído 3D fixo no corpo sobre a cor do mapa de 320 blocos e costa irregular (ver o README, "Polimento"). Não
traz dado novo; o mapa de 320 blocos continua sendo a fonte de longe, e a incógnita 4 (resolução do mapa) segue em aberto para o DH.
