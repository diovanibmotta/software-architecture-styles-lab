# 05 — Características e testes

## Avaliação (estilo do livro)

| Característica | Nota | Justificativa |
|----------------|:----:|---------------|
| Custo geral / simplicidade | ★★★★★ | Poucos conceitos, monolítico, sem infraestrutura |
| Modularidade | ★★★★☆ | Filters pequenos e reordenáveis, mas partição técnica |
| Facilidade de testar | ★★★★★ | Cada filter é uma função pura testável isolada |
| Facilidade de implantação | ★★★☆☆ | Um artefato só: simples, mas todo ou nada |
| Evolutividade | ★★★☆☆ | Fácil inserir/trocar etapa; mudança de domínio atravessa vários filters |
| Desempenho | ★★★☆☆ | Bom para lotes/streams; latência por item soma as filas |
| Escalabilidade | ★★☆☆☆ | Escala o processo inteiro, não uma etapa |
| Elasticidade / tolerância a falhas | ★☆☆☆☆ | Pipes em memória: falha do processo perde o que estava em voo |

## Como cada ponto é validado aqui

| O que | Teste |
|-------|-------|
| Ordem, rejeição, erro de transformer, back-pressure (pipes de capacidade 2, 1000 itens) | `PipelineTest.flowsThroughFiltersInOrderAndCollectsDeadLetters` |
| Falha do producer não é silenciosa | `PipelineTest.producerFailureSurfacesAsPipelineException` |
| Resultado do ETL e motivos de cada rejeição na amostra | `SalesPipelineTest.sampleFileProducesSummaryAndRejectsBadRows` |
| Volume maior que a capacidade dos pipes não perde itens | `SalesPipelineTest.largeInputKeepsEveryValidOrder` |

Testar um filter sozinho não exige o pipeline: `new ConvertToBrl(rates).transform(order)` é uma chamada de método.

```bash
mvn verify
```
