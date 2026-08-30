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

## R1 — FECHADO (30/08/2026, Kof fixes-for-kof-editor)

Duas causas compostas:
1. **GC mark-sweep no Native** (commit `9df8f72`, marcação transitiva +
   sweep conservador) — o scanner grande alocava o suficiente para o GC
   antigo corromper memória (`array index out of bounds` com 1 char a mais
   no input era o sintoma)
2. **Forward reference de função top-level no Native** (commit `85855c5`) —
   callee declarado depois do caller caía no fallback não-mangled →
   `undefined reference` no ld (repro: `outer()` chama `inner()` declarado
   depois → `x5!`)

Com ambos, a **paridade byte a byte JVM == Native == JS foi restaurada**
(7 seções do `editor.kf` idênticas nos 3 targets).

## F10 — process.spawn com stdin vivo (FECHADO na JVM)

`var h = process.spawn("sh", "-c", "...")` → `h.write(s)` / `h.readLine()` /
`h.exitCode()` / `h.kill()` / `h.alive()` (commit `85855c5` + `a295fc4`).
JVM: ProcessBuilder com pipes vivos + registry de handles (Long opaco).
Native: gap explícito `PROC001` em compile-time (fork/exec com pipes é o
próximo passo do NativeRuntime). Com o F10, o terminal do editor já pode
conversar com processos; PTY completo (TUI interativo) segue como F10-fase 2.
