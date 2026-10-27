# 1. O processo

Como o Órbita saiu do zero até um APK assinado num único dia (22/10/2026), com um desenvolvedor Android
sênior estreante em jogos trabalhando em par com um agente de IA (Claude Code) no terminal.

## Ponto de partida: validar o REA

O projeto nasceu como **teste do REA**, uma ferramenta de engenharia reversa exposta via MCP (Model Context
Protocol) para agentes de IA. Ela integra Ghidra, JADX, ADB e outras ferramentas atrás de uma API única.
Para validar o REA num caso real, usamos o jogo *Auralux: Constellations* como alvo de estudo: a pergunta era
se um agente, guiado pela ferramenta, conseguia entender as regras de um jogo comercial com engine nativa.

Conseguiu. O agente levantou as mecânicas gerais (produção, combate, captura, evolução de planetas, ritmo da
IA, loop de 60 Hz) e as confirmou observando o jogo em execução num aparelho de testes. Com isso, a pergunta
seguinte ficou natural: dá para usar esse conhecimento como base de um jogo **novo e original**?

**Regras que valeram o projeto todo**, escritas no `CLAUDE.md` e seguidas pelo agente:

- não modificar o APK original nem contornar DRM, compras ou licenças, e não usar root;
- não copiar código descompilado nem assets: o código novo nasce de uma especificação escrita por nós;
- material bruto de estudo fica fora do Git.

## As fases

O trabalho seguiu um plano com **gates**: pontos em que o desenvolvedor aprovava o resultado antes de avançar.

| Fase | O que foi feito | Gate |
|---|---|---|
| F0–F3 · Estudo | Ambiente do REA, triagem do APK, leitura das regras e experimentos no aparelho | — |
| F4 · Especificação | Regras R1–R9 escritas do zero, parâmetros, controles, IA e critérios de teste | **Gate 4**: spec aprovada |
| F5 · Spike técnico | Esqueleto Gradle multimódulo e 2.000–3.000 partículas no Galaxy A10, sem regra de jogo nenhuma | **Gate 5**: 60 fps e zero GC no aparelho |
| F6.1–6.2 · Simulação | Núcleo determinístico com 11 testes (T-01…T-11) | Hash idêntico JVM × Android |
| F6.3 · Partida | Render interpolado, laço de seleção, ordens, HUD | Jogável no A10 |
| F6.4–6.5 · IA e arena | Bots por pontuação, 3 personalidades, arena headless, overlay de debug | Bots expandem, atacam e defendem |
| Polimento | 3 rodadas de ajustes visuais e de jogabilidade (AJ-01…AJ-23) | Retorno do desenvolvedor jogando |
| Conteúdo | Menu de ajustes, 13 mapas, perfis dos bots no HUD | — |
| Entrega | APK release assinado (v1 + v2) para arm64, armv7 e x86_64 | — |

### Por que um spike antes do jogo

Para um dev Android acostumado com apps, a dúvida era a stack: Kotlin + libGDX aguenta milhares de unidades
num aparelho de entrada? O spike respondeu com números antes de qualquer regra existir:

- 2.000 partículas a 60 fps estáveis no Galaxy A10, sem nenhum GC durante a execução;
- a simulação era o gargalo (busca do inimigo mais próximo), não a renderização;
- a busca **escalonada** (cada unidade procura a cada 4 ticks) derrubou o pior caso de ~5 para ~3,2 ms/tick.

### Determinismo desde o primeiro teste

A simulação roda em ticks fixos de 60 Hz, com RNG próprio (splitmix64), `StrictMath` e ordem estável das
entidades. O teste T-10 calcula um hash do estado a cada segundo de jogo. Rodamos a mesma partida roteirizada
no desktop e no Android e o hash **bateu em todos os 60 checkpoints**. Isso permitiu manter a simulação em
`Float` (mais simples) em vez de migrar para ponto fixo, e abre caminho para replays e multiplayer em lockstep.

### O ciclo de polimento

Depois da partida jogável, o processo virou um ciclo curto:

1. o desenvolvedor jogava no aparelho e descrevia o que estava estranho ("as unidades atravessam planetas",
   "unidades paradas parecem mortas", "planetas opacos");
2. o agente registrava cada observação como um ajuste numerado (AJ-xx), com a proposta de implementação;
3. a mudança entrava com teste quando mexia na simulação (o hash de determinismo era conferido de novo) e com
   captura de tela tirada via ADB no aparelho real;
4. o desenvolvedor validava jogando.

Foram 23 ajustes em 3 rodadas. A evolução está em [Evolução visual](05-evolucao-visual.md).

### Problemas reais que apareceram

- **Queda para 15–36 fps** depois do primeiro polimento visual. Parecia limite de *fill rate* da GPU Mali, mas
  era o `SpriteBatch` descarregando o lote a cada troca de textura (~900 draw calls por frame). Juntar tudo
  num **atlas único** trouxe os 60 fps de volta.
- **"Com unidades demais o jogo trava"**: o teste de estresse chegou a 12.000 unidades sem crash, mas o
  investigamos a fundo e adicionamos um gravador de crash, um limite de ticks por frame (o jogo desacelera em
  vez de congelar) e um HUD sem alocação por frame.
- **Bug de escopo em Kotlin**: dentro de `Table().apply { }`, `skin` resolvia para a propriedade da própria
  `Table` (nula), não para a variável externa. Um clássico de `apply` que só apareceu em execução.

## Como o agente trabalhou

- **Terminal e aparelho real.** O agente compilava, instalava no Galaxy A10 via ADB, abria a partida com
  parâmetros de depuração (`--ei stress`, `--ez debugAi`, `--ef zoom`), tirava screenshots e lia o logcat.
- **Medir antes de otimizar.** Cada decisão de desempenho veio de uma medição: sonda por fase da simulação,
  benchmark headless e fps no aparelho.
- **Decisões registradas.** Cada escolha relevante (D1–D18) ficou anotada com o motivo, por exemplo
  "simulação em Float porque o hash bateu entre JVM e ART".
- **Commits pequenos** como checkpoints, um por funcionalidade.

## O que fica para depois

- IA: sincronizar a chegada de ataques vindos de várias fontes, prever a força necessária e ajustar os pesos
  automaticamente por self-play na arena (ver [IA dos bots](03-ia-dos-bots.md)).
- Áudio, tela inicial, campanha com progressão entre mapas.
- Nebulosa com posição própria em cada mapa.
