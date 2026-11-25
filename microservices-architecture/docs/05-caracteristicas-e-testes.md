# 05 — Características de arquitetura e testes

## Avaliação (escala do livro: ★ fraco … ★★★★★ forte)

| Característica | Nota | Por quê |
|----------------|------|---------|
| Implantabilidade | ★★★★★ | Cada serviço tem build e deploy próprios; mudança local não reimplanta o resto |
| Modularidade | ★★★★★ | Contextos delimitados com dados próprios |
| Evolutividade | ★★★★★ | Troca de tecnologia/esquema por serviço; novos serviços sem tocar nos antigos |
| Testabilidade | ★★★★☆ | Unidades isoladas são simples; fluxo distribuído exige e2e/contrato |
| Escalabilidade | ★★★★★ | Escala só o serviço sob carga (ex.: catálogo) |
| Elasticidade | ★★★★★ | Instâncias efêmeras e *stateless* (estado no banco do serviço) |
| Tolerância a falhas | ★★★★☆ | Falha isolada; exige breaker/timeout/outbox (implementados) |
| Desempenho | ★★☆☆☆ | Saltos de rede e serialização; mitigável com cache e chamadas assíncronas |
| Simplicidade | ★☆☆☆☆ | Muitas peças móveis |
| Custo | ★☆☆☆☆ | Infra, observabilidade e pessoas |
| Consistência | ★★☆☆☆ | Eventual; saga em vez de ACID |

## Como cada característica é validada aqui

| Característica | Teste | O que prova |
|----------------|-------|-------------|
| Banco por serviço | `FoodDeliveryE2ETest.everyServiceOwnsItsOwnDatabase` | Cada contexto vê só suas tabelas; 4 URLs JDBC distintas |
| Saga — caminho feliz | `happyPathPaysConfirmsAsynchronouslyAndDelivers` (e2e) · `OrderServiceTest.happyPath…` | `PAID` síncrono → `CONFIRMED` assíncrono → `DELIVERED` |
| Saga — compensação | `noCourierCancelsTheOrderAndTheSagaRefundsThePayment` (e2e) · `OrderServiceTest.noCourier…` | Sem entregador → `CANCELLED` + pagamento `REFUNDED` |
| Pagamento recusado | `declinedPaymentRejectsTheOrder…` | `REJECTED`, nada despachado |
| Tolerância a falha assíncrona | `deliveryServiceOutageDelaysConfirmationButLosesNothing` | Entrega fora: pedido `PAID`; ao voltar, `CONFIRMED` |
| Circuit breaker / timeout | `DownstreamTest` · `paymentOutageFailsFastWith503ThenTheCircuitRecovers` | Timeout, abre após falhas, não chama o serviço aberto, recupera por half-open; 4xx não conta |
| Idempotência | `PaymentControllerTest`, `DeliveryControllerTest`, `OrderServiceTest.callbacksAreIdempotent` | Retentativas não cobram/atribuem/estornam duas vezes |
| Gateway | `GatewayControllerTest` | Encaminha método/corpo/query/status; 404 rota; 502 upstream morto; 504 lento |
| Contrato por serviço | `*ControllerTest` (MockMvc + banco H2 próprio) | Validação (400), 404, regras de cada contexto |

## Executando

```bash
mvn verify                                   # unitários por serviço + e2e (5 contextos Spring, 4 bancos H2), sem Docker
mvn verify -pl order-service -am             # só um serviço
docker compose up -d --build                 # topologia completa com 4 PostgreSQL
```

O e2e sobe gateway + 4 serviços na mesma JVM, cada um em **seu contexto Spring, porta e banco**, e fala **só pelo
gateway** — a mesma topologia do compose. As falhas são reais: o teste **fecha** o contexto do serviço e o reabre.

## O que os testes **não** cobrem

- Rede real entre contêineres, DNS e *startup order* (compose não foi executado no desenvolvimento).
- Carga, latência sob falha em cascata e múltiplas réplicas do `order-service` (relay do outbox sem lock).
- Segurança (autenticação entre serviços e no gateway), migração de esquema e compatibilidade de contratos entre versões.
