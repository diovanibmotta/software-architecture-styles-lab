# 05 — Características de arquitetura e como testar

## Avaliação do estilo (tabela do livro, resumida)

| Característica | Nota | Por quê |
|----------------|:----:|---------|
| Particionamento | Domínio | Serviços por domínio, não por camada técnica |
| Nº de quanta | 1 a várias | Um banco compartilhado costuma manter **um único quantum** |
| Implantabilidade | ★★★★ | Cada serviço implanta sozinho; menos serviços que microsserviços |
| Elasticidade | ★★ | Escala por serviço, mas em blocos grossos e limitada pelo banco |
| Evolutividade | ★★★ | Mudança de domínio fica num serviço; mudança de schema atravessa todos |
| Tolerância a falhas | ★★★★ | Serviço cai isolado; o banco compartilhado segue como ponto único |
| Desempenho | ★★★ | Rede só no gateway; sem chamadas entre serviços |
| Escalabilidade | ★★★ | Escala por serviço; banco vira gargalo antes |
| Simplicidade | ★★★ | Poucos serviços; mais simples que microsserviços |
| Testabilidade | ★★★★ | Cada domínio é testável isoladamente |
| Custo geral | $$ | Mais barato que microsserviços, mais caro que camadas |

*Valores aproximados, conforme as tabelas do livro; confira a sua edição.*

## Estratégia de testes deste projeto

| O que valida | Onde |
|--------------|------|
| **Corretude sob concorrência**: 40 compradores × 5 unidades → exatamente 5 vendas e estoque 0 | `OrderServiceTest#neverOversellsUnderConcurrentBuyers` |
| **Atomicidade**: sem estoque → `409`, nenhum pedido criado, estoque intacto | `OrderServiceTest#insufficientStockIs409AndLeavesNoOrder` |
| Cálculo de total e decremento de estoque | `OrderServiceTest#placingOrderDecrementsStockAndComputesTotal` |
| **Pagamento idempotente sob concorrência**: 10 pagamentos simultâneos → 1 nota | `BillingServiceTest#concurrentPaymentsIssueExactlyOneInvoice` |
| Fluxo pagar → nota; pagar de novo → `409`; pedido inexistente → `404` | `BillingServiceTest` |
| CRUD e validação do catálogo | `ProductControllerTest` |

```bash
mvn verify                      # tudo, com H2 em memória
mvn -pl order-service -am test  # só um serviço (e a biblioteca compartilhada)
```

## Como testar as características na prática

| Característica | Experimento |
|----------------|-------------|
| Isolamento de falhas | `docker compose stop billing-service` → `/products` e `/orders` continuam respondendo; `/payments` falha |
| Implantabilidade | `docker compose up -d --build order-service` → só ele reinicia |
| Elasticidade | `docker compose up -d --scale catalog-service=3` (e balancear no nginx) |
| Ponto único de falha | `docker compose stop postgres` → **todos** os serviços falham: o custo do banco compartilhado |

## Lacunas conhecidas

Os testes de integração rodam por serviço sobre H2. Não há teste end-to-end contra o PostgreSQL real nem teste de contrato
de schema entre serviços — recomendados antes de levar o estilo a produção (ver [04](04-decisoes-e-tradeoffs.md)).
