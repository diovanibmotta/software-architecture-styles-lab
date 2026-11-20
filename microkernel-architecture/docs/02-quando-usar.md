# 02 — Quando usar (e quando não usar)

## Resumo da decisão

Use microkernel quando o produto tem **um fluxo estável e um conjunto de variações que cresce e muda em ritmos
diferentes do núcleo**, e você quer estender sem tocar no que já funciona.

```mermaid
flowchart TD
    A{Há um fluxo central estável<br/>com variações bem delimitadas?} -- não --> X1[Considere em camadas<br/>ou baseada em serviços]
    A -- sim --> B{As variações mudam/crescem<br/>mais rápido que o núcleo?}
    B -- não --> X2[Estratégias internas<br/>no mesmo módulo bastam]
    B -- sim --> C{Variações podem ser definidas por<br/>um contrato único e pequeno?}
    C -- não --> X3[Cada extensão exige integração<br/>própria: serviços / eventos]
    C -- sim --> D{Precisa de isolamento forte<br/>entre plug-ins ou escala independente?}
    D -- sim --> E([Microkernel com plug-ins REMOTOS<br/>ou outro estilo distribuído])
    D -- não --> OK([✅ Microkernel com plug-ins embutidos])
```

## Sinais de que o estilo é adequado

- **Produtos com extensões de terceiros ou de times diferentes**: IDEs (Eclipse, VS Code), navegadores, ferramentas de CI/CD.
- **Regras por cliente/região/parceiro** sobre um processo comum: cálculo de impostos, sinistros por estado, gateways de pagamento, **transportadoras**.
- Quer **lançar variações sem redeploy do núcleo** e sem regressão no que já está em produção.
- Time do núcleo ≠ time dos plug-ins (ou fornecedores externos).

## Sinais de que NÃO é adequado

- A "variação" é só um `if` — uma interface e três classes dentro do mesmo módulo (padrão *Strategy*) são mais simples.
- Plug-ins precisam **conversar entre si** ou compartilhar muito estado: o contrato explode e o núcleo vira um monólito.
- Requisitos de **escala e elasticidade extremas por extensão** — o estilo, sozinho, escala o núcleo como um todo.
- Plug-ins não confiáveis rodando na mesma JVM sem sandbox.

## Comparação rápida

| | Microkernel | Em camadas | Microsserviços |
|---|---|---|---|
| Extensibilidade | ★★★★ por plug-in | ★ | ★★★★ por serviço |
| Isolamento de falhas | ★★ (embutido) / ★★★★ (remoto) | ★ | ★★★★★ |
| Simplicidade operacional | ★★★ | ★★★★★ | ★ |
| Unidade de deploy | núcleo + jars | uma só | muitas |

É comum **combinar**: microkernel *dentro* de um serviço de um sistema maior (por exemplo, o serviço de frete de uma
arquitetura de microsserviços).
