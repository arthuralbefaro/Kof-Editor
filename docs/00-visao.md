# 00 — Visão

## O que é o Kof Editor

O **Kof Editor** é um editor de código escrito **inteiramente em Kof** — a
linguagem de programação multi-target que compila para JVM, binário nativo e
web a partir de um único frontend.

Ele nasceu da pergunta: *"se editores maduros já existem, por que outro editor?"*

Porque os editores modernos resolveram os problemas de 2015 com uma
arquitetura de navegador embutido + extensões JavaScript. A
arquitetura de extensões é onde o ecossistema vive — e também onde ele
morre: cada linguagem, cada feature, cada integração é uma extensão
separada, com sua própria manutenção, seus próprios bugs e sua própria
latência.

O Kof Editor acredita em outra coisa:

> **O editor deve conhecer a linguagem nativamente. Sem extensões. Sem
> servidores externos. Sem JavaScript embutido.**

## A diferença em uma frase

> Editores modernos: um navegador que edita texto e delega tudo a extensões.
>
> Kof Editor: um editor cujo núcleo — linguagens, highlight, autocomplete,
> erros, terminal, build — é **parte do próprio binário**.

## Sem extensões, sem IA

- **Sem extensões**: todo reconhecimento de linguagem (sintaxe, erros,
  autocomplete) é nativo, compilado junto com o editor.
- **Sem Copilot e sem IA integrada**: o editor não embute modelos. O máximo
  de integração com IA é um **terminal dividido na vertical** onde o
  usuário roda a ferramenta que quiser — inclusive o opencode.

## Uma linguagem, vários mundos

O editor segue a filosofia do Kof: um único código-fonte, múltiplos targets.

```text
Kof Editor (.kf)
    │
    ▼
Kof Compiler
    │
    ▼
Kof IR
    │
    ├──► JVM      (editor de desktop / server)
    ├──► Native   (binário autônomo, ELF)
    └──► Web      (editor no navegador, sem extensões)
```

O mesmo lexer, o mesmo reconhecimento de linguagem, o mesmo terminal: o que
muda é o backend de renderização e de sistema operacional.

## Ambicioso por design

Este projeto é deliberadamente ambicioso: reimplementar em Kof o que hoje
exige milhões de linhas de TypeScript + um mercado de extensões. A ambição
é viável porque o Kof carrega no compilador o que outros editores carregam no
ecossistema:

- análise semântica, types e diagnostics **nativos da linguagem**;
- JSON, serialização e execução **nativos**;
- targets JVM/nativo/web a partir do mesmo frontend.

O editor não briga com a linguagem: ele **é a linguagem**, dogfooding total
— a mesma gramática que o editor reconhece é a gramática do compilador que o
construiu.

## O que NÃO é

- Não é um clone de nenhum editor existente (a pasta `inspiracao/` é referência de
  UX/arquitetura, não código a portar).
- Não é um Electron com outra capa.
- Não é um IDE de extensões com outro nome.
- Não é um projeto que espera o Kof "ficar pronto" para começar: cada
  estágio entrega valor com o que a linguagem já oferece hoje.