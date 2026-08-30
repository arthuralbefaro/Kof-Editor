# 08 — O que falta no Kof para o editor (fonte do prompt)

> Última verificação: **0.2.3-beta + fixes locais** (`3c4f280`, 30/08/2026,
> branch `fixes-for-kof-editor`) — **build local, `mvn clean test` 669/669 e
> repros executados nos 3 targets**. Cada item tem repro mínimo e impacto no
> editor. Corrigidos ficam no histórico.

## Fixes locais aplicados (29–30/08/2026 — branch fixes-for-kof-editor)

| Fix | Commits | Repro validado |
|-----|---------|----------------|
| **R2 — `mapOf(k,v)`** criava Map vazio (args ignorados) + POP/VOID no `kof_map_put` (JVM) + mid-expression no `handleMapOp` (JS) | `8dc4644` | `mapOf("a",1).get("a")=1` nos 3 targets |
| **R3 — null safety `String?`** — `comparisonOperandType` preserva ref (null-literal decide ref; Unknown=INT); `VarDeclStmt` unwrap Nullable; `KofConditionalJump`/`KofBinary` emitem `if_acmp*` | `8dc4644`+`9462a48` | `var s: String? = null; if (s != null)` → `isnull/len=2` nos 3 targets |
| **R4 — `Box<T>` genérico** — `RecordDeclarationNode` perdia `typeParameters` (parser descartava); record resolve `T` via `resolveWithTypeParams` | `8dc4644` | `Box(42).get()=42` JVM/JS |
| **Regressão**: Unknown como ref fazia `r.exitCode != 0` emitir `if_acmpeq` sobre int → Unknown voltou a ser INT no `isRef` (2 caminhos) | `9462a48` | `process.run().exitCode` + `lastSlash >= 0` |
| **`String.lastIndexOf`** faltava no `stringMethodSignature` → `()Ljava/lang/Object;` | `3c4f280` | `path.lastIndexOf("/")` → Int |
| **String API no SemanticAnalyzer** — `indexOf/lastIndexOf/length/compareTo/isEmpty` inferem Int/Bool (var local de `s.lastIndexOf(x)` era Unknown) | `3c4f280` | `var idx = s.lastIndexOf("b"); idx >= 0` |
| **Comparação contra null no KofBinary** — `got == null` (m.get() Unknown) usava accType → `if_icmpeq` sobre ref; agora usa lado não-null ou Object | `3c4f280` | `mapOf("a",1).get("a") == null` → false |

Suíte: **`mvn clean test` 669/669** (456 compiler + 8 script + 5 C-compiler).

## Estado do par dos 3 targets (0.2.3-beta + fixes)

```text
JVM   : núcleo completo OK (7 seções) — Map/null/Box/String API corrigidos
Native: scanner completo CRASH (R1) — repro byte-exato; substring(end>len) crasha; record-genérico segfault
JS    : núcleo completo OK (7 seções)
```

## R1 — aberto (NativeBackend)

Repro byte-exato: o `Lexer.tokenize()` do editor crasha no ELF quando o
**input tem 1 char a mais** (x172 roda `tokens=7`; y173 — idêntico + `0`
no fim — dá `Runtime error: array index out of bounds`). Não é conteúdo
(variantes `0/1/x/"/"` todas crasham com 173 chars). Suspeita: jump
table/labels de método grande no NativeBackend. Repros em
`/tmp/opencode/kof-repros2/` (r1x172 = OK, r1y173 = crash).
Mismatches semânticos relacionados: `substring(end > len)` crasha no
native (JVM lança exceção catchável) e `#`+identificador hangou no
0.2.3-beta original (re-testar com os fixes).

## Verificação 0.2.3-beta (29/08/2026 — repros executados)

### Corrigidos upstream (repros passam)

| Item | Repro | Resultado |
|------|-------|-----------|
| **F3 — módulos multi-arquivo (JVM/JS)** | `import a.b.C` + `main()` num dir com `a/b/C.kf` | ✅ `hi from a.b.C` na JVM e no JS; **Native ainda quebra** (`COMP001: undefined reference`) |
| **F6 — JSON de objetos no Native** | `json.decode<Point>` + `json.encode(Point)` | ✅ `pt: 1,2` / `enc: {"x":3,"y":4}` no native (JSN002 fechado) |
| **enum** | `enum Color { Red }` + `switch` exaustivo + `name()` | ✅ 3 targets |
| **Set** (`setOf`) | `setOf()+add+contains+size` | ✅ JVM/Native |
| **pattern matching** (`case String s`) | `switch` c/ `case String str` | ✅ JVM/Native/JS |
| **higher-order** (`listOf.map/filter/reduce`) | `nums.map((x:Int)->x*2)` | ✅ JVM/Native |
| **`@` agora é token real** (annotation) | `@Kof` antes de `class` | ✅ compila; editor aceitou `@` como punctuation |

### Regressões / bugs novos (repros mínimos + workaround)

