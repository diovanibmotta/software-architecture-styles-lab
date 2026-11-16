# 03 — Arquitetura da implementação

## Visão de implantação

```mermaid
flowchart LR
    client([Cliente / flash-sale.sh]) -->|:8080| gw

    subgraph compose [docker compose]
        gw[gateway<br/>nginx least_conn]
        subgraph grid [Cluster Hazelcast - ticketing-grid]
            pu1[pu-1<br/>Spring Boot :8080<br/>membro :5701]
            pu2[pu-2<br/>Spring Boot :8080<br/>membro :5701]
            pu3[pu-3<br/>Spring Boot :8080<br/>membro :5701]
        end
        dw[data-writer<br/>cliente Hazelcast<br/>sem dados]
        pg[(postgres:16)]
    end

    gw --> pu1 & pu2 & pu3
    pu1 <--> pu2 <--> pu3 <--> pu1
    dw -- "lê outbox / ack (EntryProcessor)\nputIfAbsent IMap" --> grid
    dw -- JDBC --> pg
```

- As **3 unidades de processamento** são idênticas e simétricas; todas são membros do cluster e guardam o inventário completo.
- O **data writer** é *cliente* do cluster (não é membro): não armazena partições, só conversa com o grid. Isso o isola do
  ciclo de vida dos dados e permite reiniciá-lo sem rebalanceamento.
- O **banco nunca é acessado pelas unidades de processamento.** Só o data writer fala com o PostgreSQL.

## Mapeamento livro → código

| Conceito do livro | Classe / artefato |
|-------------------|-------------------|
| Processing unit | [`ticketing-processing-unit`](../ticketing-processing-unit) — `ReservationController` (web) + `ReservationService` (negócio) + membro Hazelcast |
| Data grid (cache replicado) | `IMap<String, EventInventory>` configurada em `GridConfigFactory` (`backupCount` síncrono) |
| Operação atômica no grid | `ReserveSeatsProcessor` (*entry processor*) |
| Data pump | *Outbox* `EventInventory.pendingReservations` (mesma entrada do estoque) + `AcknowledgeProcessor` |
| Data writer | `DataPumpConsumer` + `ReservationRepository` em [`ticketing-data-writer`](../ticketing-data-writer) |
| Data reader | `InventoryLoader` (`putIfAbsent` periódico do banco para o grid) |
| Messaging grid | `nginx/nginx.conf` |
| Deployment manager | `docker-compose.yml` (manual) |
| Contratos compartilhados | [`ticketing-contracts`](../ticketing-contracts) |

## Fluxo 1 — Reserva de ingressos (caminho crítico)

```mermaid
sequenceDiagram
    autonumber
    actor B as Comprador
    participant N as nginx
    participant C as ReservationController (pu-X)
    participant S as ReservationService
    participant M as IMap event-inventory
    participant O as Dono da chave (membro)

    B->>N: POST /events/mega-concert-2026/reservations
    N->>C: roteia (least_conn)
    C->>C: valida (customerId, 1 ≤ quantity ≤ 4)
    C->>S: reserve(event, customer, qty)
    S->>M: executeOnKey(event, ReserveSeatsProcessor)
    M->>O: executa o processor no dono da chave
    Note over O: numa única escrita da entrada:<br/>availableSeats -= qty<br/>pendingReservations += reserva (outbox)
    O-->>M: restantes ≥ 0, ou SOLD_OUT / UNKNOWN_EVENT
    M-->>S: resultado (backups síncronos já confirmaram)
    alt reservado
        S-->>C: CONFIRMED
        C-->>B: 201 {reservationId, remainingSeats}
    else esgotado
        C-->>B: 409 SOLD_OUT
    else evento inexistente
        C-->>B: 404 UNKNOWN_EVENT
    end
    Note over M,O: Backups síncronos garantem que a<br/>alteração já está nas outras unidades
```

Nenhuma etapa envolve I/O de banco. O custo é: validação + um salto de rede ao dono da chave (se ele não for a unidade
local) + replicação síncrona para os backups.

**Por que o outbox fica dentro do inventário?** Estoque e reserva pendente são gravados na **mesma entrada** do mapa, logo na
mesma operação atômica e replicada. Não existe janela em que o assento foi vendido mas a reserva ainda não está registrada para
persistência (o que ocorreria com duas operações separadas, ex.: `IMap` + `IQueue`, se a unidade caísse entre elas).

## Fluxo 2 — Persistência assíncrona (outbox → data writer)

