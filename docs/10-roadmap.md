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

Estado 30/08/2026: os itens 3, 4, 5, 10, 14 **já existem** (paleta com filtro,
busca com resultados clicáveis, minimap clicável, breadcrumbs). Itens novos
implementados no commit `e35d12b`: Ctrl+F/F3 (buscar no arquivo), Ctrl+G (ir
para linha), Ctrl+D/Ctrl+Shift+K, F2 (renomear), F12 (definição), Alt+Z wrap,
auto-save, terminal com cwd/histórico/ANSI, sidebar responsiva (overlay no
mobile), tema exportar/importar YAML.

| # | Feature | Bloqueio | Notas |
|---|---------|----------|-------|
| 1 | **Multi-caret / seleção em bloco** (Alt+Click, Ctrl+Alt+↑↓, Ctrl+Shift+L) | editor de texto web do KofJS (DOM) ou overlay próprio | o textarea tem 1 caret |
| 2 | **Folding (recolher blocos)** | renderer custom (pre não sabe de linhas) | possível com overlay por linha |
| 8 | **Run/Debug com breakpoints** — destravado parcialmente | debugger MVP do Kof chegou (Fases 1-3): `kof debug` DAP sobre stdio, breakpoints por linha Kof + call stack (JVM); locals/stepping = Fase 7 | integrar o DAP ao painel do editor |
| 11 | **Snippets configuráveis** (user snippets em arquivo) | — | snippets fixos existem na paleta |
| 12 | **Settings web** (settings.json na UI) | — | auto-save/wrap já têm toggle na paleta |
| 13 | **Detecção de encoding / fim de linha** | — | crlfToLf já normaliza |
| 15 | **Reload do servidor ao salvar .kf do editor** | — | o editor edita o próprio front |
| 16 | **Drag & drop de arquivos no editor web** | HTML5 drag-drop | |
| 17 | **Terminal PTY real** (digitar dentro de htop/vim) | **F-pty no Kof**: `process.run` é one-shot, sem stdin interativo/PTY | precisa `process.spawn` com pipes vivos no Kof4j; o emulador visual (ANSI/cursor/grid) pode ser escrito em Kof — parser CSI igual ao Lexer |

## Dependências do Kof (destravam itens acima)

Estado verificado contra **0.2.3-beta** (29/08/2026):

- ~~**F4 processos**~~ — ✅ `process.run` → terminal/git nativos destravados (6, 7)
- ~~**J6 KofJS for-loop / J2 IO platform**~~ — ✅ resolvidos; o **núcleo roda no target `js`** (revalidado 29/08)
- **Debugger Kof** — 🟡 MVP disponível (`kof debug`: breakpoints + call stack) → Run/Debug (8)
- **F3 módulos multi-arquivo** — ✅ JVM/JS no 0.2.3-beta (`import a.b.C`); ❌ Native
- **LSP** — 🟡 diagnostics reais via `kof lsp`; hover/completion já chegaram (0.1.2)
- **R1 (regressão)** — scanner completo cracha no Native no 0.2.3-beta → bloqueia o binário nativo (Estágio 6)
- **R2/R3/R4 (regressões JVM/Native)** — `mapOf(k,v)`, `String?`, `Box<T>` quebram → não usar até o fix
- **`enum`/`Set`/pattern/higher-order/`spawn`** — ✅ novos idiomas disponíveis no editor (lexer atualizado)