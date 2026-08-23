# 01 — Estratégia

## Princípio central

> O Kof Editor nasce do mesmo princípio do Kof:
> o programador escreve a intenção — o runtime cuida da complexidade.

No contexto do editor: o usuário abre um arquivo `.kf` e o editor **já sabe**
tudo sobre Kof. Não há "instale a extensão da linguagem". A linguagem vem
dentro do editor porque o editor é escrito na linguagem.

## O que copiar de editores modernos (inspiração, não portabilidade)

O repositório em `inspiracao/` reúne referências de editores modernos.
Dele aproveitamos **design de experiência**, não código:

| Ideia de editores modernos | Reinterpretação no Kof Editor |
|-------------------|-------------------------------|
| Command Palette | Paleta de comandos nativa, alimentada pelo registro de comandos do próprio binário |
| Editor com abas | View model de documentos em `Buffer`, sem navegador |
| Status bar | Barra com target atual (jvm/native/web) e diagnóstico do arquivo |
| Terminal integrado | Terminal **nativo** (ver 05), não um xterm.js |
| Diagnostics inline | Produzidos pelo lexer/análise nativa, não por LSP externo |
| Language detection por extensão | Registry de linguagens compilado no binário |
| Build tasks | Comandos `run`/`build`/`check` nativos que invocam o toolchain Kof |

O que **não** copiamos: arquitetura de extensões, host de plugins,
navegador embutido, telemetria, marketplace.

## Diferença estratégica: nativo > extensível

Os editores extensíveis venceram por serem genéricos. O Kof Editor aposta que a
**convergência é melhor que a extensibilidade genérica**:

- O Lexer de Kof é o lexer do editor. Não existe "gramática desatualizada".
- O autocomplete de Kof conhece o sistema de tipos do Kof. Não existe
  "language server que esqueceu de atualizar".
- Erros de sintaxe vêm do mesmo frontend do compilador. O que o editor
  aceita, o compilador aceita — e vice-versa.

Isso só é possível porque o editor é escrito **na** linguagem que ele edita.

## Suporte a outras linguagens

O reconhecimento é nativo, mas não exclusivo: o registry de linguagens é um
dado do binário. JSON já é nativo (lexer próprio, sem extensão). Markdown e
texto plano estão no registry e entram em estágios futuros. A regra:

> Cada linguagem suportada é um lexer **compilado**, nunca um script
> carregado em runtime.

## Estratégia de construção (estágios)

A linguagem Kof ainda está em alpha (`0.0.4`). A estratégia é **construir o
editor em paralelo com a linguagem**, entregando em cada estágio apenas o
que o Kof consegue compilar hoje, e marcando explicitamente o que depende de
features futuras (ver [02 — Etapas](02-etapas.md) e
[08 — Restrições](08-restricoes-kof.md)).

Três consequências práticas:

1. **Núcleo primeiro**: lexer, tokens, buffer, registry — nada de UI antes
   do núcleo funcionar. É o que existe hoje (Estágio 1).
2. **Cross-target por design**: nenhum módulo pode assumir "estou na JVM".
   JSON de objetos, por exemplo, é JVM-only hoje — o código que o usa é
   isolado para não vazar para o nativo.
3. **Workarounds documentados**: quando o compilador Kof tem um bug, o
   código contorna e o bug vira item de documentação e feedback para o
   Kof4j (ver [08 — Restrições](08-restricoes-kof.md)).

## Decisões deliberadas

- **Sem extensões, mesmo que "um dia dê para ter"**: extensões são a porta
  de entrada para a fragmentação que este projeto quer eliminar.
- **Sem AI embutida**: o editor não tem Copilot. O que ele oferece é um
  terminal nativo (split vertical) para rodar qualquer ferramenta — inclusive
  o opencode. IA é do usuário, não do editor.
- **Um binário, três mundos**: o mesmo `.kf` compila para JVM, native e web.
  A UI será um backend de renderização plugado no mesmo núcleo.
- **Dogfooding**: o Kof Editor é a aplicação de referência do Kof. Cada
  bug de compilador encontrado aqui é um item para o roadmap do Kof4j.

## Métricas de sucesso

- `kof build --target jvm` e `--target native` produzem o mesmo editor com
  o mesmo comportamento observável.
- Zero extensões necessárias para editar Kof, JSON, Markdown e texto plano.
- O editor é capaz de abrir, editar e rodar os próprios módulos
  (`src/core/*.kf`).