```mermaid
sequenceDiagram
    autonumber
    participant G as IMap event-inventory (grid)
    participant W as DataPumpConsumer
    participant R as ReservationRepository
    participant DB as PostgreSQL

    loop enquanto rodando
        W->>G: get(eventId) → pendingReservations
        alt outbox vazio
            W->>W: sleep 50 ms
        else há reservas pendentes
            loop cada reserva
                W->>R: save(reservation)
                R->>DB: BEGIN
                R->>DB: INSERT reservations (PK = reservationId)
                alt chave duplicada (replay)
                    DB-->>R: DuplicateKeyException
                    R-->>W: false (ignorado)
                else novo
                    R->>DB: UPDATE events SET available_seats = available_seats - qty
                    R->>DB: COMMIT
                    R-->>W: true
                end
            end
            W->>G: executeOnKey(eventId, AcknowledgeProcessor(ids))
            Note over G: remove do outbox só os ids já gravados;<br/>reservas que chegaram durante a gravação permanecem
        end
    end
```

**Garantia:** *at-least-once*. A ordem é **ler → gravar no banco → confirmar no grid**. Se o writer cair entre gravar e
confirmar, as reservas são relidas e reenviadas — e o `INSERT` com chave primária `reservationId` torna o reprocessamento
**idempotente**. O *ack* remove por id, nunca "tudo", então não perde reservas que entraram enquanto o writer gravava.

## Fluxo 3 — Carga/recarga do grid (data reader)

```mermaid
sequenceDiagram
    autonumber
    participant S as @Scheduled (10 s)
    participant L as InventoryLoader
    participant DB as PostgreSQL
    participant M as IMap event-inventory

    loop a cada 10 s
        S->>L: loadMissingEvents()
        L->>DB: SELECT * FROM events
        loop cada evento
            L->>M: putIfAbsent(eventId, inventory)
            Note right of M: só insere se ausente → nunca<br/>sobrescreve estoque "vivo" do grid
        end
    end
```

Cobre dois cenários: **partida a frio** (o banco semeia o grid vazio) e **falha total do grid** (ao voltar, o grid é
repovoado a partir do último estado durável).

## Fluxo 4 — Falha de uma unidade

```mermaid
sequenceDiagram
    autonumber
    actor B as Comprador
    participant N as nginx
    participant P1 as pu-1
    participant P2 as pu-2 (cai)
    participant P3 as pu-3

    B->>N: POST reservations
    N->>P2: roteia
    P2--xN: erro / timeout
    N->>P3: proxy_next_upstream
    P3-->>B: 201
    Note over P1,P3: Hazelcast detecta a perda do membro,<br/>promove backups a donos das partições<br/>e reequilibra. Estoque e outbox permanecem íntegros (estão na mesma entrada replicada).
```

## Modelo de dados

```mermaid
erDiagram
    EVENTS ||--o{ RESERVATIONS : has
    EVENTS {
        varchar id PK
        varchar name
        int total_seats
        int available_seats
    }
    RESERVATIONS {
        varchar id PK "reservationId (idempotência)"
        varchar event_id FK
        varchar customer_id
        int quantity
        timestamp confirmed_at
    }
```

No grid, a mesma informação vive como `EventInventory(eventId, name, totalSeats, availableSeats)`. A **fonte da verdade durante a
venda é o grid**; o banco é uma cópia durável que converge após o backlog da data pump zerar.

## API

| Método | Rota | Sucesso | Erros |
|--------|------|---------|-------|
| `GET` | `/events/{eventId}/availability` | `200` `EventInventory` | `404` |
| `POST` | `/events/{eventId}/reservations` | `201` `{reservationId, quantity, remainingSeats}` | `400` validação · `404` evento · `409` esgotado |
| `GET` | `/grid` | `200` `{node, members[], dataPumpBacklog}` | — |
| `GET` | `/actuator/health` | `200` | — |

Todas as respostas dos endpoints de evento trazem `X-Served-By: pu-N`.

## Configuração relevante

| Variável | Onde | Efeito |
|----------|------|--------|
| `GRID_MEMBERS` | PU e writer | Endereços `host:porta` das unidades (discovery TCP/IP) |
| `GRID_BACKUPS` | PU | Nº de cópias síncronas. `3 unidades → 2` = cópia completa em todas |
| `NODE_NAME` | PU | Nome devolvido em `X-Served-By` |
| `SPRING_DATASOURCE_*` | writer | Conexão com o PostgreSQL |

## Como estender (adicionar `pu-4`)

1. Duplicar o serviço `pu-3` no `docker-compose.yml` como `pu-4`.
2. Incluir `pu-4:5701` em `GRID_MEMBERS` (em todas as unidades e no writer).
3. Incluir `server pu-4:8080;` no `upstream` do `nginx.conf`.
4. `docker compose up -d --build` — o novo membro entra no cluster e recebe cópia dos dados.
