# 10 — Roadmap de funcionalidades

Estado atual da UI: web (WebView + kof serve), abas, explorer, editor com
highlight, menu, temas, problemas, atalhos, pastas recentes, templates.

## Implementado (checklist vivo)

- [x] Abas com X, dirty ●, Ctrl+W, Ctrl+Tab
- [x] Explorer em árvore (expandir, abrir, abrir na pasta, refresh preservando estado)
- [x] Editor com highlight (lexer Kof), gutter, linha atual
- [x] Temas claro/escuro (tema do Kof), exportar/importar YAML (legado Swing)
- [x] Problemas com linha/coluna + navegação (Ctrl+Shift+M)
- [x] Quick Open (Ctrl+P), símbolos (Ctrl+Shift+O), renomear (Ctrl+F2), definição (F12/Ctrl+Click)
- [x] Autocomplete (Ctrl+Space) com keywords + símbolos + snippets
- [x] Formatar documento (Shift+Alt+F) em Kof
- [x] Buscar no arquivo (Ctrl+F/F3), buscar na pasta
- [x] Mover/duplicar linha, comentar (Ctrl+/), indentar seleção, auto-close, auto-indent
- [x] Zoom, wrap, salvar tudo, fechar todos, reabrir aba (Ctrl+Shift+T)
- [x] Pastas recentes, novo em branco, **novo com template (9 linguagens, licença + usuário/máquina)**
- [x] Git: status, commit, diff (host-side — processos pendentes no Kof)
- [x] Terminal integrado (Ctrl+`) — executa via shell do host
- [x] Terminal com comandos TUI (htop/top/vim/less/...) — `script -qec` + `cleanTty` em Kof (ESC/CR limpos)
- [x] "Abrir pasta..." com diálogo nativo — `/api/folderpick` (zenity) em vez de `prompt()`
- [x] Botão "abrir" da sidebar: input vazio → diálogo nativo (não cai mais no cwd do serve)
- [x] Ícone do app (kof.png) na janela (webview recompilado) e na tela de loading
- [x] Content-Type correto no `kof serve` (html/css/js) — página renderiza no navegador

## Faltando (ordem sugerida)

| # | Feature | Bloqueio | Notas |
|---|---------|----------|-------|
| 1 | **Multi-caret / seleção em bloco** (Alt+Click, Ctrl+Alt+↑↓, Ctrl+Shift+L) | editor de texto web do KofJS (DOM) ou overlay próprio | o textarea tem 1 caret |
| 2 | **Folding (recolher blocos)** | renderer custom (pre não sabe de linhas) | possível com overlay por linha |
| 3 | **Paleta de comandos web** (Ctrl+Shift+P) com filtro | — | substitui o prompt atual |
| 4 | **Buscar na pasta com resultados clicáveis** | — | endpoint /api/search existe (searchDir) |
| 5 | **Painel de busca lateral (Ctrl+Shift+F)** | — | |
| 6 | ~~**Terminal nativo em Kof** (processos)~~ — destravado | **✅ F4 resolvido** (`process.run`) | migrar do shell do host para `process.run` em Kof |
| 7 | ~~**Git nativo em Kof**~~ — destravado | **✅ F4 resolvido** (`process.run`) | migrar do host-side para `process.run` |
| 8 | **Run/Debug com breakpoints** — destravado parcialmente | debugger MVP do Kof chegou (Fases 1-3): `kof debug` DAP sobre stdio, breakpoints por linha Kof + call stack (JVM); locals/stepping = Fase 7 | integrar o DAP ao painel do editor |
| 9 | ~~**Editor web 100% KofJS** (sem servidor)~~ — **RESOLVIDO (verificado 0.0.14)** | J6/J2/J4/J5 fechados; o núcleo roda as 7 seções idênticas à JVM (com workarounds J7) | manter workarounds até B19/B20/J7 subirem |
| 10 | **Minimap web** | overlay | possível |
| 11 | **Snippets configuráveis** (user snippets) | — | |
| 12 | **Settings web** (settings.json na UI) | — | existe no legado Swing |
| 13 | **Detecção de encoding / fim de linha** | — | |
| 14 | **Breadcrumbs web** | — | legado Swing tem |
| 15 | **Reload do servidor ao salvar .kf do editor** | — | o editor edita o próprio front |
| 16 | **Drag & drop de arquivos no editor web** | HTML5 drag-drop | |

## Dependências do Kof (destravam itens acima)

Estado verificado contra 0.0.14-alpha (24/08/2026):

- ~~**F4 processos**~~ — ✅ `process.run` → terminal/git nativos destravados (6, 7)
- ~~**J6 KofJS for-loop / J2 IO platform**~~ — ✅ resolvidos no 0.0.14 → item 9 depende só do repro J4/J5
- **Debugger Kof** — 🟡 MVP disponível (`kof debug`: breakpoints + call stack) → Run/Debug (8)
- **F3 módulos multi-arquivo** — ❌ P1 do plano do Kof (indireto)
- **LSP** — 🟡 diagnostics reais via `kof lsp`; hover/completion planejados (alternativa ao autocomplete nativo do Estágio 5)