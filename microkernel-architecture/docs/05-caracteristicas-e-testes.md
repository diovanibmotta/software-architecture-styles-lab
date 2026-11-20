# 05 — Características de arquitetura e como testar

## Avaliação do estilo (tabela do livro, resumida)

| Característica | Nota | Por quê |
|----------------|:----:|---------|
| Implantabilidade | ★★★ | Plug-ins entram/saem sem redeploy do núcleo; depende da qualidade do carregador |
| Testabilidade | ★★★★ | Cada plug-in é testado isolado contra o contrato; o núcleo, com plug-ins de teste |
| Simplicidade | ★★★★ | Poucos tipos de componente, modelo mental pequeno |
| Custo geral | $ | Uma unidade de deploy, infraestrutura mínima |
| Desempenho | ★★★ | Chamadas locais; o orçamento é o do plug-in mais lento |
| Modularidade / extensibilidade | ★★★★ | É o ponto forte do estilo |
| Evolutividade | ★★★ | Fácil estender; difícil mudar o contrato |
| Tolerância a falhas | ★ | Embutido: mesma JVM. Melhora com plug-ins remotos |
| Escalabilidade / elasticidade | ★ | O núcleo escala como um todo, não por plug-in |

*Valores aproximados, conforme as tabelas do livro; confira a sua edição.*

## Estratégia de testes deste projeto

| Camada | O que valida | Onde |
|--------|--------------|------|
| **Plug-in isolado** | Regra de preço/prazo e elegibilidade de cada transportadora | `ExpressPluginTest`, `EconomyPluginTest`, `PickupPluginTest` |
| **Carga real de jar** | Descobrir, carregar, recarregar e descarregar jars compilados na hora | `PluginManagerTest` |
| **Isolamento de classes** | Mesma classe `t.P` em dois jars convive sem conflito | `PluginManagerTest#sameClassNameInTwoJarsIsIsolated` |
| **Resiliência do núcleo** | Jar corrompido e id duplicado viram `failed`; os saudáveis seguem | `PluginManagerTest#brokenJarIs...`, `#duplicatePluginIdIsRejected` |
| **Falha/lentidão de plug-in** | Exceção e timeout não afetam os demais; latência total limitada | `QuoteServiceTest` |
| **API** | Validação (`400`) e cotação ponta a ponta via MockMvc | `KernelApiTest` |
| **Hot deploy ao vivo** | Soltar/remover jar com o núcleo no ar | `scripts/demo.sh` |

Os testes compilam plug-ins **de verdade** (`javax.tools`) e os empacotam em jar: o caminho de *class loading* —
onde moram os bugs deste estilo — é exercitado sem mocks.

```bash
mvn verify          # todos os testes
scripts/demo.sh     # hot load / unload contra o docker compose
```
