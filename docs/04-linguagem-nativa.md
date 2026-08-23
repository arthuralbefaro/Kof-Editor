# 04 — Linguagem Nativa

## Princípio

> O editor reconhece linguagens porque ele **é** a linguagem.
> Não existe "plugin de sintaxe": o lexer de Kof está no binário do editor.

Nos editores convencionais, cada linguagem é uma extensão com uma grammar TextMate + um
language server. No Kof Editor, a linguagem é compilada junto com o editor —
mesmo binário, mesma versão, zero instalação.

## Registry de linguagens

`LanguageRegistry` é o coração da detecção nativa. Compilado no binário,
sem carregamento em runtime:

| Linguagem | Extensão | Lexer | Estado |
|-----------|----------|-------|--------|
| Kof | `.kf` | `Lexer` (completo) | ✅ |
| JSON | `.json` | `JsonLexer` | ✅ |
| Markdown | `.md` | planejado | ⏳ Estágio 5 |
| Texto Plano | `.txt` | detecção básica | ⏳ Estágio 5 |

A API do registry é a porta de entrada de **qualquer** linguagem futura:
adicionar uma linguagem é escrever um lexer em Kof e registrá-lo — nunca
distribuir um script.

## Categorias de tokens (alinhadas à grammar oficial)

O Kof distribui a grammar `editor/kof.tmLanguage.json` (scope `source.kof`).
O editor usa exatamente as mesmas categorias, para que highlight e
compilador nunca divirjam:

| Categoria do editor | Scope da grammar |
|---------------------|------------------|
| `comment` | `comment.line.double-slash.kof`, `comment.block.kof` |
| `string` | `string.quoted.double.kof` |
| `char` | `string.quoted.single.kof` |
| `number` | `constant.numeric.kof` |
| `keyword` | `keyword.control.kof`, `keyword.declaration.kof`, `constant.language.kof` |
| `type` | `storage.type.kof` |
| `operator` | `keyword.operator.*.kof` |
| `punctuation` | (parte do highlight estrutural) |
| `error` | não existe na grammar — é do frontend nativo |

`error` é a categoria que editores convencionais só conseguem com LSP; no Kof Editor ela
nasce no lexer.

## Pipeline de reconhecimento

```text
source ──► Lexer.tokenize() ──► TokenNode ──► Highlighter ──► render
                                             └────► diagnostics ──► erros
```

- **Highlight**: cada token recebe a cor do `Theme` pela categoria
  (`colorFor`). Hoje a saída demo é taggada (`[keyword]var[/keyword]`);
  a versão ANSI está pronta no `Theme` e depende do escape `\u001b` no
  compilador (ver 08).
- **Erros**: tokens `error` são coletados por `diagnostics` com a mesma
  severidade e mensagem que o editor vai mostrar inline (Estágio 2).

## Autocomplete (Estágio 5)

O autocomplete será nativo e alimentado pela **análise**, não por lista
estática:

- palavras-chave e tipos: do conjunto que o `Lexer` já classifica;
- símbolos do documento: identificadores coletados na tokenização
  (escopo global do módulo);
- membros de classe: quando o editor tiver o parser/symbol table próprio em
  Kof (ou consumir o frontend oficial do Kof exposto como biblioteca).

A regra continua: a lista de sugestões nunca diverge do que o compilador
aceita, porque a fonte é a mesma.

## Por que não LSP?

O Kof já tem `kof lsp` (ver `docs/tooling/LSP.md` do Kof4j). Para o
Nesses editores, o LSP é a única via. Para o Kof Editor, o LSP é um caminho
**desnecessário**: o frontend oficial vive no mesmo ecossistema, e o editor
pode consumir a análise diretamente em Kof quando a linguagem expor isso.

O LSP continua útil para *outros* editores (Vim, Emacs, etc.) — não para o
nosso.