# 04 — Decisões de design, trade-offs e limitações

Registro no formato de ADRs resumidos.

## D1 — `IMap` com backups em vez de `ReplicatedMap` {#d1}

**Contexto.** O livro descreve o cache **replicado**. O Hazelcast tem `ReplicatedMap` (cópia completa em todos os membros), mas
ele usa replicação **assíncrona/eventual** e **não suporta operações atômicas** (`EntryProcessor`, locks).

**Decisão.** Usar `IMap` (particionado) com `backupCount = nº de membros − 1`, síncrono (`asyncBackupCount = 0`).

**Consequências.**
- ✅ Cada unidade mantém uma cópia completa (leitura local, alta disponibilidade — o espírito do cache replicado).
- ✅ Cada chave tem **um único dono**; o `EntryProcessor` roda nele e serializa as escritas → **sem overselling**, sem lock distribuído.
- ✅ Perda de até `backups` membros sem perda de dados.
- ⚠️ Escrita mais cara: espera ack dos backups (latência ∝ nº de réplicas).
- ⚠️ Hot key: um único evento = uma única chave = um único dono processando tudo em série. Para *dezenas de milhares* de reservas/s no mesmo evento, particione o inventário (ver D7).

## D2 — Atomicidade via *entry processor*, não via banco nem lock

**Decisão.** `ReserveSeatsProcessor` faz *check-and-decrement* dentro do dono da chave.

**Alternativas descartadas.**
- `SELECT … FOR UPDATE` no banco: reintroduz o gargalo que o estilo quer evitar.
- `IMap.lock(key)` + get/put: 3 round-trips e lock distribuído sujeito a *lease*/falhas.
- `CAS` otimista (`replace(k, old, new)`) em laço: sob contenção alta gera tempestade de retentativas.

## D3 — Data pump como `IQueue` replicada

**Decisão.** Reservas confirmadas vão para uma fila distribuída com os mesmos backups do inventário.

**Consequências.**
- ✅ Sem broker externo (Kafka/RabbitMQ) para o exemplo; a fila sobrevive à queda de unidades.
- ⚠️ **Não é durável em disco.** Se **todos** os membros caírem com backlog, as reservas pendentes se perdem — enquanto o banco ainda não recebeu. O estoque no banco ficará *maior* que o real (ingressos "voltam" a ser vendáveis após recarga).
- 🔧 Evolução: `QueueStore` do Hazelcast (persistência), ou trocar por Kafka/RabbitMQ com *publisher confirms*, ou **Transactional Outbox** no próprio grid.

## D4 — Entrega *at-least-once* + escrita idempotente

**Decisão.** `peek → gravar → poll`, e `INSERT` com PK = `reservationId`.

**Por quê.** *Exactly-once* distribuído é inviável de forma simples; *at-least-once* + idempotência dá o mesmo efeito prático.
**Limite.** Um único writer consome em ordem; vários writers exigiriam coordenação (a idempotência ainda evita duplicidade, mas haveria trabalho repetido).

## D5 — Data writer é *cliente*, não membro do grid

**Decisão.** `HazelcastClient` em vez de membro.

**Por quê.** Membros guardam partições e participam de rebalanceamento; reiniciar o writer não deve causar migração de dados.
O cliente também tenta reconectar indefinidamente (`clusterConnectTimeoutMillis = ∞`), permitindo ordem de subida livre.

## D6 — Discovery estático (TCP/IP) e `nginx` como messaging grid

**Decisão.** Lista fixa `GRID_MEMBERS` + upstream fixo no nginx.

**Por quê.** Simplicidade e determinismo no Docker Compose.
**Limite.** Não é elástico de verdade: adicionar `pu-N` exige editar configs. Em produção usar discovery do orquestrador
(plugin Kubernetes do Hazelcast / AWS), *autoscaling* (HPA / ECS) e *service discovery* dinâmico no gateway. Aí o
**deployment manager** passa a ser real.

## D7 — Hot key e particionamento do inventário (não implementado)

Um evento = uma chave. Para eventos gigantes, divida em *shards* de estoque (`event:setor-A`, `event:setor-B`, …) ou em
"lotes de assentos" (`event:0…N`), e distribua pedidos entre eles. É a evolução natural quando o dono da chave virar gargalo.

## D8 — Sem reserva temporária (hold) / pagamento

Fora de escopo: o modelo é "reserva confirmada = ingresso vendido". Um sistema real teria **reserva com TTL**
(`IMap.put(..., ttl)`) e etapa de pagamento (candidato a *processing grid*/saga).

## Limitações conhecidas (resumo)

| Limitação | Impacto | Mitigação |
|-----------|---------|-----------|
| Data pump sem persistência em disco | Perda de reservas pendentes se todo o grid cair | `QueueStore` / Kafka (D3) |
| Partição de rede (*split-brain*) | Dois sub-clusters podem vender o mesmo assento | Hazelcast *split-brain protection* com quórum / CP Subsystem (consistência forte, menor throughput) |
| Hot key | Teto de throughput por evento | Shards de inventário (D7) |
| Discovery estático | Elasticidade manual | Orquestrador + discovery dinâmico (D6) |
| Sem autenticação, rate limit, anti-bot | Aberto a abuso | Gateway/WAF, filas virtuais (sala de espera) |
| Sem idempotência na API de reserva | Retentativa do cliente gera 2 reservas | Header `Idempotency-Key` guardado em `IMap` com TTL |
| Sem *processing grid* | Fluxos multi-unidade não orquestrados | Saga/orquestrador quando houver pagamento |
| JVM + `--add-opens` | Hazelcast exige flags no JDK 17+ | Já aplicadas no `Dockerfile` e no Surefire |

## O que o estilo **não** resolveu aqui (e é preciso saber)

- **Consistência durável imediata**: entre confirmar ao cliente e gravar no banco existe uma janela (normalmente milissegundos a segundos).
- **Relatórios**: consultar `reservations` no banco mostra o estado *após* o backlog drenar; use `GET /grid` (`dataPumpBacklog`) para saber se o banco já convergiu.