| # | Bug (0.2.3-beta) | Repro mínimo | Impacto no editor | Workaround |
|---|------------------|--------------|-------------------|------------|
| **R1** | **NATIVE: núcleo do editor crasha** — `Lexer.tokenize()` (o scanner completo c/ if/else + `List` + `substring`) → `Runtime error: array index out of bounds` (ou hang sem saída). Loop simples de `substring` isolado funciona; o `tokenize()` inteiro não | o `editor.kf` combinado no `--target native` (paridade 0.0.14 quebrou) | **o binário nativo do editor não roda mais** (seção [2] falha) | rodar na JVM/JS; repro mínimo enviado ao Kof4j |
| **R2** | ~~**`mapOf(k, v)` quebra**~~ — **CORRIGIDO (`8dc4644`)** | `var m = mapOf("a", 1); m.get("a")` → `1` nos 3 targets | — | — |
| **R3** | ~~**null safety `String?` VerifyError**~~ — **CORRIGIDO (`8dc4644`+`9462a48`)** | `var maybe: String? = null; if (maybe != null)` → OK nos 3 targets | — | — |
| **R4** | ~~**`Box<T>` c/ primitivo VerifyError (JVM)**~~ — **CORRIGIDO (`8dc4644`)** na JVM/JS (native: segfault pré-existente em record-genérico) | `Box(42).get()` → `42` | — | — |
| **R5** | **NATIVE: `substring` com `end > length` crasha** (`array index out of bounds`) em vez de encurtar/lançar exceção capturável — a JVM lança `StringIndexOutOfBoundsException` (catchável) | `s.substring(len, len+1)` | mismatch semântico JVM↔Native; lexers/parceiros que sondeiam o fim da string quebram no native | nunca passar `end > length` |
| **R6** | **NATIVE: `#` (token de erro) + identificador → hang** (loop infinito, sem saída) | `new Lexer("#x").tokenize()` no native | qualquer diagnóstico de token inválido no native pode travar | tratar `#`/erro no lexer sem depender de `substring` fora de bounds |

> Nota de diagnóstico: o launcher `bin/kof` **mascara `VerifyError` da JVM com
> "os componentes de runtime do JavaFX não foram encontrados"** (o texto não
> existe no jar nem no launcher — é o catch-all do run). Para ver o erro real,
> compile `kof build --target jvm` e invoque a classe por reflexão
> (`Class.forName(...).getMethod("main",...)`), que expõe o `VerifyError`.

### Estado do par dos 3 targets (0.2.3-beta)

```text
JVM   : núcleo completo OK (7 seções)  — R2/R3/R4 impedem Map-par/null-safety/Box
Native: núcleo CRASH na seção [2] (R1)  — R5/R6; F3/F6/enum/Set/pattern OK
JS    : núcleo completo OK (7 seções)  — pattern matching + null OK
```

O núcleo foi **atualizado para a gramática 0.2.3-beta**: o `Lexer` do editor
agora classifica `enum`/`spawn`/`await`/`assert`/`entity` (keywords), tipos
minúsculos (`int`/`string`/`bool`...), números hex `0x...`/exponencial, `->`,
`=>`, `::`, `<<=`, `>>=`, `>>>` e `@` (punctuation); `fun` voltou a ser
identificador. A amostra do demo usa `enum`/`mapOf`/`spawn`/`0xFF00FF`/
`String?` e o token inválido passou a ser `#` (LEX005) — `@` é token válido.

## Atualização `3329323`→`025a5fd` (25/08/2026 — G5 observability + G9 security)

| Mudança | Verificação executada |
|---------|----------------------|
| **B25 ✅ CORRIGIDO upstream** (`3329323` exclui String/Int/Long/Bool do SEM025 — mesmo fix do nosso, commitado) | `.length()`/`contains`/`substring` voltam a compilar em todos os targets |
| **kof.observability (G5) — sem gap de target** | ✅ JVM/Native/JS: `health()` → "UP", `readiness()` → true, `counter`/`increment(name,delta)` (1→6), `gauge`, `requestId()`; ⚠️ requestId tem formato divergente: 36 chars c/ hífens na JVM/JS (UUID) vs 32 hex no native |
| **G9 security**: `apiKeyGenerate/apiKeyValid`, `sessionCreate/sessionGet/sessionDestroy`, `rateLimit(key, limit, window)` | ✅ JVM/Native/JS: key gerada valida, "fake" rejeita, session retorna o usuário, rateLimit ok |
| fix native `65f748f`: args vazio (N3) + box String `+=` (N9) | sem repro dedicado |

Persistem neste build: **B19, B20, B21(+PKG004), B23, B24, J7**.
Paridade JVM == Native == JS do editor **revalidada**.

## Atualização 0.1.0-beta (25/08/2026 — G10 completo, security nativa)

