# 02 — Etapas

O Kof Editor é construído em estágios alinhados com a evolução da linguagem
Kof. Cada estágio só depende do que o Kof **já compila** (validado com o
compilador em desenvolvimento). Dependências de features futuras do Kof são
marcadas explicitamente.

> Legenda: ✅ entregue · 🚧 em andamento · ⏳ aguardando feature do Kof

---

## Estágio 0 — Base do repositório ✅

- `inspiracao/` com referências de editores modernos (não versionado)
- `docs/` com visão, estratégia, arquitetura e roadmap
- `scripts/combine.sh` — concatena os módulos Kof em um arquivo único
  (o Kof ainda não tem módulos multi-arquivo com resolução entre arquivos)
- `scripts/build.sh` / `scripts/run.sh`

## Estágio 1 — Núcleo da linguagem ✅

Entrega: o **cérebro** do editor, compilando e rodando na JVM.

- `TokenNode` — modelo de tokens (cadeia ligada, sem arrays)
- `Lexer` — lexer Kof completo em Kof:
  palavras-chave, tipos, strings, chars, números, comentários `//` e `/* */`,
  operadores (1, 2 e 3 chars), pontuação, tokens de erro
- `JsonLexer` — lexer JSON (strings, números, true/false/null, pontuação)
- `LanguageRegistry` — registro nativo de linguagens
  (Kof `.kf`, JSON `.json`, Markdown `.md`, Texto Plano `.txt`)
- `Theme` — tema de cores por categoria de token (ANSII 256, alinhado à
  grammar oficial `source.kof`)
- `Highlighter` — renderização do highlight (versão taggada legível hoje;
  versão ANSI pronta, aguardando escape `\u001b` no compilador — ver Estágio 1+)
- `Buffer` — documento por linhas com insert/delete em posição
- `diagnostics` — erros de tokenização nativos
- JSON nativo (String/Int/List) sem bibliotecas

Validação: `scripts/run.sh` imprime as 7 seções da demo (registry, highlight,
tabela de tokens, diagnostics, JSON, buffer).
**Verificado em 29/08/2026 (Kof 0.2.3-beta): núcleo roda na JVM e no KofJS**
(`--target js`) com saída idêntica. A paridade byte a byte dos **3 targets**
era válida no Kof 0.0.14 (24/08/2026); o **Native regrediu** no 0.2.3-beta —
o scanner completo cracha no ELF (R1; ver 08).

## Estágio 1+ — Desbloqueios do Kof ✅ (estado verificado contra 0.2.3-beta, 29/08)

Features da linguagem necessárias para o Estágio 2 e além
(itens de feedback para o Kof4j — estado verificado contra 0.2.3-beta):

| Feature do Kof | Usada para | Hoje (0.2.3-beta) |
|----------------|------------|------|
| Escape `\u001b` em literais | Cores ANSI reais no highlight | ✅ corrigido (`ansi()` usa o escape direto, JVM e native) |
| API de String no NativeBackend (`length`, `substring`, concat) | Editor rodar em binário nativo | ⚠️ API existe, mas `substring(end>len)` crasha (R5) e o scanner completo cracha no native (R1) |
| JSON de objetos no NativeBackend | Registry/estado serializado no nativo | ✅ **fechado no 0.2.3-beta** (repro `json.decode<Point>` no native) |
| Módulos multi-arquivo | Fim do `combine.sh`; módulos reais `core/`, `ui/`, `term/` | ✅ **JVM/JS no 0.2.3-beta** (`import a.b.C` resolve); ❌ Native ainda `COMP001` |
| E/S de arquivos e args de `main` | Abrir/salvar arquivos, CLI do editor | ✅ `kof.io` (File/Path/Directory) + `main(String[] args)` |
| Tipos genéricos `List<T>` reais | Eliminar workarounds de cadeias ligadas | ✅ (`Box<T>` com primitivo regrediu na JVM — R4) |
| Processos (executar programas) | Terminal e runner nativos | ✅ `process.run(...)` → Result(stdout, stderr, exitCode) |
| Concorrência (`spawn`/`await`) | Execução concorrente no editor | ✅ JVM/JS (`Handle<T>` tipado); Native `CONC001` |
| `enum`, `Map`/`Set`, `String?`, pattern matching | Idiomas do 0.1.0/0.2.0 | ⚠️ `enum`/`Set`/pattern ✅; `mapOf(k,v)` quebra (R2); `String?` VerifyError na JVM (R3) |

> Regressões do 0.2.3-beta detalhadas em [08 — Restrições](08-restricoes-kof.md)
> (R1-R6). O núcleo foi atualizado para a gramática 0.2.3-beta (lexer).

## Estágio 2 — Arquivo e sessão ⏳ (destravado: kof.io + main(args))

- Abrir/salvar arquivos via `kof run editor.kf <path>` (ou API de arquivo da
  linguagem)
