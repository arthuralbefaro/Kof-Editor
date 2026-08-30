# 06 — Targets

O Kof compila um único frontend para vários backends. O Kof Editor usa essa
capacidade para ser entregue em três mundos sem mudar o núcleo.

## O que é comum (100% do núcleo)

Lexer, tokens, registry, theme, highlight, buffer, diagnostics, comandos —
**tudo em Kof, agnóstico de target**. O que muda é só a camada de sistema:

```text
Kof Editor (um código-fonte)
    │
    ├── JVM      → editor de desktop (UI via interop Java), server/CLI
    ├── Native   → binário ELF autônomo (estático, sem dependências)
    └── Web      → editor no navegador (KofJS/WASM)
```

## JVM (estado atual ✅)

- O editor **roda hoje** na JVM: `kof build --target jvm`.
- JSON de objetos, strings, concat — tudo disponível.
- Caminho natural para o Estágio 4 (UI via interop com Java: Swing/JavaFX
  ou render próprio sobre `java.awt`/`javafx`).

## Native ⏳ (regressão no 0.2.3-beta)

- O backend nativo gera ELF x86-64 (link dinâmico).
- No **Kof 0.0.14** o núcleo do editor rodava idêntico no ELF. No
  **0.2.3-beta** o scanner completo (`Lexer.tokenize`) **cracha com
  `array index out of bounds` na seção [2]** (regressão do NativeBackend —
  R1; ver [08 — Restrições](08-restricoes-kof.md)). Loops simples de
  `substring` isolados ainda funcionam; o `tokenize()` inteiro não.
- Ganhos no native desde 0.0.14: JSON de objetos (`JSN002` fechado),
  `enum`, `Set`, pattern matching, higher-order, `spawn/await` (JVM/JS).
- Mismatches semânticos que ainda atrapalham: `substring(end>len)` crasha
  (R5) e token de erro + identificador pode hangar (R6).
- Com o editor compilando no native: um binário, zero dependências.

## Web / KofJS ✅ (núcleo roda; front ainda via `kof serve`)

- O KofJS é **alpha funcional** no Kof 0.2.3-beta: a mesma IR gera ESM
  (ES2022+) executado na GraalJS **embarcada** (sem Node.js), com source
  maps. O **núcleo do editor roda no target `js`** (7 seções idênticas à
  JVM — revalidado 29/08/2026).
- `kof run editor.kf --target js` / `kof build --target js`.
- O front do editor ainda é servido por `kof serve` (fase 1); a migração
  para um front KofJS declarativo (`kof.ui`) é o próximo passo
  (ver [09 — Contrato KofJS](09-kofjs.md)).
- Persistência local (IndexedDB via interop) e web worker seguem pendentes.

## Regra de ouro

> Nenhum módulo do editor pode depender de uma capacidade que só existe em
> um target — a menos que seja explicitamente isolado e documentado.

Exemplo aplicado: o registry foi escrito sem JSON de objetos (que é JVM-only
hoje) justamente para não vazar para o nativo.

## Matriz atual (núcleo, 0.2.3-beta)

| Capacidade | JVM | Native | Web |
|------------|-----|--------|-----|
| Lexer Kof (novo, 0.2.3-beta) | ✅ | ✅ | ⏳ |
| Lexer JSON | ✅ | ✅ | ⏳ |
| Registry | ✅ | ✅ | ⏳ |
| Buffer | ✅ | ✅ | ⏳ |
| Núcleo completo (7 seções) | ✅ | ❌ crash [2] (R1) | ✅ |
| JSON (String/Int/List) | ✅ | ✅ | ✅ |
| JSON (objetos) | ✅ | ✅ (fechado 0.2.3-beta) | ✅ |
| ANSI no highlight | ✅ | ✅ | ⏳ |
| UI | ⏳ Estágio 4 (ou `kof.ui` + webview) | ⏳ Estágio 6 | ⏳ Estágio 7 |

\* Native: o scanner completo (`tokenize`) cracha no 0.2.3-beta (R1);
`substring(end>len)` crasha (R5); `#`+identificador pode hangar (R6) — ver 08.
## Escopo de desenvolvimento atual (23/08/2026)

O foco atual de desenvolvimento é **JVM e Native para Linux**. O target
**web** é uma capacidade real e funcional (o front do editor roda via
`kof serve` + `kof-webview`), mas está **fora do escopo atual de
desenvolvimento**: sem release web, sem navegador como alvo de entrega.
No 0.2.3-beta o for-loop (J6) e o núcleo completo no KofJS funcionam; o
bloqueio do native virou o regressão R1 (scanner cracha no ELF). A stack web
permanece como o caminho de execução de desenvolvimento (a janela do editor
roda em `kof-webview`, que é o mesmo front servido).