| Mudança | Verificação executada |
|---------|----------------------|
| **B22 ✅ CORRIGIDO** — PBKDF2+SHA-512+JWT+AES-GCM nativos em asm (SECN001/002/003/004, G10 fechado) | `passwords.verify("abc", hash("abc"))` → `true` no native |
| **Multi-arquivo avançou (F3)** — `e462fcb` alinha serve/check/test ao modelo de módulo | `kof check <dir>` **resolve cross-file** ✅ (erro proposital detectado entre irmãos); ⚠️ mas `kof build <dir>`/`kof run <file>` **ainda não resolvem irmãos** (SEM025) e run não aceita diretório (COMP001) |
| **PKG004 novo**: `package` declarado deve corresponder ao diretório ("um diretório é um pacote") | valida no run também — arquivos com package errado na pasta quebram o run |
| ~~NOVO B25 (P0 bloqueante) — SEM025 falso positivo em builtins~~ — **CORRIGIDO** (`3329323`) | o commit `05cf8bd` acusava `Cannot resolve method 'length' on type 'String'` para qualquer método de String/Int/Long/Bool — quebrou a linguagem inteira; fix aplicado localmente e depois commitado upstream |
| ⚠️ Inconsistência de release | `VERSION` = `0.0.14-alpha`, pom `<revision>` = `0.1.0-beta` (CI de concordância deveria pegar) |
| kof.mq | `queueSize` ✅; `mq.size` ainda dá crash `COMP002` (B24); subscribe com lambda c/ captura ainda VerifyError (B23); `fn(msg)` dos docs → SEM011 |

## Atualização `c4a184f`→`3b3f793` (25/08/2026 — messaging, strings e security nativos)

| Mudança | Verificação executada |
|---------|----------------------|
| **kof.mq** (pub/sub + filas em memória) | ✅ `mq.queue()`/`push`/`pop` funcionam; ❌ **`mq.size(q)` dá crash do compilador** (`COMP002: frame crash`, disfarçado de "JavaFX não encontrado" no launcher) — o nome certo é `mq.queueSize(q)`; ❌ **`mq.subscribe(topic, lambda com captura)` → `VerifyError: Bad local variable type` em `Lambda0.invoke`** (sem teste E2E upstream; a sintaxe `fn(msg)` documentada nem parseia) |
| **String→numérico no NATIVE** (`c822ed3`: `kof_string_to_int/long/double/float` em asm) | ✅ `"42".toInt()` → 42, `"7".toLong()+1` → 8; FLT001 continua para double/float |
| **passwords PBKDF2 no NATIVE** (`c4a184f`) | ❌ na época `verify` retornava false — **corrigido no 0.1.0-beta** (ver acima) |
| base64 + JSON string handling no NativeRuntime | sem repro dedicado |

## Atualização `22fd027`+`f44654f` (25/08/2026)

| Mudança | Efeito no editor |
|---------|------------------|
| **kof.ui: `Link`, `Image`, `Icon`, `Font`** | novos widgets com `text/setText`, `url/setUrl`, `src`, `setFont/font`, `remove` — úteis para o front do editor migrar para widgets (`kof check` OK) |
| **`KofUnaryOp` ganhou `D2F`** + JSON quoting no NativeRuntime + package lookup no SemanticAnalyzer | sem efeito direto |
| ⚠️ **NOVO B21 — `kof run <file>` compila o DIRETÓRIO inteiro** | exige exatamente um `main()` entre todos os `.kf` da pasta (`PKG002: module has N main() functions`). O editor não é afetado (`build/` tem só o `editor.kf` combinado), mas qualquer repro/teste em pasta compartilhada quebra — rodar em dir isolado |

## Resultado da verificação 0.0.14 (repros executados, 24/08/2026)

### Corrigidos confirmados com repro

| Bug | Repro executado | Resultado |
|-----|-----------------|-----------|
| B2 `pos++` em campo | contador com `pos++` | ✅ 1/2/3 |
| B3 record + for-in + accessors | `record Token` + `listOf` + `x.kind()` | ✅ `kw` |
| B10 campos do construtor primário em métodos | `class Connection(String host, Int porta)` + método | ✅ `mel` |
| B11 `var x = new Classe()` local c/ field-init | `var t = new Theme()` + uso | ✅ |
| B12 native: função que modifica campos | `lightThemeFrom(t)` no ELF | ✅; **e `kof run --target native` voltou a funcionar** |
| B13 VerifyError no editor completo | demo das 7 seções na JVM | ✅ sumiu |
| J5 `\n` literal no runtime JS gerado | `node --check` dos `.mjs` do editor | ✅ sintaxe OK |
| J4 editor completo no JsBackend | `node Default.mjs` do editor | ✅ roda as 7 seções (após workarounds J7/Buffer) |

### Novos bugs encontrados nesta verificação (reportar ao Kof4j)

