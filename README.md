# Software Architecture Styles Lab

Laboratório prático (monorepo) para **implementar e testar cada estilo de arquitetura** descrito no livro
[*Fundamentos da Arquitetura de Software*](https://www.oreilly.com/library/view/fundamentals-of-software/9781492043447/)
(Mark Richards e Neal Ford).

Cada estilo vive em um **subprojeto independente**, com:

- um caso de uso em que o estilo realmente faz sentido;
- implementação funcional (Java), testes e como executar;
- documentação detalhada em português, com diagramas [Mermaid](https://mermaid.js.org/): conceitos, **quando usar / quando evitar**, trade-offs e decisões de design.

## Estilos

| # | Estilo (livro) | Subprojeto | Caso de uso | Status |
|---|----------------|------------|-------------|--------|
| 1 | Arquitetura baseada em espaço (*Space-Based*) | [`space-based-architecture`](space-based-architecture) | Venda relâmpago de ingressos (flash sale) | ✅ Implementado |
| 2 | Em camadas (*Layered*) | `layered-architecture` | — | ⏳ Planejado |
| 3 | Pipeline | `pipeline-architecture` | — | ⏳ Planejado |
| 4 | Microkernel | [`microkernel-architecture`](microkernel-architecture) | Motor de cotação de frete com transportadoras como plug-ins (hot load) | ✅ Implementado |
| 5 | Baseada em serviços (*Service-Based*) | `service-based-architecture` | — | ⏳ Planejado |
| 6 | Orientada a eventos (*Event-Driven*) | `event-driven-architecture` | — | ⏳ Planejado |
| 7 | Orientada a serviços orquestrada (*Orchestration-Driven SOA*) | `orchestration-driven-soa-architecture` | — | ⏳ Planejado |
| 8 | Microsserviços (*Microservices*) | `microservices-architecture` | — | ⏳ Planejado |

> Os nomes dos subprojetos pendentes são tentativos e podem mudar quando forem implementados.

## Convenções do monorepo

- Um subprojeto = um estilo, **autocontido** (build, testes, `docker-compose` e docs próprios).
- Todo subprojeto tem um `README.md` com: visão geral, como executar, estrutura e links para `docs/`.
- Os `docs/` seguem a mesma estrutura em todos os estilos:
  1. conceito e componentes do estilo;
  2. **quando usar** (e quando não usar);
  3. arquitetura da implementação (diagramas);
  4. decisões, trade-offs e limitações;
  5. características de arquitetura (avaliação estilo livro) e como testar.
- Código e nomes de projetos em inglês; documentação em português.

## Pré-requisitos gerais

- JDK 21+ e Maven 3.9+
- Docker + Docker Compose (para executar as topologias distribuídas)

## Licença

[MIT](LICENSE)
