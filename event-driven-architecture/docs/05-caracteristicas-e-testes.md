# 05 — Características de arquitetura e como testar

## Avaliação do estilo (tabela do livro, resumida)

| Característica | Nota | Por quê |
|----------------|:----:|---------|
| Particionamento | Técnico | Processadores por responsabilidade; o fluxo atravessa todos |
| Nº de quanta | 1 a várias | Depende dos bancos e do broker compartilhados |
| Implantabilidade | ★★★★ | Cada processador implanta sozinho; contratos de evento são a fronteira |
| Elasticidade | ★★★★★ | Mais instâncias na mesma assinatura = mais vazão; o broker absorve picos |
| Evolutividade | ★★★★★ | Novo assinante sem tocar ninguém (`notification-service`) |
| Tolerância a falhas | ★★★★★ | Broker retém eventos; um serviço cair não derruba o resto |
| Desempenho | ★★★★ | Sem espera síncrona; latência fim a fim maior que uma chamada direta |
| Escalabilidade | ★★★★★ | Particiona o trabalho por assinatura |
| Simplicidade | ★ | Fluxo implícito, eventual, difícil de depurar |
| Testabilidade | ★★ | Testar o fluxo exige broker e *esperas* assíncronas |
| Custo geral | $$$ | Broker, observabilidade e disciplina de contratos |

*Valores aproximados, conforme as tabelas do livro; confira a sua edição.*

## Estratégia de testes deste projeto

Todos em [`OrderFlowE2ETest`](../e2e-tests/src/test/java/com/github/diovanibmotta/eventdriven/OrderFlowE2ETest.java): um
**broker Artemis real** (TCP, embutido) e os **4 serviços, cada um em seu contexto Spring**, falando só pelo broker e
por suas portas HTTP.

| O que valida | Teste |
|--------------|-------|
| Fluxo completo: `202 PENDING` → `CONFIRMED`, estoque −1, cliente notificado | `happyPathConfirmsOrderReservesStockAndNotifiesCustomer` |
| Sem estoque → `CANCELLED` ("stock rejected"), estoque intacto, cliente notificado | `outOfStockCancelsOrderAndNotifiesCustomer` |
| **Compensação**: pagamento recusado → `CANCELLED` e estoque devolvido | `declinedPaymentCancelsOrderAndCompensatesByReleasingStock` |
| **Corretude sob concorrência**: 12 pedidos × 5 unidades → 5 confirmados, 7 cancelados, estoque 0 | `neverOversellsWithConcurrentOrders` |
| **Tolerância a falhas**: `payment-service` cai, o pedido espera `PENDING`, ao voltar é confirmado | `brokerHoldsEventsWhileAProcessorIsDownAndDeliversWhenItReturns` |

```bash
mvn verify        # ~15 s; não precisa de Docker
```

## Como testar as características na prática

| Característica | Experimento |
|----------------|-------------|
| Tolerância a falhas | `docker compose stop payment-service`, faça pedidos, `docker compose start payment-service`: todos viram `CONFIRMED` |
| Elasticidade | `docker compose up -d --scale inventory-service=3` (remova o `ports` do serviço): as instâncias dividem as mensagens da mesma assinatura |
| Evolutividade | Escreva um 5º serviço que assine `payments.approved` com outra assinatura: ele recebe tudo sem alterar nenhum dos 4 |
| Visibilidade do fluxo | Console do Artemis (`:8161`) → filas das assinaturas: mensagens acumuladas indicam quem está atrasado |
| Idempotência | Reenvie manualmente um evento pelo console: pedido/estoque não mudam duas vezes |