| # | Bug | Repro mínimo | Impacto no editor | Workaround aplicado |
|---|-----|--------------|-------------------|---------------------|
| **B19** | **`new` dentro de construtor suprime o `super()` implícito** — sintoma novo: `VerifyError: Type uninitializedThis ... not assignable` (antes era `Bad local variable type`) | classe com field-init + `var y = new Outra()` no construtor | classes do editor não podem instanciar dentro do construtor sem chamá-lo explicitamente | **`super()` explícito** na 1ª linha (verificado: funciona) |
| **B20** | **Método de usuário com nome de builtin String desvia para o builtin no NATIVE**: `startsWith`, `contains`, `trim`... → resultado errado silencioso | `class D { contains(String s): Bool { return true } }` → `false` no native, `true` na JVM | `Lexer.startsWith` retornava errado no native → comentários `//` viravam operadores | **renomeado para `hasPrefix`** (lexer.kf) |
| **J7** | **JsBackend: statements após `if` dentro de loop são içadas ao header** do for JS com `continue` sintético; variável declarada no corpo vira TDZ → `ReferenceError`. Acontece em `while`/`for`/`for-in` | loop com `if (!first) {...}` seguido de mais statements (`j2/j4.kf`) | qualquer loop com código após um `if` quebra no JS | **if/else como último statement do corpo** (`listNames` reestruturado) |
| **B21** | **`kof run <file>` compila o diretório inteiro** (novo em `f44654f`): exige um único `main()` entre todos os `.kf` da pasta → `PKG002` | dois `.kf` com `main()` no mesmo dir + `kof run um.kf` | scripts de repro/teste em pasta compartilhada quebram; `scripts/run.sh` do editor OK (build/ tem só o combinado) | arquivo sozinho no dir; ou `kof build <dir>` consciente do escopo |
| **B22** | **`passwords.verify` no NATIVE retorna sempre `false`** (`c4a184f`) | `verify("abc", passwords.hash("abc"))` → false no native, true na JVM | segurança nativa inutilizável para login | usar JVM p/ auth até corrigir |
| **B23** | **`mq.subscribe(topic, lambda c/ captura)` → `VerifyError: Bad local variable type`** em `Lambda0.invoke`; a sintaxe `fn(msg)` dos docs nem parseia | `var r = ""; mq.subscribe("t", (msg) -> { r = msg; return 0 })` | pub/sub do kof.mq inutilizável na JVM | usar filas (`push`/`pop`) que funcionam |
| **B24** | **namespace não suportado dá crash em vez de diagnóstico**: `mq.size(q)` → `COMP002: frame crash` ("JavaFX não encontrado" no launcher) | `var n = mq.size(q)` | erros de API viram crash críptico | nome correto: `mq.queueSize(q)` |
| — | **`class X(args)` compila como RECORD real** (`extends java.lang.Record`, ACC_FINAL): estender essas classes falha (`cannot inherit from final class`) e declarar campos no corpo delas dá `NoSuchFieldError` | `class Animal(String name)` + `class Dog extends Animal` | herança exige classe "normal" com `constructor` explícito | usar `class X { campo; constructor(...) }` p/ classes com estado |

### Mudança de semântica que expôs bug latente nosso

- **`List.add` agora faz append de verdade** (correção do B4 no Kof). O padrão
  sentinela `listOf("")` + `add(...)` **sem** `remove(0)` deixou de funcionar:
  o `Buffer` ficava com uma linha vazia fantasma no índice 0.
  **Fix nosso**: `lines.remove(0)` ao final do constructor (`buffer.kf`).
- Os demais módulos já usavam sentinela + `remove(0)` corretamente.

### Estado final: paridade dos 3 targets

```text
kof run editor.kf            == kof build --target native (ELF) == node Default.mjs
7 seções idênticas byte a byte (modulo ANSI) — verificado com diff em 24/08/2026
```

## Atualização documental 0.0.14 (antes do build local)

| Mudança no Kof | Efeito no editor | Evidência |
|----------------|------------------|-----------|
| Overload de construtores | F1 avança — classes com múltiplos construtores compilam | commit `83de878`; overload geral de métodos ainda colapsa por nome (nota do target Android) |
| **`test "nome" { }` estruturado** | suíte nativa nos 3 targets, PASS/FAIL por nome + exit code (G6 fechado) | `status.md`; commit `a362613` |
| **`kof debug` DAP MVP** | breakpoints por linha Kof + call stack sobre stdio (`KofDebug.java` + JDWP cru) — item 8 do roadmap parcialmente destravado; locals/stepping ficam para a Fase 7 do debugger | `debug-adapter.md`; commits `9b46610`, 0.0.14 |
| **LSP com frontend real** | `kof lsp`: didOpen/didChange → publishDiagnostics do compilador de verdade (mesmos códigos/mensagens); hover/completion ainda não existem | `tooling/LSP.md` |
| **Otimizador de IR + `--release`** | constant folding/DCE sempre ativos; `--release` tira metadata de debug | `status.md` |
| **kof.ui multi-janela** | `Window` ganhou size/font/bold/color e múltiplas janelas — caminho oficial da janela do editor passa a ser kof.ui + webview `bin/kof-webview` (WebKitGTK) | CHANGELOG 0.0.14 |
| **JSN003 fechado no Native** | `json.decode<Int[]>` funciona no native; **JSN002 (objetos) segue aberto** — F6 continua | commits `beba03d` |
| **Android target (Fase 1)** | interop externo (.jar/.aar), SAM conversion, annotations — sem efeito direto no editor hoje | `targets/KOFANDROID.md` |

