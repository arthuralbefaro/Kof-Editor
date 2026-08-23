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

## Native ⏳ (aguarda API de String no NativeBackend)

- O backend nativo gera ELF x86-64 estático.
- Hoje o editor compila a maior parte para nativo, mas o runtime ainda não
  tem `String.length`/`substring` — o lexer depende deles. Bloqueio
  documentado em [08 — Restrições](08-restricoes-kof.md).
- JSON de objetos também é JVM-only hoje (o editor usa JSON de
  String/Int/List, que já funciona nativo — o núcleo foi escrito para não
  depender de JSON de objetos).
- Quando a String API nativa existir: um binário, zero dependências.

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
| JSON (objetos) | ✅ | ❌ | ⏳ |
| ANSI no highlight | ✅ (via `json.decode` de `\u001b`) | ⏳ | ⏳ |
| UI | ⏳ Estágio 4 | ⏳ Estágio 6 | ⏳ Estágio 7 |

\* compila, mas depende da String API nativa para executar (ver 08).
## Escopo de desenvolvimento atual (23/08/2026)

O foco atual de desenvolvimento é **JVM e Native para Linux**. O target
**web** é uma capacidade real e funcional (o front do editor roda via
`kof serve` + `kof-webview`), mas está **fora do escopo atual de
desenvolvimento**: sem release web, sem navegador como alvo de entrega,
sem manutenção ativa do backend KofJS até o Kof destravar o for-loop
(J6). A stack web permanece como o caminho de execução de
desenvolvimento (a janela do editor roda em `kof-webview`, que é o
mesmo front servido).
