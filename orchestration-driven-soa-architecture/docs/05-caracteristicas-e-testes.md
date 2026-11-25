# 05 — Características de arquitetura e como testar

## Avaliação do estilo (tabela do livro, resumida)

| Característica | Nota | Por quê |
|----------------|:----:|---------|
| Particionamento | Técnico | Serviços por camada/capacidade; o processo atravessa todos |
| Nº de quanta | 1 | Orquestrador + contrato canônico amarram tudo em um único quantum |
| Implantabilidade | ★ | Mudança de processo/contrato exige coordenar vários módulos |
| Elasticidade | ★ | Escala o ESB inteiro, não o passo que satura |
| Evolutividade | ★ | Tudo acoplado ao orquestrador e ao canônico |
| Tolerância a falhas | ★ | Orquestrador é ponto único de falha; compensações parciais |
| Desempenho | ★★ | Chamadas síncronas em sequência; ESB acrescenta latência |
| Escalabilidade | ★★ | Limitada pelo orquestrador |
| Simplicidade | ★ | Muitas peças (ESB, contrato, registro) para um fluxo |
| Testabilidade | ★★★ | O fluxo é explícito e central: fácil de testar em um lugar |
| Reuso | ★★★★★ | É o ponto forte e a razão de existir do estilo |
| Custo geral | $$$$ | ESB, governança e coordenação entre equipes |

*Valores aproximados, conforme as tabelas do livro; confira a sua edição.*

## Estratégia de testes deste projeto

| Nível | Arquivo | O que valida |
|-------|---------|--------------|
| Unitário (serviços) | `CustomerServiceTest`, `InventoryServiceTest`, `BillingServiceTest`, `OrderServiceTest` | Regras de negócio, **idempotência**, e **sem overselling** sob concorrência (estoque) |
| Unitário (orquestração) | [`OrderToCashWorkflowTest`](../esb-orchestrator/src/test/java/com/github/diovanibmotta/soa/orchestrator/OrderToCashWorkflowTest.java) | Ordem dos passos, desvios, **compensação**, falha técnica, falha pós-cobrança (sem liberar estoque) |
| E2E | [`OrderToCashE2ETest`](../e2e-tests/src/test/java/com/github/diovanibmotta/soa/OrderToCashE2ETest.java) | Os 5 módulos, cada um em seu contexto Spring/porta HTTP |

| Cenário E2E | Teste |
|-------------|-------|
| `201 CONFIRMED`, estoque −1, *trace* na ordem esperada, consulta do pedido | `happyPathConfirmsOrderReservesStockAndExposesTheOrchestrationTrace` |
| Cliente desconhecido → cancelado sem tocar no estoque | `unknownCustomerIsRejectedBeforeTouchingStock` |
| Cliente inativo → cancelado | `inactiveCustomerIsRejected` |
| Sem estoque → cancelado sem cobrar | `outOfStockCancelsOrderWithoutCharging` |
| **Compensação**: cobrança recusada → estoque devolvido | `declinedPaymentCancelsOrderAndCompensatesByReleasingStock` |
| **Concorrência**: 12 pedidos × 5 unidades → 5 confirmados, 7 cancelados, estoque 0 | `neverOversellsWithConcurrentOrders` |
| **Falha**: billing fora do ar → `502`, estoque liberado, pedido `CANCELLED` | `billingDownIsSurfacedAs502AfterCompensatingStockAndCancellingTheOrder` |

```bash
mvn verify        # não precisa de Docker
```

## Como testar as características na prática

| Característica | Experimento |
|----------------|-------------|
| Visibilidade do fluxo | `POST :8100/orders` e leia `steps[]`: o processo inteiro está em uma resposta |
| Ponto único de falha | `docker compose stop esb-orchestrator`: nenhum pedido entra, mesmo com os 4 serviços saudáveis |
| Tolerância a falhas | `docker compose stop billing-service` e faça um pedido: `502`, estoque devolvido (`GET :8102/stock/console`) |
| Compensação | Pedido com `unitPriceCents` > 1.000.000: `CANCELLED`, estoque intacto |
| Acoplamento / evolutividade | Tente adicionar uma etapa de antifraude: veja quantos módulos você precisa tocar (`Canonical`, `Gateways`, `RestGateways`, workflow, testes) |
| Elasticidade | `--scale billing-service=3` não ajuda se o orquestrador for o limite; meça o orquestrador sob carga |