## Lista ativa — bugs do compilador

| # | Bug | Repro mínimo | Impacto no editor | Workaround atual |
|---|-----|--------------|-------------------|------------------|
| B1 | ~~Método declarado depois~~ — **CORRIGIDO** | `class Foo { call() helper() }` → `7` | ordem natural liberada (refatoração futura) | — |
| B10 | ~~**Campos do construtor primário dentro de métodos quebram**~~ — **CORRIGIDO (0.0.7)** | `class Connection(String host, Int porta) { hostInfo(): String { return host } }` → `NoSuchMethodError` | construtor primário liberado (F1 avança; overload de construtores chegou no 0.0.14) | — |
| B2 | ~~`++`/`--` em campo~~ — **CORRIGIDO (`9fb735f`)** | `pos++` → `1` | lexer usa `pos++` | — |
| B3 | ~~**`record` + lista: `for (var t in list)` e `var t = list.get(i)` quebram accessors**~~ — **CORRIGIDO (0.0.7)** | `for (var t in list2) { t.kind() }` → `kw` | Editor pode voltar a usar `record` como value object | — |
| B4 | ~~**`listOf()` vazio + `add` deixa elemento como `Object`**~~ — **CORRIGIDO (0.0.7)** | elemento pinado no `add` | fim do padrão sentinela + remove | — |
| B5 | ~~**`return` nu (sem valor e sem `;`) quebra o parser**~~ — **CORRIGIDO (0.0.7)** | devolve o default do tipo (`0`/`null`/`false`) | baixo | — |
| B6 | ~~Escapes `\uXXXX`~~ — **CORRIGIDO** | `"\u001b"` → ESC real no JVM **e no native** | `ansi()` virou `"\u001b[" + code + "m"` — cores no terminal nativo | — |
| B7 | ~~Field initializers de instância ignorados~~ — **CORRIGIDO (`48041b1`)** | `class A { Int n = 42 }` → `42` | `Theme` sem constructor (19 campos com init) | — |
| B8 | ~~**Default params não parseiam**~~ — **CORRIGIDO (0.0.7, JVM e Native)** | `new Ponto()` → `1,2`; `new Ponto(5)` → `5,2` | construtores com defaults idiomáticos | — |
| B9 | ~~**`json.decode<List<String>>` + `get(0)` devolve `Object`**~~ — **CORRIGIDO (0.0.7)** | `list.get(0).length()` → `3` | `ansi()` sem concat de tipagem | — |
| B14 | ~~**`KofRuntime.java` gerado: text block com escapes quebrados (JWT)**~~ — **CORRIGIDO (0.0.7)** | `jwt.create`/`jwt.verify` rodam | editor não precisa mais da cópia corrigida de build | — |
| B12 | ~~**Native: `undefined reference` para função que modifica campos de classe**~~ — **CORRIGIDO (verificado 0.0.14)** | `lightThemeFrom(t)` roda no ELF | editor nativo volta a ter tema light | — |
| B11 | ~~**`var x = new Classe()` (local) com field-init → VerifyError**~~ — **CORRIGIDO (verificado 0.0.14)** | `var t = new Theme()` + uso funciona | — | — |
| B13 | ~~**VerifyError `Bad local variable type` no editor completo**~~ — **SUMIU no 0.0.14** | demo das 7 seções roda na JVM | — | — |
| B18 | ~~**`String.replace(a, b)` com Strings → VerifyError**~~ — **CORRIGIDO (JVM)** | `s.replace("\r\n", "\n")` — resolução de overload agora escolhe `(CharSequence, CharSequence)` quando os args são String; `replace('a','b')` continua `(CC)` | `cleanTty()` do terminal (crlfToLf mantido por segurança) | pode usar `replace` de novo |

## Lista ativa — features faltando

| # | Feature | Impacto no editor | Notas |
|---|---------|-------------------|-------|
| F1 | ~~Construtor primário `class Foo(Int x)`~~ — **LIBERADO** | B10 corrigido (0.0.7) + overload de construtores (0.0.14) | usar `class Foo(Int x)` idiomático |
| F2 | ~~`args` de `main`~~ — **CORRIGIDO** | `main(String[] args)` + `kof run x.kf hello mundo` → `hello`/`mundo` | CLI do editor destravado | 
| F3 | **Módulos multi-arquivo** — **JVM/JS OK no 0.2.3-beta** (`import a.b.C` resolve) | `combine.sh` continua por enquanto; fim do combine **no native** | repro `hi from a.b.C` ✅ JVM/JS; **Native ainda `COMP001: undefined reference`** (R1-adjacente) |
| F4 | ~~Processos (executar programas)~~ — **IMPLEMENTADO (`process.run`)** | Terminal/runner nativos do editor (Estágio 3) destravados | `var r = process.run("git", "status")` → Result(stdout, stderr, exitCode); virtual threads no 0.0.14 |
| F5 | **`spawn` no target Native** | — | `spawn`/`await` ✅ JVM/JS no 0.2.3-beta; Native ainda `CONC001` |
| F6 | ~~**JSON de objetos no NativeBackend**~~ — **FECHADO no 0.2.3-beta** | `json.encode`/`decode<T>` de `record` | repro `pt: 1,2` / `enc: {"x":3,"y":4}` no native (JSN002 fechado) |
| F7 | ~~String API no NativeBackend~~ — **CORRIGIDO** | length/charAt/substring etc. | ver histórico |
| F8 | ~~Binding de janela~~ — **CHEGOU (`a8d0f3f`)** | `kof.ui Window/Label`: `Window("título")`, `w.title`, `w.bind(label)`, `w.show()`, `w.close()`; 0.0.14 adicionou size/font/bold/color e multi-janela; a UI renderiza por KofJS no webview nativo | a janela nativa do editor pode migrar para `Window` + `bin/kof-webview` |
| F9 | ~~`kof_platform` no runtime JS~~ — **RESOLVIDO (runtime embarcado)** | IO delega para Java (`KofJsRunner`); browser continua DOM shim | J2 fechado |

