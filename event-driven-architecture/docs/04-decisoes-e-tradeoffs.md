# 04 — Decisões e trade-offs

## Decisões

| Decisão | Motivo | Custo |
|---------|--------|-------|
| **Topologia de broker** (coreografia) | O fluxo é curto e linear; mostra o desacoplamento máximo e a extensibilidade | O fluxo inteiro não está escrito em lugar nenhum (veja a tabela de tópicos em [03](03-arquitetura-da-implementacao.md)) |
| **ActiveMQ Artemis (JMS)** | Broker real, tópicos duráveis, embutível nos testes **sem Docker** | Menos "moderno" que Kafka; sem log replayável |
| **Um tópico por tipo de evento** | Assinaturas simples e explícitas | Muitos tópicos se o catálogo crescer |
| **Assinatura durável + compartilhada** | Retém eventos de consumidor offline e permite várias instâncias competindo | Nome de assinatura precisa ser único por serviço/tópico |
| **Eventos no passado, imutáveis (`record`)** | Fato ≠ comando; permite novos assinantes sem tocar o produtor | Mudar um evento exige versionamento |
| **`POST /orders` → 202** | Honestidade sobre a assincronia | O cliente precisa consultar ou ser notificado |
| **Compensação por evento** (`PaymentDeclined` → devolver estoque) | Sem transação distribuída | Estados intermediários visíveis (estoque reservado por instantes) |
| **Handlers idempotentes** | JMS entrega *at-least-once* | Cada serviço guarda o que já processou |
| **Sem `application.yml`; config por env vars** | Quatro apps no mesmo *classpath* de teste sem colisão; 12-factor | Menos descobrível para quem espera um arquivo |
| **`artemis.version=2.52.0`** | O 2.40 (padrão do Boot 3.5) não sobe o broker no JDK 24+ | Sobrescreve o BOM do Boot; revisar quando o Boot atualizar |

## Idempotência na prática

| Serviço | Como tolera a redelivery |
|---------|--------------------------|
| order | Só transiciona se ainda estiver `PENDING` |
| inventory | Guarda reserva/rejeição por `orderId`; reprocessar **republica o mesmo resultado** em vez de reservar de novo; `release` remove a reserva uma vez |
| payment | `computeIfAbsent` por `orderId`; reprocessar republica a mesma decisão |
| notification | Chave `orderId:tipo`: nunca envia a mesma mensagem duas vezes |

## Limitações conhecidas (atalhos conscientes)

| Limitação | Consequência | Como evoluir |
|-----------|--------------|--------------|
| **Estado em memória** em cada serviço | Reiniciar um serviço perde pedidos/estoque/idempotência | Um banco **por serviço** (o isolamento de dados é parte do estilo) |
| **Sem outbox**: muda o estado e publica em seguida, não atomicamente | Crash entre os dois = evento perdido (a redelivery cobre o caso do *consumidor*, não o do *produtor* de `OrderPlaced`) | *Transactional outbox* + relay |
| **Sem DLQ/retry configurados** | Mensagem "venenosa" é redelivered até o limite padrão do broker (aviso `AMQ222165` nos logs) | `AddressSettings` com `deadLetterAddress`, *backoff* e alertas |
| **Sem versionamento de eventos / schema registry** | Mudança incompatível quebra consumidores | Campos opcionais, versão no tipo, registry (Avro/JSON Schema) |
| **Sem tracing** | Seguir um pedido entre serviços é manual (`orderId` nos logs) | Propagar *correlation id* + OpenTelemetry |
| **Sem *event sourcing* / replay** | O broker não é fonte da verdade | Kafka + *event store* se auditoria/replay forem requisitos |
| **Um único broker** | É o ponto único de falha do estilo | Cluster/HA do Artemis (réplica ou *shared store*) |
| Compose **não executado** no ambiente de desenvolvimento | Possíveis ajustes de imagem/credenciais | Rodar `docker compose up` e corrigir o que aparecer |

## Quando migrar para *mediator*

Se o fluxo ganhar muitos passos, ramificações e regras de reparo (ex.: reenvio, aprovação manual, prazos), a coreografia
vira uma teia difícil de entender. Aí vale um **mediador/orquestrador** que conhece o fluxo e emite comandos — trocando
parte do desacoplamento e da elasticidade por **controle e visibilidade**.
