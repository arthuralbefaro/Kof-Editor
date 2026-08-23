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

## Estágio 1+ — Desbloqueios do Kof ⏳

Features da linguagem necessárias para o Estágio 2 e além
(todos itens de feedback para o Kof4j):

| Feature do Kof | Usada para | Hoje |
|----------------|------------|------|
| Escape `\u001b` em literais | Cores ANSI reais no highlight (workaround atual: `json.decode`) | ⚠️ via JSON (JVM) |
| API de String no NativeBackend (`length`, `substring`, concat) | Editor rodar em binário nativo | ❌ `String_length` indefinido no .s |
| JSON de objetos no NativeBackend | Registry/estado serializado no nativo | ❌ JVM-only |
| Módulos multi-arquivo | Fim do `combine.sh`; módulos reais `core/`, `ui/`, `term/` | ❌ cross-file não resolve |
| E/S de arquivos e args de `main` | Abrir/salvar arquivos, CLI do editor | ❌ |
| Tipos genéricos `List<T>` reais | Eliminar workarounds de cadeias ligadas | ⚠️ parcial (listOf String ok) |
| Processos (executar programas) | Terminal e runner nativos | ❌ |

## Estágio 2 — Arquivo e sessão ⏳ (depende de E/S de arquivo)

- Abrir/salvar arquivos via `kof run editor.kf <path>` (ou API de arquivo da
  linguagem)
- Detecção de linguagem por extensão usando o `LanguageRegistry`
- `Buffer` ligado ao arquivo: load/save, dirty state
- Diagnostics por documento com posição (linha/coluna)
- CLI do editor: `kof-editor open <arquivo>`, `kof-editor check <arquivo>`

Critério de conclusão: editar um `.kf` de ponta a ponta
(carregar → editar → salvar → compilar).

## Estágio 3 — Terminal nativo ⏳ (depende de processos)

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

## Estágio 6 — Editor nativo ⏳ (depende de String API + runtime nativo)

- Build `--target native` do editor completo (ELF autônomo)
- Mesmo comportamento observável do Estágio 4/5
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

1. String API completa no NativeBackend (`length`, `substring`, `concat`,
   `charAt` com tipos corretos) — **Estágio 6**
2. Escape `\u001b` (e `\uXXXX`) em literais — **Estágio 1+/4**
3. Módulos multi-arquivo — **todos os estágios**
4. E/S de arquivos e args — **Estágio 2**
5. Processos — **Estágio 3**
6. JSON de objetos no native — **Estágio 6**
7. Interop com Java para UI — **Estágio 4**