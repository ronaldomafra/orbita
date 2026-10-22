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
- JDK 17 no sistema; JDK 21 (Temurin) em `~/.local/share/rea/jdk-21.0.13+9` (usado pelo REA)
- Ghidra 12.1.4: `~/.local/share/rea/ghidra_12.1.4_PUBLIC`
- Dispositivo: Galaxy A10 (SM-A105M, serial RQ8M708HWEZ), Android 11, armeabi-v7a; aparelho de testes do usuário
- JADX headless: `~/.local/share/rea/android/jadx-headless-mcp-0.7.1-all.jar`
