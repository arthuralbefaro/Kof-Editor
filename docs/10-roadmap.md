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

## Faltando (ordem sugerida)

| # | Feature | Bloqueio | Notas |
|---|---------|----------|-------|
| 1 | **Multi-caret / seleção em bloco** (Alt+Click, Ctrl+Alt+↑↓, Ctrl+Shift+L) | editor de texto web do KofJS (DOM) ou overlay próprio | o textarea tem 1 caret |
| 2 | **Folding (recolher blocos)** | renderer custom (pre não sabe de linhas) | possível com overlay por linha |
| 3 | **Paleta de comandos web** (Ctrl+Shift+P) com filtro | — | substitui o prompt atual |
| 4 | **Buscar na pasta com resultados clicáveis** | — | endpoint /api/search existe (searchDir) |
| 5 | **Painel de busca lateral (Ctrl+Shift+F)** | — | |
| 6 | **Terminal nativo em Kof** (processos) | **F4** (processos no Kof) | hoje o host executa via shell |
| 7 | **Git nativo em Kof** | **F4** | hoje host-side |
| 8 | **Run/Debug com breakpoints** | debugger do Kof em construção (DebugInfo na IR) | — |
| 9 | **Editor web 100% KofJS** (sem servidor) | **J4** (`unknown local slot 0`) | o front já é o mesmo |
| 10 | **Minimap web** | overlay | possível |
| 11 | **Snippets configuráveis** (user snippets) | — | |
| 12 | **Settings web** (settings.json na UI) | — | existe no legado Swing |
| 13 | **Detecção de encoding / fim de linha** | — | |
| 14 | **Breadcrumbs web** | — | legado Swing tem |
| 15 | **Reload do servidor ao salvar .kf do editor** | — | o editor edita o próprio front |
| 16 | **Drag & drop de arquivos no editor web** | HTML5 drag-drop | |

## Dependências do Kof (destravam itens acima)

- **F4 processos** → terminal/git nativos (6, 7)
- **J4 KofJS com o editor completo** → editor sem servidor (9)
- **Debugger Kof** → Run/Debug (8)
- **F2 args de main, F3 módulos** → CLI e estrutura (indiretos)