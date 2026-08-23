# 05 — Terminal e Execução

## Visão

O Kof Editor tem um **terminal nativo** — não um emulador embutido como o
xterm.js (terminal web popular). "Nativo" aqui tem dois sentidos:

1. **Escrito em Kof**: o terminal é um módulo do editor, não uma biblioteca
   JavaScript embutida.
2. **Usa o runtime do target**: na JVM, processos via interop; no nativo,
   syscalls do runtime Kof (`kof_net_*`, fork/exec); na web, workers.

## Split vertical (a única "integração de IA")

O editor não embute IA. O que ele oferece é um **terminal dividido na
vertical** (editor | terminal), onde qualquer ferramenta roda como processo
comum — inclusive o **opencode**. A filosofia: IA é ferramenta do usuário,
o editor fornece o terminal e deixa o caminho livre.

## Comandos nativos

O editor traz run/build/check como comandos do próprio binário, sempre
apontando para o toolchain Kof:

| Comando | Ação | Equivalente CLI |
|---------|------|-----------------|
| **Run** | executa o arquivo atual | `kof run <file.kf>` |
| **Build** | compila o projeto | `kof build <dir> --target <t>` |
| **Check** | valida sem gerar código | `kof check <file.kf>` |
| **Target** | seleciona jvm/native/web | flag `--target` |

A barra de status mostra o target atual. A saída do build alimenta o
diagnostics: um erro do compilador vira uma marca na linha correspondente
(clique → navega).

## Arquitetura (Estágio 3)

```text
┌──────────────────────────────────────────────┐
│           UI do terminal (por target)        │
├──────────────────────────────────────────────┤
│           TermSession                       │
│   fila de comandos · scrollback · cwd        │
├──────────────────────────────────────────────┤
│           ProcessRunner                     │
│   start/kill · stdout/stderr → painel        │
├──────────────────────────────────────────────┤
│   JVM: java.lang.Process   Native: syscalls  │
└──────────────────────────────────────────────┘
```

## Estado atual

- Estágio 1 entrega o pipeline de **saída**: o próprio demo imprime
  resultados estruturados no terminal.
- O terminal web executa comandos reais via `process.run` (Kof) usando o
  shell do usuário (`config.env("SHELL")`, fallback `bash`) — rota
  `/api/exec` do front.
- Comandos TUI (htop, top, vim, less, more, nano, man, watch) rodam via
  `script -qec` e a saída é limpa em Kof: `cleanTty()` remove sequências
  ESC e `crlfToLf()` normaliza CRLF → LF (o `String.replace` do Kof ainda
  gera bytecode inválido — ver [08 — Restrições](08-restricoes-kof.md), B18).
- O `ProcessRunner` depende de execução de processos no Kof
  (interop JVM ou syscalls nativos) — ver [02 — Etapas](02-etapas.md),
  Estágio 3.

## Regras

- Terminal é sempre **processo real**: nada de simulação em JavaScript.
- Split é sempre **vertical** (editor à esquerda, terminal à direita),
  reforçando o foco em editor de código.
- A saída de `run`/`build` é texto puro; programas TUI são suportados via
  `script` com a saída limpa de escapes — o editor não emula terminal
  full-screen.