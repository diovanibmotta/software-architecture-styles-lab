# 02 — Quando usar

## Bons casos

- **ETL / ingestão de dados**: ler → limpar → enriquecer → carregar (este projeto).
- **Processamento de logs e telemetria**: capturar → filtrar → calcular métricas → gravar.
- **Compiladores e processadores de texto/mídia**: lexer → parser → otimizador → gerador.
- **Fluxos de aprovação automatizados** com passos claros e sequenciais (validar → enriquecer → classificar → notificar).

Sinais de que serve: o problema é **naturalmente sequencial**, cada passo tem entrada e saída bem definidas, os
passos mudam ou são reaproveitados com frequência, e o dado flui em uma só direção.

## Maus casos

| Situação | Por quê |
|----------|---------|
| Fluxo com muitos desvios, loops ou *fan-out/fan-in* complexo | O estilo é linear; ramificações exigem orquestração que ele não oferece |
| Requisição/resposta interativa de baixa latência com estado de sessão | Cada item atravessa N filas; sem contexto compartilhado |
| Domínio rico, com regras que cruzam entidades | Partição técnica espalha uma regra de negócio por vários filters |
| Escala diferente por etapa em produção | Num monólito todas escalam juntas (ver [04](04-decisoes-e-tradeoffs.md)) |

## Comparação rápida

| Estilo | Quando prefiro ele ao pipeline |
|--------|--------------------------------|
| Camadas | CRUD/regra de negócio sem fluxo de dados em etapas |
| Orientada a eventos | Fluxos assíncronos com ramificações e múltiplos consumidores independentes |
| Microkernel | Núcleo estável com extensões opcionais (plugins), não uma sequência |
