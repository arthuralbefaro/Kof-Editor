# 08 — O que falta no Kof para o editor (fonte do prompt)

> Última verificação: 0.0.5-alpha (árvore atual — hex, bitwise, static init, KofJS 100% p/ programas simples).
> Cada item tem repro mínimo e impacto no editor. Corrigidos ficam no histórico.

## Lista ativa — bugs do compilador

| # | Bug | Repro mínimo | Impacto no editor | Workaround atual |
|---|-----|--------------|-------------------|------------------|
| B1 | ~~Método declarado depois~~ — **CORRIGIDO** | `class Foo { call() helper() }` → `7` | ordem natural liberada (refatoração futura) | — |
| B10 | **Campos do construtor primário dentro de métodos quebram** | `class Connection(String host, Int porta) { hostInfo(): String { return host } }` → `NoSuchMethodError: String Connection.hostInfo()` (acesso direto no main funciona) | construtor primário ainda não usável com métodos | `public constructor` com `this.x = x` |
| B2 | ~~`++`/`--` em campo~~ — **CORRIGIDO (`9fb735f`)** | `pos++` → `1` | lexer usa `pos++` | — |
| B3 | **`record` + lista: `for (var t in list)` e `var t = list.get(i)` quebram accessors** | `record Token(...)` + `listOf(t0)` + `for (var t in list) { t.kind() }` → `NoSuchMethodError: 'java.lang.Object Token.kind()'` | Editor não usa `record` (usa `class` com campos); `Token t = list.get(1)` (declaração tipada) funciona | `class` no lugar de `record` |
| B4 | **`listOf()` vazio + `add` deixa elemento como `Object`** | `var list = listOf()` + `list.add(Token(...))` + `list.get(0).kind` → VerifyError | Padrão `listOf(Token("","",0,0))` + `list.remove(0)` | sentinela + remove |
| B5 | **`return` nu (sem valor e sem `;`) quebra o parser** | `if (x) { return }` → `Expected expression` | Baixo | `return;` |
| B6 | ~~Escapes `\uXXXX`~~ — **CORRIGIDO** | `"\u001b"` → ESC real no JVM **e no native** | `ansi()` virou `"\u001b[" + code + "m"` — cores no terminal nativo | — |
| B7 | ~~Field initializers de instância ignorados~~ — **CORRIGIDO** | `class A { Int n = 42 }` → `42` | `Theme` sem constructor (19 campos com init) | — |
| B7 | **Field initializers de instância são ignorados silenciosamente** | `class A { Int n = 42 }` → `n == 0` | `Theme` e demais usam construtor com params | construtor |
| B8 | **Default params não parseiam** | `constructor(Int x = 12)` → erro de parse | — | aspiracional do learn/35 |
| B9 | **`json.decode<List<String>>` + `get(0)` devolve `Object`** | `var list = json.decode<List<String>>(raw)` + `list.get(0).length()` → VerifyError | `ansi()` usa `"" + get(0)` para tipar | concat |
| B14 | **`KofRuntime.java` gerado: text block com escapes quebrados (JWT)** | o template do JvmRuntime usa `"\"iat\":"` no text block → o arquivo gerado tem `""iat":"` (javac falha); o correto no text block é `\\"` para gerar `\"` | editor ficava sem runtime | corrigido na cópia de build (2 linhas do JWT); revisar no Kof4j |
| B12 | **Native: `undefined reference` para função que modifica campos de classe** | `lightThemeFrom(Theme t): Theme { t.name = ... }` → `ld: undefined reference to lightThemeFrom` (símbolo não emitido; JVM/Web ok) | editor nativo usa o demo sem tema light | — |
| B11 | **`var x = new Classe()` (local) com field-init → VerifyError** | `var t = new Theme()` (Theme com field init) + uso → `Bad local variable type`; `return new Theme()` e `lightFrom(darkTheme())` funcionam | `lightTheme()` refatorado para retorno direto/param | retorno direto |
| B18 | **`String.replace(a, b)` com Strings → VerifyError** | `s.replace("\r\n", "\n")` → `Bad type on operand stack` — o compilador resolve o overload `replace(CC)` mas empurra as **Strings** na pilha | `cleanTty()` do terminal quebrava o `kof serve` no boot (editor inteiro morto, "nenhum botão funcionava") | `crlfToLf()` manual (substring + `==`, sem `replace`) |

## Lista ativa — features faltando

