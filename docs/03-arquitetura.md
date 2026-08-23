# 03 — Arquitetura

## Visão geral

O Kof Editor segue a mesma filosofia do Kof: um **núcleo** agnóstico de
backend, com **adaptadores** por target. O núcleo é todo em Kof; o que muda
entre JVM/nativo/web é apenas a camada de sistema (tela, arquivos,
processos).

```text
┌─────────────────────────────────────────────────────┐
│                     UI / Terminal                    │  ← adaptador por target
│            (JVM: Swing · Native: runtime · Web: DOM) │
├─────────────────────────────────────────────────────┤
│                   Sessão do editor                   │
│        documentos (Buffer) · abas · status · undo    │
├─────────────────────────────────────────────────────┤
│                 Linguagem nativa                     │
│   Lexer(Kof) · Lexer(JSON) · LanguageRegistry        │
│   Theme · Highlighter · Diagnostics · Autocomplete   │
├─────────────────────────────────────────────────────┤
│                     Execução                         │
│   terminal nativo · run/build/check · targets        │
├─────────────────────────────────────────────────────┤
│                     Kof Runtime                      │
│        (strings, JSON, processos, arquivos)          │
└─────────────────────────────────────────────────────┘
```

## Módulos (hoje, em `src/core/`)

O Kof ainda não resolve símbolos entre arquivos (módulos multi-arquivo),
então os módulos são arquivos separados que `scripts/combine.sh` concatena
em um único `build/editor.kf`. A divisão de arquivos **já é a arquitetura
alvo** — quando o Kof tiver módulos, basta remover o script.

| Arquivo | Responsabilidade | Deps |
|---------|------------------|------|
| `token.kf` | `TokenNode` (cadeia ligada), `eq` (comparação segura de strings), `escapeJson`, `visualText`, JSON manual de tokens | — |
| `lexer.kf` | Lexer Kof: scanner completo + classificação (keyword/type/identifier) | token.kf |
| `json-lexer.kf` | Lexer JSON (strings, números, constantes, pontuação) | token.kf |
| `theme.kf` | Tema de cores por categoria de token | — |
| `highlight.kf` | `Highlighter`: render (ANSI) e renderTagged (estrutural) | lexer, theme |
| `language.kf` | `LanguageRegistry` nativo + `LanguageEntryNode` | token.kf |
| `buffer.kf` | `Buffer`: documento por linhas, insert/delete | token.kf |
| `demo.kf` | Amostras de código e funções de demo (tabela, diagnostics) | todos |
| `main.kf` | Ponto de entrada e demo do estágio 1 | todos |

## Fluxo principal (Estágio 1)

```text
kofSource ──► Lexer.tokenize() ──► TokenNode (cadeia)
                                      │
                    ┌─────────────────┼──────────────────┐
                    ▼                 ▼                  ▼
             Highlighter        printTokenTable    diagnostics
                    │                 │                  │
                    ▼                 ▼                  ▼
          texto com categoria    tabela kind|text   lista de erros
```

E para linguagens:

```text
LanguageRegistry.find(".json") ──► "json" ──► JsonLexer (nativo, compilado)
```

## Modelo de dados

### Tokens

Cada token é um `TokenNode`:

```
kind: "keyword" | "type" | "string" | "char" | "number" | "comment"
    | "operator" | "punctuation" | "identifier" | "error" | "eof"
text:  texto exato no fonte
start/end:  offsets no fonte
```

As categorias espelham a grammar oficial do Kof (`source.kof`), de forma que
o highlight do editor e a gramática do compilador nunca divergem.

### `List<T>` como modelo de dados ✅

O Kof 0.0.5 (commit `34ffc9f`) corrigiu `List<T>` de classes e o
descritor de campo. O editor usa `List<Token>`, `List<LanguageEntry>` e
`List<String>` diretamente — sem cadeias ligadas. Detalhe: o elemType é
inferido do primeiro elemento, então o padrão é
`listOf(Token("", "", 0, 0))` + `list.remove(0)` no retorno.

### Buffer

`Buffer` é uma cadeia de `LineNode` com `count`:

- `lineAt(i)`, `lineCount()`, `text()` (junção com `\n`)
- `insertText(line, col, text)` e `deleteText(line, col, len)` — base para
  cursor, seleção e undo/redo do Estágio 4

## Regras de arquitetura (hard constraints)

Aplicadas porque o compilador Kof alpha ainda tem bugs (detalhes em
[08 — Restrições](08-restricoes-kof.md)):

1. ~~Nenhum `new` dentro de construtor~~ — **corrigido** (34ffc9f).
2. **Callee antes de caller** — dentro de uma classe, um método só chama
   métodos declarados **antes** dele (resolução de retorno vira `Object`
   caso contrário).
3. **Nomes de campos únicos no programa inteiro** — o symbol table global
   mistura classes: dois campos com o mesmo nome resolvem para o primeiro.
4. ~~Sem `==`/`!=` de String em condição~~ — **corrigido no 0.0.5**; o editor
   usa `==` idiomático.
5. **Sem `null` em fluxo de valores** — frame merge com `null` e classe
   própria faz o ASM tentar carregar a classe em runtime. Usar sentinelas.
6. **Sem `Char` vindo de método do usuário** — boxagem incorreta.
   `peek()` retorna `String` de 1 char via `substring`.
7. **Sem `pos++` em campos** — não avança. Usar `pos = pos + 1`.
8. ~~Sem `List<T>` de classes e sem `List` como campo~~ — **corrigido** (34ffc9f).
9. **Sem `return` nu** — `return` sem valor exige `;` (senão o parser tenta
   ler uma expressão).

Essas regras estão encapsuladas no código existente; o Estágio 1+ documenta
os bugs no Kof4j que, resolvidos, eliminam cada regra.

## Arquitetura futura (módulos)

Quando o Kof tiver módulos multi-arquivo:

```text
src/
├── core/       ← o que existe hoje (núcleo da linguagem)
├── session/    ← documentos, abas, undo (Estágio 2/4)
├── term/       ← terminal nativo + runner (Estágio 3)
├── lang/       ← lexers adicionais: markdown, plain (Estágio 5)
├── ui/         ← backends de renderização por target (Estágio 4/6/7)
│   ├── jvm/
│   ├── native/
│   └── web/
└── cli/        ← interface de comando (Estágio 2)
```