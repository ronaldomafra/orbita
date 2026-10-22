# Projeto Órbita

Jogo original de estratégia espacial inspirado nas mecânicas de Auralux 2 (`com.wardrumstudios.auralux2`).
Plano vigente: `doc/Plano_Projeto_Orbita_v2.md` (a v1 fica só como histórico).

## Regras do agente

- Investigação via REA (MCP). Use apenas as operações e os parâmetros anunciados pelo servidor instalado; não invente comandos.
- NÃO modificar o APK original, NÃO contornar DRM/compras/licenças, NÃO usar root.
- NÃO copiar código descompilado nem assets para o jogo. O código novo nasce da especificação aprovada em `docs/`.
- Binários e saídas brutas ficam em `evidence/` (fora do Git). Em `docs/` vão só os dossiês resumidos.
- Toda afirmação sobre o jogo de referência registra: fonte, método, classe/offset/hash, observação × hipótese, confiança e lacunas (formato `MECH-xxx`).
- Código do jogo só depois do Gate 4 (especificação aprovada), exceto o spike técnico da F5, que não contém regras do jogo.
- Git local apenas, para checkpoints. Não há remoto.

## Arquitetura alvo

`game → ai → sim`. `sim` e `ai` são Kotlin/JVM puro (sem libGDX/Android); `game` usa libGDX + KTX; launchers `android/` e `desktop/`; `tools/arena/` para partidas headless.

## Ambiente

- ADB: `~/Android/Sdk/platform-tools/adb` (fora do PATH)
- JDK 17 instalado; JDK 21 só se o Ghidra for necessário
- JADX headless: `~/.local/share/rea/android/jadx-headless-mcp-0.7.1-all.jar`
