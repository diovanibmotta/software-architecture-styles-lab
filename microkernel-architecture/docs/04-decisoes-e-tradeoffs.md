# 04 — Decisões, trade-offs e limitações

## Decisões

| Decisão | Por quê | Custo |
|---------|---------|-------|
| `ServiceLoader` + `URLClassLoader` por jar (em vez de OSGi/JPMS/PF4J) | Zero dependência, mostra o mecanismo à mostra; suficiente para o estilo | Sem gestão de dependências entre plug-ins nem versionamento do contrato |
| Contrato num módulo **sem dependências** | Plug-in não herda Spring/núcleo; jar minúsculo | O contrato precisa ser pensado com cuidado: mudar é quebrar todos |
| Lista vazia = "não se aplica" | Plug-in decide a própria elegibilidade; núcleo não conhece regras de negócio de cada um | — |
| Fan-out paralelo em virtual threads com timeout por plug-in | Um plug-in lento não degrada os demais nem soma latência | `cancel(true)` só interrompe código que respeite interrupção |
| Descoberta por *polling* (`scan()`) | Funciona igual em Windows/Linux/Docker volume; simples | Latência de até `scan-interval`; `WatchService` seria mais imediato |
| Carregar de cópia temporária | Evita lock de arquivo no Windows | I/O extra por carga; limpeza de temporários |
| Spring só no núcleo | Plug-ins ficam livres de container | Plug-ins não usam injeção de dependência |

## Limitações conhecidas (e como evoluir)

1. **Mesma JVM = isolamento de classes, não de recursos.** Um plug-in pode consumir toda a CPU/memória ou chamar
   `System.exit`. *Evolução:* plug-ins **remotos** — o plug-in vira um serviço HTTP/mensageria e o núcleo guarda só
   um adaptador `ShippingPlugin` que chama a rede. O contrato e o `QuoteService` não mudam.
2. **Sem sandbox de segurança.** Plug-in carregado executa com os privilégios do processo. Só carregue jars confiáveis
   (a pasta de plug-ins é uma superfície de ataque — proteja o acesso a ela).
3. **Sem versionamento do contrato.** Mudança incompatível em `ShippingPlugin` quebra plug-ins antigos em tempo de
   execução (vão aparecer em `failed`, mas só ali). *Evolução:* `apiVersion()` no contrato e rejeição no carregamento.
4. **Plug-in que ignora interrupção** continua rodando em segundo plano após o timeout (a resposta já saiu, mas o
   recurso não é liberado).
5. **Vazamento de class loader.** Se um plug-in deixar threads ou referências estáticas vivas após `stop()`, o *class
   loader* não é coletado. Faz parte do contrato do plug-in encerrar o que abriu.
6. **Sem desabilitar/priorizar plug-ins** sem mexer no jar; **sem configuração por plug-in**.
7. **Detecção por `lastModified`**: uma troca de jar sem alterar a data não recarrega.

## Riscos de arquitetura (do livro)

- **O núcleo engordar**: cada vez que um plug-in "precisa de um pouquinho mais do núcleo", o contrato cresce e o
  acoplamento volta. Vigie o tamanho de `ShippingPlugin`.
- **Plug-ins dependendo uns dos outros**: sinal de que o recorte está errado.
