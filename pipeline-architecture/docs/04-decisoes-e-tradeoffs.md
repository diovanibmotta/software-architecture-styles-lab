# 04 — Decisões e trade-offs

| Decisão | Motivo | Custo |
|---------|--------|-------|
| Java puro, engine própria (~150 linhas) | O estilo é simples; esconder em framework (Camel, Spring Integration) esconderia o conceito | Sem retry, métricas, DSL de ramificação |
| Um thread por filter + pipes limitados | Paralelismo entre etapas e back-pressure de graça | Cada filter é sequencial: um filter lento limita a vazão total |
| *Virtual threads* | Barato ter uma thread por filter, e bloquear em fila é idiomático | Requer JDK 21+ |
| Builder tipado (`Builder<T>`) | O compilador garante que a saída de um filter casa com a entrada do próximo | Pipeline estritamente linear |
| Dead letters em vez de abortar | Uma linha ruim não deve derrubar o arquivo de 1 milhão de linhas | Quem chama precisa olhar o relatório; nada é reprocessado automaticamente |
| Producer que falha aborta (`PipelineException`) | Fonte quebrada = resultado incompleto e silencioso | Os itens já processados não são desfeitos |
| `Tester` retorna `Optional<String>` (motivo) | Rejeição auditável, não só `boolean` | Um pouco mais verboso |

## Limitações conhecidas

- **Um processo, pipes em memória**: se a JVM cai, perde-se o que estava nas filas. Para durabilidade, trocar os pipes por Kafka/SQS (e aí o estilo vira *event-driven*).
- **Só `RuntimeException` é tratada por item**: um `Error` mata o filter e o pipeline trava (marcado com `shortcut:` no código).
- **Sem paralelismo dentro de um filter**: para escalar um filter lento, seria preciso N instâncias consumindo do mesmo pipe.
- **Pipeline linear**: sem *fan-out* (um item para vários ramos) nem *join*.
- **Estado em filters** (ex.: detectar pedidos duplicados) é possível, mas quebra a regra "filter sem estado" e vira ponto de contenção.

## Como evoluir

1. Filter paralelo (`transform(name, n, ...)`) com N workers por pipe.
2. Retry com limite antes de ir para dead letters; persistir dead letters em arquivo.
3. Pipes duráveis (arquivo/fila externa) → cada filter vira um processo, ganhando escala por etapa.