| # | Feature | Impacto no editor | Notas |
|---|---------|-------------------|-------|
| F1 | **Construtor primário `class Foo(Int x)`** — ⚠️ **parcial** | campos acessam direto no main (`c.host` ✓); acesso dentro de métodos quebra (B10) | bloqueado por B10 |
| F2 | ~~`args` de `main`~~ — **CORRIGIDO** | `main(String[] args)` + `kof run x.kf hello mundo` → `hello`/`mundo` | CLI do editor destravado | — |
| F3 | **Módulos multi-arquivo** (resolução de símbolos entre arquivos) | `combine.sh` concatena 9 módulos em 1 arquivo | fim do combine |
| F4 | **Processos (executar programas)** | Terminal/runner nativos do editor (Estágio 3) | hoje o host Java executa via `ProcessBuilder` |
| F5 | **`spawn` no target Native** | — | o próprio Kof documenta `CONC001` |
| F6 | **JSON de objetos no NativeBackend** | `json.encode` de `Token`/`LanguageEntry` (hoje: JSON manual nas funções `*ToJson` do editor) | JVM suporta; native dá `JSN002` |
| F7 | **String API no NativeBackend** | — | ✅ já corrigido (length/charAt) — ver histórico |
| F8 | ~~Binding de janela~~ — **CHEGOU (`a8d0f3f`)** | `kof.ui Window/Label`: `Window("título")`, `w.title`, `w.bind(label)`, `w.show()`, `w.close()`; JVM/Native são no-ops, a UI é KofJS (DOM) | a janela nativa do editor usará `Window` + o webview nativo do Kof | — |
| F9 | **`kof_platform` no runtime JS** (node: IO, navegador: DOM) | IO no browser (J2) e launcher web | — |

## Bugs do KofJS (backend JS)

| # | Bug | Repro | Impacto |
|---|-----|-------|---------|
| J1 | ~~`__kof_entry()` nunca invocado~~ — **CORRIGIDO** | hello/for-in rodam no node | — |
| J4 | ~~Editor quebra no JsBackend~~ — **AVANÇOU** | o editor completo agora gera `Default.mjs` + `index.html` (mount `#kof-root`) | falta o runtime JS compilar | |
| J5 | **`kof-runtime.mjs` gerado com `\n` literais não escapados** | `const html = "<!DOCTYPE html>\n<html>..."` → SyntaxError no node | runtime JS do KofJS | — |
| J2 | **IO JS: `kof_platform` não ligado** | `File(...).writeText` no JS → `kof_platform.writeText is not a function` (falta o runtime node/platform) | IO no browser |
| J3 | ~~`handleConstructorCall`~~ — **CORRIGIDO** | `class Token` + `new` compila e roda no node (`kw`) | — |

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

## Prioridade sugerida (para você ajeitar)

1. **B13** — regressão pós-`9fb735f`: `VerifyError: Bad local variable type` em múltiplos métodos (`main`, `diagList`) com o compilador atual; o editor usa o build `9fb735f` (sem debug) até estabilizar
2. **B2** — `++` em campo **instável** (regressão no `6812632`; lexer no workaround)
2. **super() com `new` em construtor** — regrediu no `6812632` (registry usa `init()`)
3. **B10** — campos do construtor primário em métodos (destrava F1)
4. **B3/B4/B9** — records/listas tipadas (value objects de verdade)
5. **F6** — JSON objetos no native (fim do JSON manual)
6. **F8** — binding `window()` (editor 100% Kof)
7. **F2/F3/F4/F5/F9** — args, módulos, processos, spawn native, platform JS
8. **B5/B8** — parser (return nu, default params)
9. **J4/J2** — KofJS com o editor completo e IO
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

## F4 — IMPLEMENTADO no Kof novo (KofProcess)

`var r = process.run("git", "status", "--porcelain", "-b")` → Result(stdout, stderr, exitCode). Roda no cwd do serve. Usado no painel Source Control do editor (/api/gitstatus): branch + arquivos modificados coloridos.

## Notas do estudo do Kof 0.0.5 (rebuild e396561)

- **`fun` removido** da sintaxe canônica (funções por nome) — o parser ainda tolera `fun` (retrocompatível); o lexer do editor mantém "fun" como keyword.
- **int/string/bool** minúsculos e Int/String/Bool maiúsculos — ambos aceitos.
- `class User(String name)` record-style; default parameters; `handle(...)` com parse corrigido.
- **KofJS** (J6) ainda quebra no for: `KofJS: for-loop expected Label(end)` (JsBackend.parseLoop:739) — o editor estático segue esperando.
- Meu fix de Content-Type (text/html/css/js) continua ausente do upstream — reaplicado no build local.
- stdlib nova: kof.security (JWT), kof.web (routing/middleware), kof.config, kof.log, debugger (kof-debug).
