# Kof Editor — Documentação

> Uma linguagem. Um editor. Vários mundos.

Índice da documentação do **Kof Editor**, o editor de código escrito
inteiramente em [Kof](https://github.com/estrutura-do-projeto), a linguagem
de programação multi-target (JVM, nativo e web).

---

## Documentos

| Documento | Conteúdo |
|-----------|----------|
| [00 — Visão](00-visao.md) | O que é o Kof Editor, a diferença em relação aos editores modernos e o objetivo final |
| [01 — Estratégia](01-estrategia.md) | Princípios de decisão, o que copiar de editores modernos, o que recusar, filosofia de design |
| [02 — Etapas](02-etapas.md) | Roadmap em estágios (0 a 8), cada um com entregáveis, dependências da linguagem e critérios de conclusão |
| [03 — Arquitetura](03-arquitetura.md) | Camadas, módulos, fluxos de dados e como tudo se conecta |
| [04 — Linguagem Nativa](04-linguagem-nativa.md) | O sistema de reconhecimento de linguagens 100% nativo: lexer, highlight, diagnostics, autocomplete |
| [05 — Terminal e Execução](05-terminal-execucao.md) | Terminal integrado, execução, build e targets nativos do editor |
| [06 — Targets](06-targets.md) | JVM, nativo e web: como o mesmo editor é entregue em cada mundo |
| [08 — Estado Atual e Restrições](08-restricoes-kof.md) | O que roda hoje (0.2.3-beta), regressões do Kof (R1-R6) com repros mínimos, workarounds e dependências da linguagem |
| [09 — Contrato KofJS](09-kofjs.md) | O front web do editor: hoje via `kof serve`, e o contrato para quando o backend KofJS existir |
| [10 — Roadmap](10-roadmap.md) | Lista viva de funcionalidades implementadas e o que ainda falta |

---

## Escopo

**Foco atual: JVM e Native para Linux.** O target web é uma capacidade
real existente (serve + webview), mas está **fora do escopo atual de
desenvolvimento** — sem release web nem navegador como alvo de entrega.
Release: `scripts/release.sh` (tarballs JVM/native em `dist/`) + pipeline
`.github/workflows/release.yml`.

## Estado atual (Estágio 1)

O núcleo do editor compila e roda na **JVM** (via `kof build --target jvm`) e
no **KofJS** — revalidado no Kof 0.2.3-beta:

- Registry de linguagens 100% nativo (Kof, JSON, Markdown, texto plano)
- Lexer Kof completo em Kof, **alinhado à gramática 0.2.3-beta**:
  palavras-chave (`enum`, `spawn`, `await`, `assert`, `entity`...), tipos
  maiúsculos **e minúsculos** (`Int`/`int`, `String`/`string`...), strings,
  chars, números (dec/hex `0x`/exponencial), comentários, operadores
  (`->`, `=>`, `::`, `<<=`, `>>=`, `>>>`), `@` (annotation), tokens de erro
- Lexer JSON completo em Kof
- Highlight de sintaxe com as mesmas categorias da grammar oficial
  (`source.kof`) — ⚠️ a grammar do Kof4j está defasada (falta `enum`/`spawn`/
  `await`/`assert`/`mapOf`); o lexer do editor segue o **Lexer.java** real
- Diagnostics nativos (erros de tokenização em tempo de edição)
- JSON nativo (String/Int/List) sem bibliotecas
- Buffer de documento com operações de insert/delete
- **Front web** servido por Kof (`scripts/web.sh`): página, CSS, JS e
  tokenização (`/tokenize`) produzidos por código Kof — abre em qualquer
  navegador, com o reconhecimento de linguagem rodando no servidor em Kof puro

O editor instalado (`scripts/install.sh` → comando `kof-editor`) abre uma
janela nativa (WebKitGTK) com o front servido por `kof serve`: abas,
explorer, terminal (com comandos TUI via `script` + `cleanTty` em Kof),
git, temas e "Abrir pasta..." com diálogo nativo (zenity).

O **JVM** e o **KofJS** (GraalJS embarcada) rodam o núcleo completo —
revalidado em **29/08/2026 contra o Kof 0.2.3-beta** (7 seções idênticas).
O target **nativo** ainda **crasha na seção [2]** no 0.2.3-beta (regressão do
NativeBackend no scanner completo — ver
[08 — Restrições](08-restricoes-kof.md), R1); a paridade byte a byte dos 3
targets era válida no Kof 0.0.14 (24/08/2026). O **web** roda via `kof serve`
com o cérebro Kof no servidor; o front declarativo em Kof aguarda a adoção
dos widgets `kof.ui` (ver [09 — Contrato KofJS](09-kofjs.md)).

## Como rodar

```bash
scripts/run.sh          # combina os módulos e executa na JVM (usa `kof` do PATH)
scripts/build.sh        # combina e gera build/out
```

O compilador `kof` usado deve ser a versão em desenvolvimento do Kof
(release **`0.2.3-beta`** atual ou superior). A variável `KOF` pode apontar para
o binário desejado:

```bash
KOF=/caminho/para/kof scripts/run.sh
```