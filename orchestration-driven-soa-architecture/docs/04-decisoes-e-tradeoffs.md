# 04 — Decisões e trade-offs

## Decisões

| Decisão | Motivo | Custo |
|---------|--------|-------|
| Orquestrador **dono do fluxo**; serviços passivos | Um lugar para ler, auditar e testar o processo; serviços reutilizáveis | Orquestrador vira o centro de tudo |
| **Contrato canônico** compartilhado (`soa-contracts`) | Sem tradução entre serviços; vocabulário único | Acoplamento de build/versão entre **todos** os módulos |
| Regras de negócio **nos serviços** (crédito em `customer`, limite em `billing`) | O orquestrador só decide *a quem chamar*, não *o que é válido* | Exige disciplina: lógica tende a vazar para o ESB |
| Resposta **síncrona** com desfecho final e *trace* | Simples para o canal e fácil de depurar | Latência = soma dos passos; canal preso enquanto tudo roda |
| **Compensação** explícita (`release`) em vez de transação distribuída | Sem 2PC; cada serviço tem seu estado | Consistência eventual; compensações podem falhar |
| Operações **idempotentes por `orderId`** (reserve, charge, settle, release) | Permitem retentar com segurança | Estado de deduplicação por serviço |
| Falha técnica → `502` + compensação | Cliente sabe que não foi uma recusa de negócio | Sem retry/circuit breaker automático |

## Idempotência na prática

`reserve` reaproveita a reserva do mesmo pedido; `charge` devolve a decisão já gravada; `settle` só muda um pedido
`CREATED` (a primeira decisão vence); `release` ignora pedidos sem reserva. Testes unitários de cada serviço cobrem.

## O que este estilo faz **mal** (e o código mostra)

- **Acoplamento**: acrescentar "antifraude" ao fluxo exige alterar `OrderToCashWorkflow`, os `Canonical` e, se o
  contrato mudar, **todos** os serviços e consumidores. Compare com o `notification-service` do estilo orientado a eventos.
- **Gargalo / ponto único de falha**: toda requisição passa por `esb-orchestrator`; se ele cai, o negócio para. Os
  testes mostram o 502 quando um serviço cai, mas nada protege contra a queda do próprio orquestrador.
- **Baixa evolutividade e implantação**: o orquestrador conhece todos os serviços; uma mudança de processo é um
  projeto coordenado entre equipes.
- **Elasticidade fraca**: escala-se o orquestrador **inteiro** para ganhar vazão em um único passo.
- **Reuso é caro**: serviços "genéricos" acumulam condicionais para atender todos os processos.

## Limitações conhecidas (atalhos conscientes)

- Estado **em memória** em cada serviço (sem banco); reiniciar apaga os dados.
- Sem **estorno** de cobrança: falha após a cobrança deixa o pedido `CREATED` e registra `reconcile` (precisaria de
  `billing.refund` + processo de reconciliação).
- Sem **retry, circuit breaker, timeout por serviço configurável** (há timeouts fixos de 2 s/5 s) nem *outbox*.
- Orquestração **em código Java**; ESBs reais usam BPEL/BPMN com motor persistente e processos de longa duração
  (aqui, o processo vive e morre dentro de uma requisição HTTP).
- Sem autenticação, **registro de serviços dinâmico**, versionamento de contrato ou transformação de protocolo.
- O `docker-compose.yml` **foi validado** (`docker compose config`), mas não executado ponta a ponta; o
  comportamento é coberto pelos testes com contextos separados.

## Quando migrar

| Sintoma | Direção |
|---------|---------|
| Equipes brigam por mudanças no orquestrador | Dividir por domínio: orquestração local por contexto (microsserviços) |
| Picos derrubam o orquestrador | Fluxo assíncrono por eventos (filas/broker) |
| Reuso nunca aconteceu | Fundir serviços ou aproximar da arquitetura baseada em serviços |
