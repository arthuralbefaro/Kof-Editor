# 09 — Contrato com o KofJS (front universal)

> Status: contrato definido e **já implementado para a janela nativa JVM**.
> O backend KofJS está sendo desenvolvido no Kof4j (Fase 5 do roadmap).
> Quando existir, o front do Kof Editor compila para o navegador **sem
> JavaScript manual** — e o mesmo contrato roda embutido na janela nativa.

## Contexto

O KofJS é o backend web do Kof. A visão (roadmap do Kof4j) é um frontend
declarativo e minimalista:

```kof
page Home {
    column {
        text("Olá")
        button("Entrar") {
            login()
        }
    }
}
```

**Decisão de arquitetura**: o KofJS não é só "front para navegador" — ele é
a **linguagem de front universal**, embutível na janela nativa do editor.
O editor instalado abre **janela normal** (JVM ou nativo) e a interface é
descrita em Kof, renderizada por um host que interpreta o DOM Kof — sem
navegador. Hoje o host é `src/ui/jvm/KofDomHost.java` (interpreta o DOM em
Swing); amanhã o KofJS renderiza o mesmo DOM direto, no browser ou na
janela.

## Estado do KofJS (0.0.5 — em construção ativa)

- `kof build --target js` gera `Default.mjs` + `kof-runtime.mjs` +
  `kof-runtime-node.mjs` (ESM)
- Programas simples compilam e o código gerado é correto (verificado)
- Bloqueios atuais: entry não invocado, IO do runtime JS incompleto,
  construtores de classe quebram o backend — ver [08 — Restrições](08-restricoes-kof.md)

## O que o editor espera do KofJS

O front do editor precisa de quatro capacidades mínimas:

| Capacidade | Uso no editor | Estado hoje |
|------------|---------------|-------------|
| DOM declarativo (`page`/`column`/`row`/`text`) | Layout do editor: painel, gutter, status | via `kof serve` + HTML |
| `textarea`/edição com binding | O documento em edição | textarea servida |
| Eventos (`onInput`, `onClick`) | re-tokenizar a cada tecla | JS servido |
| `fetch`/`post` nativo | chamar `/tokenize` no servidor (ou direto, quando KofJS rodar no browser) | fetch do JS |

Quando o KofJS compilar para o navegador, o mesmo módulo `src/ui/web/`
passa a rodar o lexer **no client** (KofJS) — sem servidor. O contrato de
saída é o mesmo: `Lexer.tokenize()` → tokens → render por categoria.

## Arquitetura em três fases

```text
Fase 1 (hoje):  kof serve ──► HTML+JS ──► POST /tokenize ──► Lexer Kof (servidor)
Fase 2 (KofJS):  kofjs compile ──► browser: Lexer Kof (client) + DOM declarativo
Fase 3 (final):  um front Kof declarativo, sem JS servido, sem servidor
```

O front atual (fase 1) já é **servido por Kof**: a página, o CSS e o JS
são produzidos por funções Kof (`pageHtml`, `pageCss`, `pageJs`), e o
reconhecimento de linguagem é o lexer Kof. A única parte não-Kof é o
render DOM do navegador (que o KofJS vai absorver).

## Endpoints atuais (fase 1)

| Rota | Método | Retorno |
|------|--------|---------|
| `/` | GET | página do editor (HTML) |
| `/editor.css` | GET | tema escuro por categoria de token |
| `/editor.js` | GET | render do navegador (debounce 150ms) |
| `/tokenize` | POST | `[{kind, text, start, end}]` produzido pelo `Lexer` Kof |

## Como rodar hoje

```bash
scripts/gui-run.sh [arquivo]  # janela nativa (DOM Kof renderizado em Swing)
scripts/install.sh            # instala: ~/.local/bin/kof-editor + menu
scripts/web.sh [porta]        # front web servido por Kof (kof serve)
```

A janela nativa e o navegador usam **o mesmo contrato DOM**: o front em Kof
(`renderApp`) devolve um JSON com título, idioma, documento, spans de
tokens e diagnostics com linha/coluna (calculados em Kof); o host pinta.
Quando o KofJS existir, `renderApp` continua o mesmo — muda apenas o
renderer.

## Janela nativa (host atual)

O host (`KofDomHost`) implementa o window manager do DOM Kof na janela:

- **Divisões verticais e horizontais** aninhadas (`Ctrl+\` à direita,
  `Ctrl+Shift+\` abaixo), redimensionáveis pelo divisor
- **Abas por divisão** (JTabbedPane em cada painel) com duplicar/fechar
- **Editor colorido no próprio documento** (spans aplicados ao editor, sem
  preview separado)
- **Problemas nativos** (Ctrl+Shift+M): lista com linha/coluna, clique
  navega para o erro
- **Terminal integrado** (Ctrl+`): split inferior, executa `kof run`/build
  e qualquer comando
- **Undo/redo por documento** (Ctrl+Z / Ctrl+Y)
- **Abrir/salvar via `kof.io`** (`File.readText`/`writeText` chamados pelo
  host através do front Kof — o I/O é da linguagem, o host só mostra o
  diálogo)
- Menu: Arquivo, Editar, Exibir, Executar, **Tema** (atalhos de editores modernos)
- **Activity bar com ícones desenhados** (Explorer, Buscar, Git, Contorno, Problemas)
- **Explorer visível por padrão** — árvore com lazy-load, ícones por tipo,
  watcher automático (refresh a cada 5s)
- **Painel inferior com abas** (Problemas | Terminal)
- **Minimap** colorido à direita do editor (cores dos tokens), clique navega
- **Ctrl+P** Quick Open (recursivo, filtro) · **Ctrl+G** ir para linha
- **Ctrl+F** buscar no arquivo (destaque + F3) · **Ctrl+D** próxima ocorrência
- **Alt+Shift+↑/↓** mover linha · **Alt+Z** wrap · **Ctrl+/−** zoom
- **Contorno** (outline): símbolos class/record/função detectados pelo lexer
  Kof em `outlineKf` — duplo clique navega
- **Middle-click** fecha a aba · **Ctrl+Shift+E/F/G** abrem as views laterais
- Status bar: branch git, erros, UTF-8, idioma clicável, target clicável
- **Temas padrão claro/escuro** definidos em Kof (`theme.kf`): `darkTheme()`
  (Dracula) e `lightTheme()` (Claro) — o JSON sai de `themeAt(index)` e o
  host aplica UI + tokens
- **Exportar/importar tema em YAML**: `Tema → Exportar tema (YAML)...` /
  `Importar tema (YAML)...` (formato `chave: "#RRGGBB"`)

## Dependências do KofJS (feedback para o Kof4j)

1. Backend KofJS que compile Kof para o navegador (WASM/JS), com paridade
   da String API e do Lexer (já existem no JVM e no native), **e um
   renderer embutido** que interprete o mesmo DOM Kof dentro da janela
   nativa (JVM e native) sem navegador.
2. DOM declarativo mínimo: `page`, `column`, `row`, `text`, `textarea`,
   `button` com eventos `onInput`/`onClick`.
3. `fetch`/`post` nativo para comunicação com o servidor quando o editor
   web for híbrido (análise pesada no servidor, render no client).
4. Persistência local (IndexedDB via interop) para abrir/salvar.

O editor está pronto para consumir tudo isso: o núcleo não muda, apenas o
adaptador de render (hoje `pageHtml`/`pageJs` → amanhã `page`/`column`).