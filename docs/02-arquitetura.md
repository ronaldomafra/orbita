# 2. Arquitetura e simulação

## Módulos

| Módulo | Tipo | Responsabilidade |
|---|---|---|
| `sim` | Kotlin/JVM puro | Regras, estado, comandos, tick fixo, mapas, eventos |
| `ai` | Kotlin/JVM puro | Observação do estado, bots, personalidades |
| `game` | libGDX + KTX | Partida, render, câmera, input, HUD, ajustes, mapas |
| `android` | App Android | Launcher, gravador de crash, sonda de determinismo |
| `desktop` | LWJGL3 | Launcher, benchmark e sonda headless |
| `tools/arena` | CLI JVM | Partidas bot × bot em paralelo, verificação de vazamento |

Regra de dependência: **`game → ai → sim`**. Nem `sim` nem `ai` dependem de libGDX ou Android. Na prática:
as regras são testadas com JUnit puro, a arena roda partidas milhares de vezes mais rápido que o tempo real e a
mesma simulação roda idêntica no desktop e no celular.

## Simulação (`sim`)

### Regras

| Regra | Resumo |
|---|---|
| R1 Mundo | Ticks fixos de 60 Hz. O render interpola e nunca altera o estado |
| R2 Planeta | Dono, nível, saúde, crescimento e nível máximo definido pelo mapa |
| R3 Produção | A cada 128 ticks, cada planeta ativo gera 2 × nível unidades |
| R4 Unidade | Dono, power, posição, velocidade e ordem (ponto ou planeta) |
| R5 Combate | Unidades inimigas próximas se travam em pares; no choque, as duas perdem `min(a, b)` |
| R6 Chegada | Cura, evolui, ataca ou captura conforme o dono e o nível do planeta |
| R7 Fim | Facção sem planetas ativos e sem unidades é eliminada |
| R8 Ordens | `SendUnits(facção, centro, raio, alvo, maxPower)`: a mesma API para o jogador e para os bots |
| R9 Determinismo | Mesmo mapa + seed + comandos = mesmo estado, conferido por hash |

### Dados em SoA, sem alocação no tick

As unidades não são objetos. Ficam em arrays paralelos (`FloatArray` para x, y, vx, vy; `IntArray` para dono,
alvo e modo), com slots reaproveitados em ordem LIFO. Com milhares de unidades, isso elimina a pressão de GC
no Android: o spike mediu **zero coletas** durante a execução no Galaxy A10.

### Grade espacial por facção

Para achar o inimigo mais próximo, a cada tick a simulação monta uma grade uniforme **por facção**, com células
do tamanho do alcance de atração e só com unidades livres (as já travadas em combate ficam de fora). Cada
unidade procura nas 3 × 3 células à sua volta, nas grades das outras facções, e essa busca é escalonada a cada
4 ticks por unidade. As demais distâncias (choque, chegada, desvio de planetas) são calculadas direto, porque
envolvem poucos objetos.

### Determinismo

- RNG próprio (splitmix64) com seed da partida;
- `StrictMath` nas funções trigonométricas;
- ordem estável de entidades, sem iterar `HashMap` no tick;
- `stateHash()` e registro de comandos `(tick, comando)` para replay.

O hash da partida roteirizada é idêntico na JVM desktop e no ART do Android (ARMv7). Por isso a simulação
continua em `Float`, sem ponto fixo.

### Eventos

A simulação publica eventos (`CLASH`, `CAPTURE`, `LEVEL_UP`, `DESTROYED`, `DEATH`, `SPAWN`) que alimentam as
partículas e os pulsos do render. Eles ficam fora do estado e do hash, então os efeitos visuais não afetam o
determinismo.

## Render e controles (`game`)

- **Passo fixo com acumulador** e interpolação entre o tick anterior e o atual. No máximo 4 ticks por frame:
  sob carga extrema o jogo desacelera em vez de congelar.
- **Sem assets:** texturas de brilho, estrelas, nebulosa e superfícies dos planetas (ruído 3D com distorção)
  são geradas em código e reunidas em atlas. Um único atlas foi o que manteve 60 fps na GPU Mali.
- **Câmera 3D** (`CameraRig`): uma `PerspectiveCamera` sobre o plano do mapa. No zoom 1 a vista é de cima, e ao
  aproximar a câmera inclina até 42°. O que está *no plano* (fundo, trilhas de órbita, seleção) usa a
  perspectiva; o que está *em pé* (planetas, unidades, partículas) é projetado e desenhado em espaço de tela,
  sempre redondo e com a escala da profundidade. Toque e pan usam a interseção do raio da câmera com o plano.
- **Órbitas visuais:** a simulação é 2D. O número de planos de órbita igual ao nível do planeta, a inclinação
  e o rastro das unidades existem só no desenho.
- **Input** (`MatchInput`): laço circular de seleção, toque no próprio planeta, envio por toque, pan com mola
  elástica além das bordas e pinça para zoom.

## Ajustes (`game/settings`)

`Params` é um **registro único** de parâmetros (chave, rótulo, categoria, faixa, passo, padrão e tipo). A tela
de ajustes é gerada a partir dele com Scene2D, e os valores ficam salvos em Preferences. Para expor um novo
parâmetro basta declará-lo em `Params`: a tela já o mostra. Parâmetros de simulação e IA só valem ao reiniciar,
para não quebrar o determinismo no meio da partida.

## Mapas (`MapLibrary`)

13 mapas em código, todos num mundo de 1600 × 760 u. Os de 3 facções têm simetria de 120°, e um teste garante
que as bases ficam à mesma distância do centro (± 1%), que os planetas não se sobrepõem e que uma partida curta
só com bots captura neutros em todos eles.
