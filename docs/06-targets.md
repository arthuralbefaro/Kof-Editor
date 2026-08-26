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

## Native ⏳ (String API resolvida; falta validar o editor completo)

- O backend nativo gera ELF x86-64 estático.
- A API de String nativa (`length`, `charAt`, `substring`, concat...) foi
  corrigida no Kof — o bloqueio original do lexer caiu; **repro do editor
  completo no native pendente** (B11/B12 podem afetar: ver
  [08 — Restrições](08-restricoes-kof.md)).
- JSON de objetos também é JVM-only hoje (JSN002) — o editor usa JSON de
  String/Int/List, que já funciona nativo (o núcleo foi escrito para não
  depender de JSON de objetos).
- Com o editor compilando no native: um binário, zero dependências.

## Web ⏳ (aguarda backend Web do Kof — KofJS)

- O mesmo editor com backend de renderização DOM.
- O lexer/análise roda em worker (sem travar a UI).
- Persistência local via IndexedDB (interop).
- O terminal web é compilado para Web Worker + pipes — ainda terminal
  nativo, apenas com transporte web.

## Regra de ouro

> Nenhum módulo do editor pode depender de uma capacidade que só existe em
> um target — a menos que seja explicitamente isolado e documentado.

Exemplo aplicado: o registry foi escrito sem JSON de objetos (que é JVM-only
hoje) justamente para não vazar para o nativo.

## Matriz atual

| Capacidade | JVM | Native | Web |
|------------|-----|--------|-----|
| Lexer Kof | ✅ | ✅* | ⏳ |
| Lexer JSON | ✅ | ✅* | ⏳ |
| Registry | ✅ | ✅ | ⏳ |
| Buffer | ✅ | ✅* | ⏳ |
| JSON (String/Int/List) | ✅ | ✅ | ⏳ |
| JSON (objetos) | ✅ | ❌ (JSN002; arrays ✅) | ⏳ |
| ANSI no highlight | ✅ (`\u001b` nativo da linguagem) | ✅ (`\u001b` no native — B6) | ⏳ |
| UI | ⏳ Estágio 4 (ou `kof.ui` + webview) | ⏳ Estágio 6 | ⏳ Estágio 7 |

\* compila e executa (String API nativa corrigida); repro do editor completo pendente (ver 08).
## Escopo de desenvolvimento atual (23/08/2026)

O foco atual de desenvolvimento é **JVM e Native para Linux**. O target
**web** é uma capacidade real e funcional (o front do editor roda via
`kof serve` + `kof-webview`), mas está **fora do escopo atual de
desenvolvimento**: sem release web, sem navegador como alvo de entrega.
O bloqueio J6 (for-loop no KofJS) foi **resolvido** no Kof (0.0.14:
while/for/do-while ✅ nos 3 targets), o que volta a permitir manutenção do
backend KofJS. A stack web permanece como o caminho de execução de
desenvolvimento (a janela do editor roda em `kof-webview`, que é o
mesmo front servido).
