# 09 — Contrato com o KofJS (front universal)

> Status: contrato definido e **já implementado para a janela nativa JVM**.
> O backend KofJS está **alpha funcional** (`0.2.3-beta`): a mesma
> IR gera ES Modules executados na GraalJS embarcada (sem Node.js), com
> source maps, e a UI oficial da linguagem (`kof.ui`) renderiza por KofJS
> dentro do webview nativo `bin/kof-webview` (WebKitGTK).
> O **núcleo do editor roda no target `js`** (7 seções idênticas à JVM —
> revalidado 29/08/2026).
> Quando o front declarativo do editor migrar para `kof.ui`, ele compila para
> o navegador/janela **sem JavaScript manual** — e o mesmo contrato roda
> embutido na janela nativa.

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
O editor instalado abre **janela normal** (WebKitGTK via `bin/kof-webview`)
e a interface é descrita em Kof, servida por `kof serve` — sem navegador
visível. O antigo host Java/Swing (`KofDomHost`) foi removido; a janela
nativa e o navegador compartilham o mesmo front (`src/ui/web/web.kf`).

## Estado do KofJS (0.2.3-beta — alpha funcional)

- `kof build --target js` / `kof run --target js`: mesma IR → ESM (ES2022+)
  executado na GraalJS **embarcada** no jar (sem Node.js nem runtime externo)
- Funciona: classes/herança/records/interfaces, `List<T>`/String API/arrays,
  JSON, exceções, lambdas **com capturas**, if-expressions, `kof.io`,
  `kof.time`, source maps, for/while/do-while, `enum`, `Set`, pattern
  matching, higher-order, `spawn`/`await` (`Handle<T>`), null safety
- IO delega para Java via `kof_platform` (`KofJsRunner`) — J2 resolvido
- **Núcleo do editor no KofJS**: as 7 seções do `editor.kf` rodam no
  `--target js` com saída idêntica à JVM (revalidado 29/08/2026)
- **UI oficial**: widgets `kof.ui` (`Window`/`Label`/`Button`/`Input`/
  `Column`/`Row`/`View`/`Style`, bind + ações por lambda; `Link`/`Image`/
  `Icon`/`Font`) renderizam DOM no webview nativo `bin/kof-webview` ou no
  browser
- Bloqueios remanescentes: regressões gerais do 0.2.3-beta (R1-R6) — ver
  [08 — Restrições](08-restricoes-kof.md); no JS em si o núcleo está OK

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

## Janela nativa

A janela nativa é o `bin/kof-webview` (WebKitGTK), presente na distro Kof:
o launcher sobe `kof serve build/editor-web.kf` e aponta o webview para
`http://localhost:8765`. Toda a UI do editor é Kof (`src/ui/web/web.kf`) —
o mesmo front que roda no navegador e na demo web (Pages). Recursos
atuais: explorer com lazy-load, git integrado, terminal, minimapa,
quick open (Ctrl+P), busca, temas light/dark + import/export YAML.

## Dependências do KofJS (feedback para o Kof4j)

Estado no **0.2.3-beta** (29/08/2026):

1. ~~Backend KofJS que compile Kof para o navegador~~ — ✅ existe (alpha,
   GraalJS embarcada; browser via ESM + DOM shim). **Núcleo do editor roda
   no target `js`** (revalidado 29/08). **Renderer embutido na janela
   nativa** — ✅ `bin/kof-webview` (WebKitGTK): a UI é KofJS rodando dentro
   da janela, sem navegador visível.
2. **DOM declarativo mínimo** — ⚠️ o Kof escolheu widgets `kof.ui`
   (`Window`/`Column`/`Row`/`Button`/`Input` com bind + ações) em vez de
   `page`/`text`; o editor deve migrar do contrato próprio (`renderApp`) para
   esses widgets quando possível.
3. **`fetch`/`post` nativo** — ✅ `kof.http` client existe em **JVM e JS**
   (27/08: `Java HttpClient` via interop no JS); Native ainda `HTTP002`.
   Cobre o caso híbrido (análise no servidor, render no client).
4. Persistência local (IndexedDB via interop) para abrir/salvar — ❌ pendente.

O editor está pronto para consumir tudo isso: o núcleo não muda, apenas o
adaptador de render (hoje `pageHtml`/`pageJs` → amanhã widgets `kof.ui`).