- Detecção de linguagem por extensão usando o `LanguageRegistry`
- `Buffer` ligado ao arquivo: load/save, dirty state
- Diagnostics por documento com posição (linha/coluna)
- CLI do editor: `kof-editor open <arquivo>`, `kof-editor check <arquivo>`

Critério de conclusão: editar um `.kf` de ponta a ponta
(carregar → editar → salvar → compilar).

## Estágio 3 — Terminal nativo ⏳ (destravado: `process.run`)

- Terminal integrado **nativo** (nada de xterm.js): o editor executa
  processos do sistema e mostra stdout/stderr
- **Split vertical** para rodar ferramentas externas (incl. opencode) — a
  única "integração de IA" planejada
- Comandos de execução nativos no editor:
  - `Run` → `kof run <arquivo>`
  - `Build` → `kof build <dir> --target jvm|native`
  - `Check` → `kof check <arquivo>`
  - `Target` → seletor jvm/native/web (a barra de status mostra o atual)
- Saída do build ligada ao diagnostics (clique no erro → vai para a linha)

## Estágio 4 — Renderização (JVM primeiro) ⏳ (depende de interop com Java)

- Backend de UI para JVM (via interop com Java: Swing/JavaFX ou render
  próprio)
- Painel de edição: linhas numeradas, cursor, seleção, scroll
- Abas de documentos, status bar, paleta de comandos
- Atalhos e undo/redo sobre `Buffer`
- Highlight em tempo real (estágio 1 já fornece o pipeline)

Critério: primeira versão "usável" na JVM.

## Estágio 5 — Reconhecimento profundo (autocomplete e erros semânticos) ⏳

- Parser + symbol table próprios do editor **em Kof** (ou consumo do
  frontend oficial do Kof quando exposto como biblioteca)
- Autocomplete nativo: palavras-chave, tipos, funções do módulo,
  membros de classes (alimentado pela análise, não por lista estática)
- Highlight de erros semânticos (variável indefinida, tipo incompatível)
- Hover com assinaturas
- Multi-linguagem: Markdown e JSON ganham lexers completos;
  texto plano com detecção básica

## Estágio 6 — Editor nativo ⚠️ (regrediu no 0.2.3-beta)

- Build `--target native` do editor (ELF) — **o núcleo compilava e rodava
  idêntico à JVM no Kof 0.0.14** (verificado 24/08/2026). No **0.2.3-beta o
  scanner completo (`Lexer.tokenize`) cracha no ELF** com `array index out of
  bounds` (R1) — o binário nativo do editor está bloqueado até o fix do
  NativeBackend.
- Mesmo comportamento observável do Estágio 4/5 (quando o R1 resolver)
- Terminal nativo com syscalls (via runtime Kof)
- Release binária: um binário, zero dependências

## Estágio 7 — Web 🚧 (hoje: serve · amanhã: KofJS)

- ✅ **Hoje**: front web servido por Kof (`scripts/web.sh`) — página, CSS, JS
  e `/tokenize` produzidos por funções Kof; o lexer Kof roda no servidor
- ⏳ **Com o KofJS** (Fase 5 do roadmap do Kof4j): o front declarativo em
  Kof (`page`/`column`/`textarea`) compilando direto para o navegador, com
  o lexer rodando no client
- ⏳ Persistência local (IndexedDB via interop)
- ⏳ Web worker para o lexer/análise (sem travar a UI)

O contrato com o KofJS está definido em [09 — Contrato KofJS](09-kofjs.md).

## Estágio 8 — Plataforma

- Suporte a mais linguagens como lexers nativos (nunca extensões)
- Configuração do editor em Kof (tema, atalhos) como **código Kof**
- Compartilhamento de temas/snippets como arquivos, não como marketplace
- Dogfooding total: o editor é a ferramenta de desenvolvimento do Kof

---

## Dependências externas (roadmap do Kof4j que destrava o editor)

Estado verificado contra **0.2.3-beta** (29/08/2026):

1. ~~String API completa no NativeBackend~~ — ✅ corrigido (mas `substring(end>len)` crasha — R5)
2. ~~Escape `\u001b` (e `\uXXXX`) em literais~~ — ✅ corrigido
3. **Módulos multi-arquivo** — ✅ JVM/JS (0.2.3-beta); ❌ Native (`COMP001`) — Estágio 6 nativo
4. ~~E/S de arquivos e args~~ — ✅ `kof.io` + `main(args)` — Estágio 2 destravado
5. ~~Processos~~ — ✅ `process.run` — Estágio 3 destravado
6. ~~JSON de objetos no native~~ — ✅ **fechado no 0.2.3-beta** (repro no native)
7. Interop com Java para UI — ⚠️ parcial (annotations/SAM/externos funcionam no caminho Android/JVM; a janela nativa do editor pode usar `kof.ui` + webview)
8. **Scanner completo no Native** — ❌ regressão (R1): o `Lexer.tokenize()` cracha no ELF no 0.2.3-beta; bloqueia o binário nativo do editor até o fix do NativeBackend