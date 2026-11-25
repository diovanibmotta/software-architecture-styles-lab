# 03 — Arquitetura da implementação

## Mapeamento livro → código

| Conceito do livro | Onde está |
|-------------------|-----------|
| ESB / motor de orquestração | [`esb-orchestrator`](../esb-orchestrator): `OrderToCashWorkflow` |
| Serviços corporativos reutilizáveis | `customer-service`, `inventory-service`, `billing-service`, `order-service` |
| Camada de mensagens canônicas | [`soa-contracts`](../soa-contracts): `Canonical` (records) — **único código compartilhado** |
| Registro de serviços / roteamento | `Endpoints` (propriedades `soa.endpoints.*`); só o orquestrador conhece as URLs |
| Adaptadores de protocolo | `Gateways` (interfaces) + `RestGateways` (HTTP/JSON) |
| Serviço de infraestrutura (auditoria) | lista de `Step` devolvida em cada resposta |
| Compensação | `inventory.release` quando a cobrança é recusada ou um serviço cai |

## Componentes

```mermaid
flowchart LR
    CH([Canal]) -->|POST /orders → 201 final| ESB
    subgraph ESB[esb-orchestrator :8100]
        WF[OrderToCashWorkflow] --> GW[Gateways REST]
    end
    GW -->|POST /credit-checks| CU[customer-service :8101]
    GW -->|POST /reservations<br/>DELETE /reservations/id| IN[inventory-service :8102]
    GW -->|POST /charges| BI[billing-service :8103]
    GW -->|POST /orders · PUT /orders/id/status| OR[order-service :8104]
    K[(soa-contracts<br/>Canonical)] -.compilado em.- WF & CU & IN & BI & OR
```

Cada serviço tem **estado próprio em memória** e **nenhuma dependência** de outro serviço.

## Fluxos

### Caminho feliz

```mermaid
sequenceDiagram
    participant C as Canal
    participant E as Orquestrador
    participant O as order
    participant U as customer
    participant I as inventory
    participant B as billing
    C->>E: POST /orders
    E->>O: create (CREATED)
    E->>U: checkCredit
    U-->>E: approved
    E->>I: reserve
    I-->>E: reserved
    E->>B: charge
    B-->>E: approved
    E->>O: settle CONFIRMED
    E-->>C: 201 {order: CONFIRMED, steps[]}
```

### Pagamento recusado (compensação)

```mermaid
sequenceDiagram
    participant E as Orquestrador
    participant I as inventory
    participant B as billing
    participant O as order
    E->>I: reserve
    I-->>E: reserved
    E->>B: charge
    B-->>E: declined
    Note over E: só o orquestrador sabe que precisa desfazer
    E->>I: DELETE reservation (compensação)
    E->>O: settle CANCELLED "payment declined"
```

### Um serviço fora do ar

Falha técnica (conexão recusada, timeout, 5xx) vira `ServiceUnavailableException`. O orquestrador **compensa o que
fez** (libera o estoque reservado, cancela o pedido) e responde **`502`** com `{service, orderId}`. Se a falha ocorre
**depois da cobrança**, não há operação de estorno: o passo `orchestrator.reconcile` é registrado e o pedido fica
`CREATED` — situação real que exige reconciliação (ver doc 04).

## API

| Serviço | Método | Rota | Descrição |
|---------|--------|------|-----------|
| orchestrator | `POST` | `/orders` | `{"customerId","sku","quantity"(1-10),"unitPriceCents"}` → `201` `{order, steps[]}` ou `502` |
| orchestrator | `GET` | `/orders/{id}` | Consulta o pedido (delegada ao `order-service`) |
| customer | `POST` | `/credit-checks` | `{customerId, amountCents}` → `{approved, reason}` |
| customer | `PUT` | `/customers/{id}` | Define cliente `{active, creditLimitCents}` |
| inventory | `GET`/`PUT` | `/stock/{sku}` | Consulta / define estoque |
| inventory | `POST` · `DELETE` | `/reservations` · `/reservations/{orderId}` | Reserva / libera (idempotentes por pedido) |
| billing | `POST` · `GET` | `/charges` · `/charges/{orderId}` | Cobra (recusa acima de `billing.limit-cents`) / consulta |
| order | `POST` · `GET` · `PUT` | `/orders` · `/orders/{id}` · `/orders/{id}/status` | Sistema de registro do pedido |

## Topologia de execução

`docker compose up -d --build`: 5 contêineres. O compose só expõe as portas dos serviços para facilitar
experimentos; **em produção só o orquestrador seria exposto**. Os testes reproduzem a mesma topologia em uma JVM,
cada módulo em seu contexto Spring e sua porta HTTP.
