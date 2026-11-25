# 03 — Arquitetura da implementação

## Mapeamento livro → código

| Conceito | Onde está |
|----------|-----------|
| Contexto delimitado: catálogo | `catalog-service` — tabela `items` |
| Contexto delimitado: pedido + saga | `order-service` — tabelas `orders` e `outbox` |
| Contexto delimitado: pagamento | `payment-service` — tabela `payments` |
| Contexto delimitado: entrega | `delivery-service` — tabela `deliveries` |
| API gateway | `gateway` — proxy reverso `/api/{rota}/**`, sem regra de negócio |
| Banco por serviço | `application`/`*.properties` próprios; no compose, **4 PostgreSQL**; nos testes, 4 H2 distintos |
| Sem código compartilhado | Não há módulo comum; `order-service` tem `CatalogClient.Item` e `PaymentClient.Payment` próprios |
| Resiliência | `Downstream`: *timeout* HTTP + *circuit breaker* **Resilience4j** |
| Saga com compensação | `OrderService` (+ `OutboxRelay`) |
| Mensagem assíncrona confiável | **Outbox transacional** em `order-service` |

```mermaid
flowchart LR
    C([Cliente]) -->|/api/...| G[gateway :8080]
    G --> CAT[catalog-service]
    G --> ORD[order-service]
    G --> PAY[payment-service]
    G --> DEL[delivery-service]
    ORD -- "REST + timeout + breaker" --> CAT
    ORD -- "REST + timeout + breaker" --> PAY
    ORD -- "outbox → REST (retry)" --> DEL
    ORD -- "outbox → estorno (retry)" --> PAY
    DEL -. "callback REST" .-> ORD
```

## API (via gateway: `http://localhost:8100/api/...`)

| Rota no gateway | Serviço | Descrição |
|-----------------|---------|-----------|
| `POST /api/catalog/items` · `GET /api/catalog/items[/{id}]` | catalog | `{"name","priceCents"}` |
| `POST /api/orders/orders` · `GET /api/orders/orders/{id}` | order | `{"customerId","itemId","quantity"(1-10),"address"}` → `201` + pedido |
| `POST /api/payments/payments` · `GET .../{id}` · `POST .../{id}/refund` | payment | autoriza (idempotente por pedido), consulta, estorna |
| `POST /api/deliveries/deliveries` · `GET .../{orderId}` · `POST .../{orderId}/complete` | delivery | despacha, consulta, conclui |

Estados do pedido: `PENDING → PAID → CONFIRMED → DELIVERED`, ou `REJECTED` (pagamento recusado/indisponível;
nada cobrado) ou `CANCELLED` (cobrado, **sem entregador**, estornado).

## A saga de “fazer pedido” (orquestrada pelo `order-service`)

```mermaid
sequenceDiagram
    actor C as Cliente
    participant G as gateway
    participant O as order-service
    participant K as catalog-service
    participant P as payment-service
    participant D as delivery-service
    C->>G: POST /orders
    G->>O: POST /orders
    O->>K: GET /items/{id} (timeout + breaker)
    K-->>O: preço
    O->>O: salva pedido PENDING
    O->>P: POST /payments (timeout + breaker)
    P-->>O: AUTHORIZED
    O->>O: transação: pedido PAID + mensagem no outbox
    O-->>C: 201 PAID
    Note over O,D: assíncrono, em outra thread
    O->>D: POST /deliveries (outbox, retenta até 2xx)
    D->>D: atribui entregador
    D-->>O: callback ASSIGNED
    O->>O: CONFIRMED
```

### Compensação: pago, mas sem entregador

```mermaid
sequenceDiagram
    participant O as order-service
    participant D as delivery-service
    participant P as payment-service
    O->>D: POST /deliveries (outbox)
    D-->>O: callback NO_COURIER
    O->>O: transação: pedido CANCELLED + mensagem REFUND no outbox
    O->>P: POST /payments/{id}/refund (outbox, retenta)
    P->>P: REFUNDED
```

Não há “rollback” global: o `order-service` **desfaz o efeito** do passo anterior com uma ação de negócio (estorno).

### Falhas e o que o sistema faz

| Falha | Comportamento |
|-------|---------------|
| `catalog` fora/lento | `503` imediato (breaker aberto) — **nenhum pedido é criado** |
| `payment` fora/lento | pedido `REJECTED (payment unavailable)` e `503`; breaker abre após falhas repetidas e fecha sozinho (half-open) |
| pagamento recusado | pedido `REJECTED`, nada despachado |
| `delivery` fora | pedido fica `PAID`; mensagem espera no outbox; ao voltar, é entregue → `CONFIRMED` |
| callback duplicado/atrasado | ignorado (transições só a partir do estado esperado) |
| `order-service` cai entre pagar e gravar | ver riscos em [04](04-decisoes-e-tradeoffs.md) |

## Dados: um banco por serviço

```mermaid
flowchart LR
    CAT[catalog-service] --- A[(items)]
    ORD[order-service] --- B[(orders, outbox)]
    PAY[payment-service] --- C[(payments)]
    DEL[delivery-service] --- E[(deliveries)]
```

`order` guarda só `itemId`/`paymentId` — **referências por id**, sem chave estrangeira entre bancos. Não existe `JOIN`
entre contextos: quem precisa de dado de outro serviço **pergunta pela API** (ex.: preço) ou **guarda uma cópia**.
