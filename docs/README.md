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
| [07 — Inspiração](07-inspiracao.md) | Principios de UX herdados de editores modernos (referencia na pasta `inspiracao/`, nao versionada) |
| [08 — Estado Atual e Restrições](08-restricoes-kof.md) | O que roda hoje, bugs do compilador Kof encontrados, workarounds usados e dependências da linguagem |
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

O núcleo do editor compila e roda na **JVM** (via `kof build --target jvm`):

- Registry de linguagens 100% nativo (Kof, JSON, Markdown, texto plano)
- Lexer Kof completo em Kof (palavras-chave, tipos, strings, chars, números,
  comentários, operadores, pontuação, tokens de erro)
- Lexer JSON completo em Kof
- Highlight de sintaxe com categorias alinhadas à grammar oficial (`source.kof`)
- Diagnostics nativos (erros de tokenização em tempo de edição)
- JSON nativo (String/Int/List) sem bibliotecas
- Buffer de documento com operações de insert/delete
- **Front web** servido por Kof (`scripts/web.sh`): página, CSS, JS e
  tokenização (`/tokenize`) produzidos por código Kof — abre em qualquer
  navegador, com o reconhecimento de linguagem rodando no servidor em Kof puro

O target **nativo** roda o núcleo completo (ELF estático); cores ANSI no
nativo aguardam o escape `\u001b` no lexer. O **web** roda via `kof serve`
com o cérebro Kof no servidor; o front declarativo em Kof aguarda o backend
KofJS (ver [09 — Contrato KofJS](09-kofjs.md)).

## Como rodar

```bash
scripts/run.sh          # combina os módulos e executa na JVM (usa `kof` do PATH)
scripts/build.sh        # combina e gera build/out
```

O compilador `kof` usado deve ser a versão em desenvolvimento do Kof
(release `0.0.4-alpha` atual ou superior). A variável `KOF` pode apontar para
o binário desejado:

```bash
KOF=/caminho/para/kof scripts/run.sh
```