## Bugs do KofJS (backend JS)

| # | Bug | Repro | Impacto |
|---|-----|-------|---------|
| J1 | ~~`__kof_entry()` nunca invocado~~ — **CORRIGIDO** | hello/for-in rodam no node | — |
| J4 | ~~**Editor quebra no JsBackend**~~ — **RESOLVIDO (verificado 0.0.14, com workarounds J7 + Buffer)** | `node Default.mjs` roda as 7 seções do editor; paridade byte a byte com a JVM | manter os workarounds até J7/B19 serem corrigidos upstream |
| J5 | ~~**`kof-runtime.mjs` gerado com `\n` literais não escapados**~~ — **CORRIGIDO (verificado 0.0.14)** | `node --check` passa nos `.mjs` do editor | — |
| J2 | ~~IO JS: `kof_platform` não ligado~~ — **RESOLVIDO** | IO delega para Java via `KofJsRunner`; `kof-runtime-io.mjs` cobre filesystem/stdin/stdout | IO funciona no runtime embarcado |
| J3 | ~~`handleConstructorCall`~~ — **CORRIGIDO** | `class Token` + `new` compila e roda no node (`kw`) | — |
| J6 | ~~for-loop quebrado (`KofJS: for-loop expected Label(end)`)~~ — **RESOLVIDO (≤0.0.14)** | while/for/do-while/break/continue ✅ nos 3 targets (`backend-parity.md`) | front web/KofJS destravado |

## Novidades do Kof (desta checagem)

| Item | Evidência |
|------|-----------|
| **`kof.ui` completa** (`9538e83`/`a8d0f3f`) | `Color`, `Theme`, `Window`, `Label` — UI declarativa; JVM no-op, JS = DOM |
| **`kof-debug` MVP** (`9b46610`) | breakpoints por linha Kof + stack trace (DAP) |
| **`kof.security`** (`d36ce92`) | password hash, JWT, crypto — contém o bug do B14 |
| **`main(args)`** (F2) | já registrado — CLI do editor destravado |

## Corrigidos na última atualização

| Item | Evidência |
|------|-----------|
| **`\uXXXX` escapes** (`48041b1`) | `"\u001b"` → ESC real no JVM e no **native** — `ansi()` sem JSON decode; **o terminal nativo ganhou cores** |
| **Field initializers de instância** (`48041b1`) | `Theme` agora é 19 campos com init, **sem constructor** — `darkTheme() = new Theme()` |

## Corrigidos nesta atualização

| Item | Evidência |
|------|-----------|
| **Hex literals** `0xFF6750A4` | `kof run` retorna o Int correto; editor usa `0xFF282A36` etc. no `Theme` |
| **Bitwise** `>>`/`&` | `red()/green()/blue()` do `Color` funcionam; `colorStr()` do editor usa |
| **`static` fields com init** | `class Colors { static Int primary = 0xFF... }` funciona |
| **KofJS — entry invocado** | `node Default.mjs` imprime (`hello kofjs`, `42`, `a b c`) |
| **Semântica `new` para classes** | construtor call sem `new` vira bytecode inválido — o training já usa `new User(...)`; editor agora usa `new` em todas as classes (records continuam sem) |

## Corrigidos no 0.0.7 (verificados com repros)

| Bug | Evidência |
|-----|-----------|
| **B2** `++` em campo | `pos++` → `1` (JVM) |
| **B3** record + for-in + accessors | `for (var t in list2) { t.kind() }` → `kw` |
| **B9** `json.decode<List<String>>` + `get(0)` | `list.get(0).length()` → `3` |
| **B10** construtor primário em métodos | `new Connection("mel", 80).hostInfo()` → `mel` |
| **B14** JWT text block | `jwt.create`/`jwt.verify` rodam (iat/exp corretos) |
| **B18** `String.replace(String,String)` (JVM) | `replace("\r\n", "\n")` → `[a\nb\nc]`; `replace('a','b')` segue ok — overload resolve `(CharSequence, CharSequence)` para String |
| **B4** `listOf()` vazio + `add` | `listOf()` + `add(Token())` + `get(0).kind` → `kw` — elemento pinado no `add` |
| **B8** default params em construtor | `new Ponto()` → `1,2`; `new Ponto(5)` → `5,2` — JVM e Native (ELF); fix extra no call-site `new` (wrapper reduzido) |
| **B5** `return` nu em função tipada | `return` sem valor em `: Int` devolve o default (`0`/`null`/`false`) — antes: bytecode inválido (launcher reporta como "JavaFX não encontrado", que é só o VerifyError disfarçado) |
| **F4** KofProcess | `process.run` usado pelo editor (git, terminal, zenity) |

