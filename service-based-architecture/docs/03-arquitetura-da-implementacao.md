# 03 — Arquitetura da implementação

## Mapeamento livro → código

| Conceito do livro | Implementação |
|-------------------|---------------|
| Serviço de domínio | `catalog-service`, `order-service`, `billing-service` (cada um um Spring Boot, um `.jar`, um container) |
| Banco de dados compartilhado | PostgreSQL único (`shop`); H2 em memória nos testes |
| Biblioteca compartilhada de persistência | `shared-persistence`: `schema.sql`, entidades JPA, repositórios, exceções HTTP |
| Camada de API | `nginx/nginx.conf` (roteamento por path) |
| Implantação independente | `Dockerfile` parametrizado por `MODULE`; um container por serviço |

## Topologia

```mermaid
flowchart LR
    U([Cliente]) -->|:8084| G[nginx]
    G -->|/products| C[catalog-service]
    G -->|/orders| O[order-service]
    G -->|/payments · /invoices| B[billing-service]
    C --> PG[(PostgreSQL)]
    O --> PG
    B --> PG
```

Cada serviço **não conhece** os outros: não há chamadas HTTP entre eles. O que os liga é o schema.

## Modelo de dados compartilhado

```mermaid
erDiagram
    PRODUCT ||--o{ CUSTOMER_ORDER : "é pedido em"
    CUSTOMER_ORDER ||--o| INVOICE : "gera"
    PRODUCT { bigint id PK
              string name
              bigint price_cents
              int stock }
    CUSTOMER_ORDER { bigint id PK
              bigint product_id FK
              int quantity
              bigint total_cents
              string status }
    INVOICE { bigint id PK
              bigint order_id FK "unique"
              bigint amount_cents
              timestamp paid_at }
```

| Tabela | Dono (escreve) | Lê |
|--------|----------------|----|
| `product` | catalog (cadastro) · order (decrementa estoque) | todos |
| `customer_order` | order (cria) · billing (marca `PAID`) | todos |
| `invoice` | billing | billing |

> Note que **duas tabelas têm mais de um escritor**. É o preço do banco compartilhado, discutido em [04](04-decisoes-e-tradeoffs.md).

## Sequência: fazer um pedido (um serviço, uma transação)

```mermaid
sequenceDiagram
    participant C as Cliente
    participant O as order-service
    participant DB as PostgreSQL
    C->>O: POST /orders {productId, quantity}
    O->>DB: BEGIN
    O->>DB: SELECT product
    O->>DB: UPDATE product SET stock = stock - q WHERE id = ? AND stock >= q
    alt 0 linhas afetadas
        O->>DB: ROLLBACK
        O-->>C: 409 sem estoque
    else 1 linha
        O->>DB: INSERT customer_order (PENDING)
        O->>DB: COMMIT
        O-->>C: 201 pedido
    end
```

O `UPDATE ... WHERE stock >= q` é **atômico**: o banco serializa compradores concorrentes do último item. Estoque e pedido
mudam juntos ou não mudam — **sem saga**, graças ao banco compartilhado.

## Sequência: pagar (outro serviço, mesma garantia)

```mermaid
sequenceDiagram
    participant C as Cliente
    participant B as billing-service
    participant DB as PostgreSQL
    C->>B: POST /payments {orderId}
    B->>DB: BEGIN
    B->>DB: SELECT customer_order
    B->>DB: UPDATE customer_order SET status='PAID' WHERE id=? AND status='PENDING'
    alt 0 linhas
        B-->>C: 409 já pago
    else 1 linha
        B->>DB: INSERT invoice
        B->>DB: COMMIT
        B-->>C: 201 nota
    end
```

`billing-service` **lê o pedido direto do banco** em vez de chamar `order-service`: nenhuma dependência de runtime entre
serviços, então a queda de um não derruba o outro.

## Estrutura interna de cada serviço

Cada serviço é pequeno e plano (controller + service). Em um domínio maior, **por dentro** ele seria em camadas
(ver [layered-architecture](../../layered-architecture)) — o estilo não prescreve a arquitetura interna.

## API

Ver tabela no [README](../README.md#api-via-gateway-8084). Erros: `404` não encontrado, `409` conflito de negócio
(sem estoque, já pago), `400` validação.
