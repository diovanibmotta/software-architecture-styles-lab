# 04 — Decisões e trade-offs

## Decisões de design

| # | Decisão | Alternativa descartada | Motivo |
|---|---------|------------------------|--------|
| 1 | **3 serviços por domínio** (catálogo, pedidos, cobrança) | Um serviço por entidade/função | Granularidade grossa é o ponto do estilo; cada caso de uso cabe em **um** serviço |
| 2 | **Banco único** compartilhado | Um banco por serviço | Permite transação ACID estoque+pedido sem saga; menos infraestrutura |
| 3 | **Sem chamadas entre serviços** | `billing` chamar `order` via REST | Evita acoplamento temporal e falha em cascata; custo: billing conhece o schema de pedidos |
| 4 | **`UPDATE ... WHERE stock >= q`** atômico | `SELECT ... FOR UPDATE` ou `@Version` | Uma ida ao banco, sem retry, sem lock longo; correto sob concorrência |
| 5 | **Compare-and-set** `PENDING → PAID` | Checar status no código e depois gravar | Fecha a janela de corrida: pagamentos concorrentes geram uma só nota |
| 6 | **`shared-persistence` como biblioteca** | Cada serviço com suas entidades | Mostra o acoplamento de schema de forma explícita e evita divergência |
| 7 | **Dinheiro em centavos (`long`)** | `BigDecimal` | Simplicidade; sem ponto flutuante |
| 8 | **`nginx` como gateway** | Spring Cloud Gateway | Suficiente para roteamento por path; menos peças |
| 9 | **H2 nos testes, PostgreSQL no compose** | Testcontainers | Testes rápidos e sem Docker; o SQL é compatível com ambos |

## Trade-offs assumidos

| Ganho | Custo |
|-------|-------|
| Implantar/escalar/reiniciar um domínio sem tocar nos outros | 3 pipelines, 3 containers, observabilidade distribuída |
| ACID entre estoque e pedido | O banco é **ponto único de falha** e de contenção |
| Isolamento de falhas por domínio (cobrança cai, vitrine segue) | O **banco** continua sendo falha compartilhada |
| Poucos serviços = pouca complexidade operacional | Menos elasticidade que microsserviços (escala em "blocos") |

## Riscos e limitações conhecidas

1. **Acoplamento por schema.** Alterar `customer_order` exige coordenar `order` e `billing` (e recompilar
   `shared-persistence`). Mitigar com migrações versionadas (Flyway), mudanças aditivas (expand/contract) e testes de contrato.
2. **Múltiplos escritores na mesma tabela** (`product`, `customer_order`). Hoje as regras são disciplina de código; em
   produção, separar por permissões de banco (usuários por serviço) ou esquemas.
3. **Biblioteca compartilhada = deploy coordenado.** Mudou o entity, todos os serviços que o usam são reimplantados.
   Dividir `shared-persistence` por domínio reduz o raio de impacto.
4. **Conexões.** Cada instância de serviço abre um pool; muitas réplicas esgotam conexões do PostgreSQL (usar PgBouncer).
5. **Sem autenticação, sem versionamento de API, sem observabilidade** (tracing, métricas): omitidos de propósito.
6. **Pedido de um único item** (um produto por pedido) para manter o foco no estilo, não no domínio.

## Como evoluir

- **Banco por domínio** (esquemas separados) quando um domínio exigir autonomia — o primeiro passo rumo a microsserviços.
- **Eventos** (outbox) para o que não precisa de ACID imediato, como notificações após pagamento.
- **Extrair a biblioteca compartilhada** em módulos por domínio, ou substituí-la por acesso via API quando o acoplamento doer.