> ⚠️ `kof run --target native` está quebrado no CLI ("no main class found") — usar `kof build --target native` + executar o ELF (funciona).

## Editor — bugs nossos corrigidos (23/08)

| Item | Causa | Fix |
|------|-------|-----|
| **Nenhum botão funcionava (web)** | 2 linhas órfãs no `pageJs()` do `web.kf` (`}).catch(...)` + `}` após o handler do `git-diff-btn`) → `SyntaxError` no JS gerado → script inteiro descartado | linhas removidas; validado com `node --check` no `/editor.js` servido |
| **`kof serve` crashava no boot** | B18 (ver tabela acima) — `replace` com Strings | `crlfToLf()` manual |
| **Ícone do app sem kof.png** | binário `kof-webview` antigo (sem `gtk_window_set_icon_from_file`) **e** launcher gerava `/kof.png` (heredoc com `$DEST` nunca definido no script) | recompilado `Kof4j/native/webview/kof-webview.c`; path absoluto `$HOME/.local/share/kof-editor/kof.png` no launcher; `_NET_WM_ICON` verificado via xprop |
| **Tela de loading sem o ícone** | `editor-ui.html` mostrava um "K" estilizado | `kof.png` na tela de boot e no favicon |
| **"Abrir pasta..." com `prompt()`** | sem diálogo nativo | `/api/folderpick` (Kof) → `zenity --file-selection --directory` |

## Histórico — corrigidos (não precisam entrar no prompt)

| Item | Correção |
|------|----------|
| `==` de String em condição (if/while/for) | 0.0.5 |
| `new` em construtor suprimia `super()` | `34ffc9f` — ⚠️ **REGREDIU no `6812632`**; workaround ativo: `init()` de método no `LanguageRegistry` |
| `List<T>` como campo (descritor `kof.List`) | `34ffc9f` |
| `List<T>` de classes + `get()` sem CHECKCAST | `34ffc9f` |
| `private` em métodos de classe | 0.0.5 |
| `json.encode(Char)` corrompia a classe | 0.0.5 (agora encoda como número) |
| Colisão de campos com mesmo nome entre classes | 0.0.5 (não reproduz mais) |
| `null` em merge de frames | 0.0.5 (não reproduz mais) |
| String API no NativeBackend (`length`, `charAt`) | `5170c5d` |
| `kof.io` (File/Path/Directory), `kof.time`, `kof test`, `spawn` (JVM) | atual |

## Novidades do Kof (desta checagem, 0.0.14-alpha)

| Item | Evidência |
|------|-----------|
| **`kof debug` DAP MVP** | `KofDebug.java` + `JdwpClient.java`; breakpoints por linha Kof + call stack (Fases 1-3 do debugger) |
| **LSP com frontend real** | `kof lsp` → publishDiagnostics do compilador; hover/completion planejados |
| **`test "nome" { }`** | suíte estruturada nos 3 targets (G6 fechado) |
| **Otimizador de IR + `--release`** | constant folding/DCE sempre ativos |
| **Overload de construtores** | commit `83de878` |
| **kof.ui multi-janela + size/font/bold/color** | CHANGELOG 0.0.14 |
| **process.run consolidado** | virtual threads no process output (0.0.14) |
| **Android target Fase 1** | `--target android` gera projeto Maven + APK |
| **kof.http client + kof.mq / db SQLite nativo / orm completo / log+config nativos** | G2/G3/G1 fechados no Kof (contexto) |

## Prioridade sugerida (para você ajeitar)

1. **Reportar B19/B20/B23/B24/J7 ao Kof4j** — repros em `/tmp/opencode/kof-repros/` (nat2-nat6, s2/s8, j2/j4, mq4d-mq7d); workarounds ativos (`super()` explícito, `hasPrefix`, if/else no fim do loop)
2. **Alinhar VERSION × pom** (`0.0.14-alpha` vs `0.1.0-beta`) antes do próximo release
3. **F3 multi-arquivo**: estender o modelo de módulo do `check` para `build`/`run` → fim do `combine.sh`
4. **Migrar janela nativa para `Window` + `bin/kof-webview`** (F8) — widgets Link/Image/Icon/Font + observability/G9 disponíveis
5. **Run/Debug** — integrar `kof debug` (DAP stdio) ao painel do editor
6. **F6** — JSON objetos no native (JSN002 segue aberto)
## B17 — runtime trava com strings multibyte (0.0.5-alpha, REGRESSÃO GRAVE)

