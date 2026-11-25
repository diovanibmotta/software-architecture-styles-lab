# 04 — Decisões e trade-offs

## Decisões de design

| Decisão | Motivo | Custo |
|---------|--------|-------|
| **Banco por serviço** (Spring Data JDBC, H2 nos testes, PostgreSQL no compose) | Autonomia real; esquemas evoluem sozinhos | Sem JOIN/transação entre serviços; dados duplicados |
| **Sem módulo compartilhado** | Evita acoplamento de build/deploy | DTOs duplicados; mudança de contrato exige cuidado de compatibilidade |
| **Saga orquestrada no `order-service`** | Fluxo legível em um lugar só; o pedido é o dono do processo | `order-service` conhece o fluxo (acoplamento de orquestração) |
| **REST síncrono para catálogo e pagamento** | O cliente precisa da resposta; simples | Disponibilidade multiplicada; exige breaker/timeout |
| **Entrega assíncrona via outbox + callback** | Entrega é lenta e pode estar fora; não deve travar o checkout | Consistência eventual; estado intermediário `PAID` visível |
| **Outbox transacional** | “Pago” e “pedido de entrega” confirmam **juntos** (sem *dual write*) | Polling; entrega *at-least-once* → receptores idempotentes |
| **Idempotência nos receptores** (`payments.order_id` único, `deliveries.order_id` único, estorno e callbacks idempotentes) | Retentativas são normais | Mais código e testes |
| **Resilience4j programático** (`Downstream`) | Dependência pequena, comportamento explícito e testável | Sem retry/bulkhead/rate limiter nesta amostra |
| **Gateway mínimo próprio** | Mostra a responsabilidade sem trazer Spring Cloud | Sem auth, rate limit, breaker por rota, observabilidade |
| **Por que não um broker (Kafka/Artemis)?** | O estilo #6 já mostra mensageria; aqui o outbox + REST mostra a mesma garantia com menos peças | Em produção, evento em broker escala melhor que *polling* |

## Trade-offs inerentes ao estilo (não são bugs do exemplo)

- **Complexidade operacional.** Hoje: 5 processos + 4 bancos para um domínio minúsculo. Em produção somam-se
  descoberta de serviços, configuração, segredos, *tracing* distribuído, logs centralizados, pipelines por serviço.
- **Dados distribuídos.** Sem transação ACID entre contextos, sem relatórios com JOIN. Relatórios exigem replicação
  para um *read model*/data lake.
- **Consistência eventual.** Entre “pago” e “confirmado” o sistema está intermediário; a UI e o suporte precisam
  conviver com isso.
- **Granularidade difícil.** Fino demais → saga em tudo e latência; grosso demais → vira #5. Regra prática do
  livro: separe quando as partes têm **razões diferentes para mudar/escalar**, não por tamanho de classe.
- **Testes mais caros.** Unitário por serviço é fácil; o valor está em testes de contrato e e2e, mais lentos.
- **Latência e falha em cascata.** Cada salto de rede soma tempo e probabilidade de falha.

## Atalhos conscientes e riscos conhecidos

| Atalho | Risco | Evolução |
|--------|-------|----------|
| `order-service` salva `PENDING`, chama o pagamento e só depois grava `PAID`: uma queda **entre** a cobrança e a gravação deixa pedido `PENDING` com pagamento autorizado | Cobrança sem pedido confirmado | Reconciliador que varre `PENDING` antigos e consulta `GET /payments` por `orderId`; ou autorizar também via outbox |
| Relay do outbox sem *lock* (1 instância) | Duas réplicas enviariam duplicado (os receptores toleram, mas é desperdício) | `SELECT … FOR UPDATE SKIP LOCKED`, ou CDC (Debezium) + broker |
| Retentativas do outbox sem *backoff* nem *dead-letter* | Mensagem “venenosa” é reenviada para sempre | Backoff exponencial, limite de tentativas, DLQ e alerta |
| Callback `/orders/{id}/delivery` acessível pelo gateway e sem autenticação | Qualquer cliente poderia forjar uma entrega | Rede interna/mTLS ou token entre serviços; não rotear no gateway público |
| Gateway sem auth/rate limit/breaker | Exposição e falha em cascata no entrypoint | Spring Cloud Gateway/Envoy com JWT, limites e breaker por rota |
| Descoberta de serviços por URL fixa | Não acompanha escala dinâmica | DNS do orquestrador (Kubernetes) ou service mesh |
| Esquema criado por `schema.sql` | Sem versionamento | Flyway/Liquibase por serviço |
| Sem *tracing*/métricas | Depuração de fluxo distribuído é cega | OpenTelemetry + correlação por `orderId` |
| `docker-compose.yml` não foi executado no desenvolvimento deste exemplo (veja README) | Pode precisar de ajustes | Os mesmos serviços são exercitados pelo e2e sem Docker |

## Como o exemplo evoluiria

1. Trocar o outbox por **eventos em broker** (`OrderPaid`, `DeliveryRejected`) e fazer a saga por **coreografia**
   quando houver mais participantes (ver #6).
2. Extrair **notificação** como novo serviço assinante, sem tocar nos existentes.
3. Adicionar **BFF** por canal (app, web) à frente do gateway.
4. Contratos testados com **consumer-driven contracts** (Pact/Spring Cloud Contract).
