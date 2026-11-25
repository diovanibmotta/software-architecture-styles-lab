# 03 — Arquitetura da implementação

## Mapeamento livro → código

| Conceito do livro | Implementação | Onde |
|-------------------|---------------|------|
| Camada de apresentação | Controllers REST, DTOs (`BookView`, `BorrowRequest`), tradução de exceções em status HTTP | `presentation/` |
| Camada de negócio | `LoanService` (empréstimo, devolução, multa) e `CatalogService` (disponibilidade) | `business/` |
| Camada de persistência | Repositórios com SQL explícito via `JdbcClient` | `persistence/` |
| Camada de banco de dados | PostgreSQL (compose) ou H2 em modo PostgreSQL (local/testes) | `schema.sql`, `data.sql` |
| Camadas fechadas | Regras ArchUnit que quebram o build em caso de violação | `LayeredArchitectureTest` |
| Objetos compartilhados entre camadas | Records `Book`, `Member`, `Loan` sem dependência de nenhuma camada | `domain/` |
| Unidade de implantação única | Um JAR Spring Boot | `Dockerfile` |

## Visão de componentes

```mermaid
flowchart TB
    subgraph presentation [presentation]
        BC[BookController]
        LC[LoanController]
        EH[ApiErrorHandler]
    end
    subgraph business [business]
        CS[CatalogService]
        LS[LoanService]
        EX["LoanRejectedException<br/>NotFoundException"]
    end
    subgraph persistence [persistence]
        BR[BookRepository]
        MR[MemberRepository]
        LR[LoanRepository]
    end
    DB[(books · members · loans)]

    BC --> CS
    LC --> LS
    EH -. trata .-> EX
    CS --> BR & LR
    LS --> BR & MR & LR
    BR & MR & LR --> DB
```

Repare no que **não** existe: nenhuma seta da apresentação para a persistência, nenhuma seta de baixo para cima. As
exceções de negócio são da camada de negócio; quem decide que `LoanRejectedException` vira `409` é a apresentação
(`ApiErrorHandler`). A camada de negócio nunca importa nada de `org.springframework.web` ou `org.springframework.http`
— também verificado por teste.

## Fluxo: emprestar um livro

```mermaid
sequenceDiagram
    autonumber
    actor C as Balcão
    participant P as LoanController<br/>(apresentação)
    participant B as LoanService<br/>(negócio)
    participant R as Repositórios<br/>(persistência)
    participant D as PostgreSQL

    C->>P: POST /loans {isbn, memberId}
    P->>P: valida formato (@NotBlank)
    P->>B: borrow(isbn, memberId)
    Note over B,D: @Transactional — tudo abaixo é uma transação
    B->>R: findByIsbnForUpdate(isbn)
    R->>D: SELECT ... FROM books WHERE isbn=? FOR UPDATE
    Note right of D: trava a linha do livro
    B->>R: findByIdForUpdate(memberId)
    R->>D: SELECT ... FROM members WHERE id=? FOR UPDATE
    B->>R: findActiveByMember / countActiveByIsbn
    R->>D: SELECT ...
    B->>B: atraso? limite de 3? exemplar disponível?
    alt alguma regra falha
        B-->>P: LoanRejectedException(reason)
        P-->>C: 409 {"error": reason}
    else tudo ok
        B->>R: insert(isbn, memberId, hoje, hoje+14)
        R->>D: INSERT INTO loans ...
        B-->>P: Loan
        P-->>C: 201 Loan
    end
    Note over B,D: COMMIT libera as travas
```

### Por que as travas?

Sem elas, dois balcões que tentam emprestar o **último exemplar** ao mesmo tempo leem "1 disponível" em paralelo e ambos
gravam o empréstimo. O `SELECT ... FOR UPDATE` na linha do livro faz a segunda transação **esperar** a primeira
terminar; quando ela lê de novo, o exemplar já foi emprestado. A trava na linha do membro faz o mesmo para o limite de
3 livros (o mesmo membro em dois balcões). A ordem é sempre **livro → membro**, então não há ciclo de espera (deadlock).

Esse controle está **dividido entre as camadas** do jeito que o estilo pede: a camada de negócio decide *que* precisa de
exclusividade (é uma regra: "não emprestar o mesmo exemplar duas vezes"); a camada de persistência decide *como*
(`FOR UPDATE`).

## Fluxo: devolver um livro

```mermaid
sequenceDiagram
    autonumber
    actor C as Balcão
    participant P as LoanController
    participant B as LoanService
    participant R as LoanRepository
    participant D as PostgreSQL

    C->>P: POST /loans/42/return
    P->>B: giveBack(42)
    B->>R: findById(42)
    B->>B: multa = max(0, dias de atraso) × R$ 2,00
    B->>R: markReturned(42, hoje, multa)
    R->>D: UPDATE loans SET ... WHERE id=42 AND returned_on IS NULL
    alt 0 linhas (já devolvido, inclusive por outra requisição concorrente)
        B-->>P: LoanRejectedException(ALREADY_RETURNED)
        P-->>C: 409
    else 1 linha
        B-->>P: Loan (returnedOn, fineCents)
        P-->>C: 200
    end
```

A devolução não precisa de trava explícita: o `UPDATE ... WHERE returned_on IS NULL` é uma **escrita condicional** —
se duas devoluções concorrem, só uma altera a linha.

## Modelo de dados

```mermaid
erDiagram
    books ||--o{ loans : "é emprestado em"
    members ||--o{ loans : "faz"
    books {
        varchar isbn PK
        varchar title
        varchar author
        int total_copies
    }
    members {
        varchar id PK
        varchar name
    }
    loans {
        bigint id PK
        varchar isbn FK
        varchar member_id FK
        date borrowed_on
        date due_on
        date returned_on "null = ativo"
        int fine_cents
    }
```

Exemplares disponíveis **não são armazenados**: são calculados (`total_copies − empréstimos ativos`). Assim não há
contador para ficar inconsistente — a fonte da verdade é a tabela `loans`.

## Topologia de implantação

```mermaid
flowchart LR
    C([cliente HTTP]) -->|":8081"| APP["library<br/>(Spring Boot, 1 JAR)<br/>apresentação + negócio + persistência"]
    APP -->|JDBC| PG[(postgres:16)]
```

| Ambiente | Banco | Como |
|----------|-------|------|
| Local | H2 em memória (modo PostgreSQL) | `mvn spring-boot:run` |
| Testes | H2 em memória (modo PostgreSQL) | `mvn verify` |
| Compose | PostgreSQL 16 | `docker compose up -d --build` (`SPRING_DATASOURCE_URL`) |

Trocar de H2 para PostgreSQL é só configuração — nenhuma linha de código muda. É o isolamento da camada de
persistência funcionando na prática.

## API

Detalhes de rotas e códigos de resposta no [README](../README.md#api).