**Sintoma**: qualquer handler do KofHttpServer com body contendo bytes >127 (á, é, ç, —, →, emoji) trava em loop até o socket timeout (15s) e cai. O editor "não carrega texto": o textarea é transparente e o highlight via /tokenize nunca pinta em arquivos acentuados.

**Caso mínimo** (echo.kf):
```
fun pageHtml(): String { return "<html></html>" }
handle(String method, String path, String body): String {
    return "len=" + body.length() + ":" + body
}
```
`printf 'olá\n' | curl -X POST http://localhost:8767/ --data-binary @-` → trava (000 em ~15s).
Sem multibyte (`abc`) responde. O travamento ocorre até em `body.length()` — ou seja, no runtime (String interna), não no código Kof.

**Impacto no editor**: /tokenize, /api/read, /api/dir, /api/git, /api/diags — todos os POST com body multibyte. Arquivos com acento não pintam (texto invisível).

**Suspeita**: runtime 0.0.5 indexa String por BYTE (UTF-8) e alguma função (length/substring/contains) faz loop/avanço errado com bytes >127.

## B17 — RESOLVIDO (local, 23/08): multibyte no KofHttpServer

**Root cause**: `readRequest` compara `body.length()` (caracteres UTF-8) com o Content-Length (BYTES) — com acentos/emoji o char count fica menor que o byte count → loop esperando dados até o socket timeout (15s).

**Fix (1 linha)**, aplicado em /tmp/opencode/kof-build2 e instalado em ~/.local/share/kof-dev/lib:
```java
while (body.toString().getBytes(StandardCharsets.UTF_8).length < contentLength) {
```
Validado: `printf 'olá — ✓\n' | curl -X POST /tokenize` → tokens ok; web.kf inteiro (38KB com —) → 224KB de tokens.

> **UPDATE (23/08, 0.0.7):** o fix já está no upstream (`KofHttpServer.java:124`). ATENÇÃO: o `kof-cli` é um jar *shaded* — copiar só o `kof-compiler.jar` não atualiza o servidor; é preciso reinstalar o `kof-cli-0.0.7-alpha.jar` também. Verificado: `len=8:olá — ✓` no serve.

## F4 — IMPLEMENTADO no Kof novo (KofProcess)

`var r = process.run("git", "status", "--porcelain", "-b")` → Result(stdout, stderr, exitCode). Roda no cwd do serve. Usado no painel Source Control do editor (/api/gitstatus): branch + arquivos modificados coloridos.

## Notas do estudo do Kof 0.0.5 (rebuild e396561)

- **`fun` removido** da sintaxe canônica (funções por nome) — o parser ainda tolera `fun` (retrocompatível); o lexer do editor mantém "fun" como keyword.
- **int/string/bool** minúsculos e Int/String/Bool maiúsculos — ambos aceitos.
- `class User(String name)` record-style; default parameters; `handle(...)` com parse corrigido.
- **KofJS** (J6) ainda quebra no for: `KofJS: for-loop expected Label(end)` (JsBackend.parseLoop:739) — o editor estático segue esperando.
- **Content-Type** — agora **no upstream** (`77a955c`): `KofHttpServer.buildResponse` detecta `<!DOCTYPE`/`<html` → `text/html`, `:root`/`body {`/`<style` → `text/css`, `var `/`function `/`document.`/`async ` → `application/javascript`. Sem isso o navegador renderiza o editor como texto plano. Verificado: `/` → text/html, `/editor.css` → text/css, `/editor.js` → application/javascript.
- stdlib nova: kof.security (JWT), kof.web (routing/middleware), kof.config, kof.log, debugger (kof-debug).

## F10 — PTY / process.spawn com stdin vivo (30/08/2026)

**Bloqueio**: `process.run` é one-shot (aguarda fim, devolve stdout/stderr).
O terminal do editor não consegue stdin interativo — htop/vim não aceitam
teclas, `sudo` pede senha por fora, `readLine()` dentro de TUI trava.

**Impacto no editor**: terminal vira "executor de comandos" (histórico +
ANSI renderizado já cobrem 80% do uso dev), mas sem PTY real não há sessão
interativa.

**O que destrava (proposta para o Kof4j)**:
1. `process.spawn(cmd) -> Handle` com `write(String)`, `read(): String`,
   `resize(cols, rows)`, `kill()` — pipes vivos no JVM (ProcessBuilder) e
   nativo (fork/exec + pipes; PTY completo via forkpty)
2. `kof.pty(cmd)` (opcional, fase 2): `openpty` no native,(ptmx/jdk Pty?) na
   JVM — aí htop/vim rodam de verdade dentro do editor
3. Com (1) o editor já pode rodar o emulador de terminal **escrito em Kof**
   (parser CSI/SGR — mesmo design do `Lexer` do editor: scanner de escapes,
   grid `List<List<Cell>>`, render por spans)

Repro do gap:
```kof
var p = process.run("vim", "x.txt")   // trava 15s até o socket timeout
// não há como mandar teclas para o stdin do vim
```
