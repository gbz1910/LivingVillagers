# CONTINUE HERE — Living Villagers

> Arquivo de continuidade do projeto.  
> Sempre leia este arquivo antes de modificar o mod em um novo chat.

## 1. Projeto

- Repositório: `gbz1910/LivingVillagers`
- Branch de trabalho: `main`
- Minecraft: **1.20.1**
- Mod loader: **Forge 47.4.0**
- Java do build: **17**
- Gradle usado no GitHub Actions: **8.8**
- Mod ID: `livingvillagers`
- Versão atual deste snapshot: **0.2.1-alpha.26.1**
- Commit deste snapshot: `d00a9d598ca295a8b989b847e071d7d857ebd921`
- Build alpha.26.1: **GitHub Actions concluído com sucesso**

## 2. Regra de trabalho

Antes de alterar qualquer coisa:

1. Ler este arquivo.
2. Ler o workflow atual em `.github/workflows/build.yml`.
3. Buscar os arquivos/patches atuais na branch `main`.
4. Nunca assumir que uma build antiga ainda representa o estado atual.
5. Compilar sempre pelo **GitHub Actions** antes de entregar.
6. Só afirmar que compilou se o Actions realmente terminar com `success`.
7. Sempre entregar ao usuário:
   - o `.jar` direto;
   - um `.zip` de segurança contendo o mesmo JAR.

O ambiente local não deve ser considerado a fonte principal de compilação. O GitHub Actions é o caminho confiável usado neste projeto.

## 3. Como o projeto é reconstruído atualmente

A base principal armazenada no repositório é a **alpha.18**, dividida em:

`a18.part000.b64` até `a18.part012.b64`

O workflow atual:

1. reconstrói a alpha.18;
2. aplica as correções da alpha.19;
3. aplica o overhaul de configurações da alpha.21;
4. aplica o patch da alpha.24;
5. aplica a recuperação de viagem/coleta da alpha.25;
6. aplica o overhaul de exploração do Minerador da alpha.26;
7. aplica o hotfix de cavernas/lampião da alpha.26.1;
8. remove restos antigos do Baú da Vila/Chave da Vila;
9. verifica que Baú/Chave e o sistema de pilares não voltaram;
10. compila com Java 17 + Gradle 8.8.

Não reintroduzir patches antigos sem entender a ordem atual.

## 4. REGRA CRÍTICA — cabeça dos villagers

A cabeça dos villagers customizados já causou muitos bugs.

O método aprovado foi corrigido na **alpha.17** e está documentado também em:

`docs/VILLAGER_HEAD_RENDERING.md`

### Método correto

- manter a cabeça vanilla;
- `head.visible = true`;
- esconder somente:
  - `hat.visible = false`;
  - `hatRim.visible = false`;
- aplicar isso somente à profissão que precisa ficar sem chapéu.

### Nunca fazer novamente

- NÃO usar `super.hatVisible(false)` para esconder o chapéu;
- NÃO esconder a cabeça inteira;
- NÃO usar capacete 3D para cobrir bug de textura;
- NÃO pintar a área da cabeça de preto;
- NÃO tentar resolver esse problema somente com `.png.mcmeta`;
- NÃO substituir a cabeça vanilla por uma cabeça completa dentro da layer da profissão.

Ao criar novos villagers, reutilizar o método aprovado da alpha.17.

## 5. Profissões customizadas

Profissões já existentes no projeto:

- Minerador
- Lenhador
- Ferreiro
- Construtor
- Guarda
- Arqueiro
- Fazendeiro usa a profissão vanilla como base visual/comportamental onde aplicável.

Blocos de trabalho criados anteriormente incluem:

- Mining Table → Minerador
- Blacksmith Forge → Ferreiro
- Lumberjack Workbench → Lenhador
- Builder Table → Construtor
- Guard Post → Guarda
- Archer Table → Arqueiro

## 6. Minerador — estado atual importante

O Minerador já possui uma implementação funcional avançada.

Características importantes:

- procura minérios;
- usa picareta;
- pode pegar ferramentas do chão/estoque;
- mostra a ferramenta na mão;
- usa durabilidade real;
- pode trocar por ferramenta melhor;
- considera ferramentas adequadas para os minérios;
- quebra minério progressivamente;
- olha para o bloco enquanto trabalha;
- drops físicos aparecem;
- recolhe recursos;
- deposita em baús/barris;
- para de trabalhar à noite conforme configuração;
- possui trocas próprias;
- não deve voltar a quebrar pedra aleatoriamente quando não houver minério;
- patrulha quando não encontra minério.

Preservar o comportamento que já funciona antes de fazer mudanças grandes.

### Hotfix alpha.26.1 — cavernas e retorno para casa

A alpha.26.1 corrige dois problemas observados em jogo:

- o lampião do Minerador estava renderizado de cabeça para baixo; a layer do lampião foi rotacionada sem alterar o renderer aprovado da cabeça da alpha.17;
- a exploração antiga escolhia pontos escuros genéricos e podia deixar o Minerador andando sem objetivo perto de uma caverna.

O fluxo atual do Minerador é:

1. usa a memória HOME/cama como referência de casa quando disponível;
2. procura entradas de cavernas grandes, abertas e alcançáveis perto da casa;
3. evita entradas já marcadas como esgotadas;
4. caminha até a entrada;
5. explora checkpoints internos alcançáveis e guarda os pontos já visitados;
6. durante a exploração, procura minérios expostos com linha de visão real;
7. se encontrar minério, interrompe a exploração para minerar e coletar;
8. se encher o inventário, deposita e pode retomar a mesma caverna;
9. quando duas buscas consecutivas não encontram novo checkpoint útil, considera a caverna esgotada;
10. deposita os recursos restantes e retorna para HOME/cama;
11. se não houver caverna válida nem minério realmente visível, não inicia patrulha aleatória: volta para casa e aguarda.

O Minerador não deve detectar cavernas/minérios através de paredes como um X-Ray. A entrada precisa ser uma região aberta/alcançável e os minérios continuam exigindo exposição + linha de visão.

## 7. Lenhador — estado atual alpha.24

### IMPORTANTE: pilares foram REMOVIDOS

O sistema de pilares de terra foi abandonado definitivamente.

Motivo: causava quedas, loop de giro, pathfinding instável, estruturas de terra desnecessárias e estados presos.

### Nunca reintroduzir

O workflow atual verifica que não existem novamente referências como:

- `raiseLumberjackWithDirt`
- `finishCurrentLumberjackPillar`
- `cleanupLumberjackScaffold`
- `hasScaffoldBlocks`
- `cutting_from_pillar`
- `starting_dirt_pillar`
- `climbing_dirt_pillar`

### Novo comportamento: árvore conectada

A alpha.24 substituiu o pilar pelo sistema de **corte inteligente da árvore inteira**.

Fluxo esperado:

1. Lenhador encontra uma árvore.
2. Vai até a base/área alcançável da árvore.
3. Identifica troncos conectados pertencentes àquela árvore.
4. Quebra os troncos em sequência, inclusive os altos.
5. Não precisa fisicamente subir até cada tronco.
6. Continua usando machado e tempo de trabalho.
7. Durabilidade do machado continua relevante.
8. Ao terminar a árvore, replanta quando possível.
9. Depois entra em modo de coleta de drops.
10. Só depois continua o ciclo de trabalho.

### Drops

Na alpha.24, os drops do corte da árvore foram ajustados para surgir perto da **base da árvore**, evitando drops presos no alto da copa.

O Lenhador deve:

- procurar os drops após terminar a árvore;
- caminhar até eles;
- recolher fisicamente os itens;
- priorizar madeira, mudas e outros itens relacionados à árvore;
- só depois procurar nova árvore ou retornar ao estoque.

Se houver bug de coleta, corrigir o estado de coleta em vez de trazer o sistema de pilares de volta.

### Folhas

O Lenhador pode quebrar folhas quando elas bloqueiam o acesso/caminho necessário para trabalhar.

### Árvores de mods

Existe suporte genérico iniciado na alpha.19.

O código tenta reconhecer árvores/mods através de tags/registro e relacionar nomes como:

- `*_log` → `*_sapling`
- `*_wood` → `*_sapling`
- `*_stem` → `*_fungus`
- `*_hyphae` → `*_fungus`

Não assumir compatibilidade perfeita com todos os mods. Quando algum mod específico falhar, verificar as tags e os IDs reais dele.

## 8. Replantio

Objetivo do Lenhador:

- destruir a árvore inteira;
- identificar a muda correspondente;
- replantar no local correto após terminar;
- dark oak pode exigir lógica 2x2;
- árvores modded podem exigir tratamento específico caso não sigam nomes/tags comuns.

## 9. Trabalho noturno

Os trabalhadores devem respeitar a configuração da vila para trabalho à noite.

Por padrão, a intenção do projeto é que profissões como Minerador e Lenhador parem de trabalhar à noite.

Não deixar villager preso em estado de trabalho quando o período de descanso começa.

## 10. Estoque

O sistema usa estoques físicos próximos, principalmente:

- baús;
- barris.

Os trabalhadores devem buscar ferramentas e depositar recursos nesses estoques.

## 11. Baú da Vila e Chave da Vila — REMOVIDOS

A ideia foi testada e depois descartada pelo usuário.

Foram removidos:

- bloco Baú da Vila;
- item Chave da Vila;
- receita;
- traduções;
- registros;
- integração de estoque;
- recursos relacionados.

### Regra

**NÃO REINTRODUZIR o Baú da Vila nem a Chave da Vila, a menos que o usuário peça explicitamente no futuro.**

O workflow atual possui verificação para impedir que restos desses sistemas sejam incluídos sem querer.

## 12. Configurações da Vila

A interface foi reformulada na alpha.21.

Ao pressionar a tecla configurada para abrir as configurações:

### Tela principal

Deve mostrar opções simples e gerais da vila.

A proposta é não sobrecarregar o jogador com dezenas de parâmetros logo na primeira tela.

Existe um botão:

**Configurações avançadas**

### Tela avançada

Permite configurar profissões individualmente.

O Lenhador possui/foi planejado com controles como:

- limite de estoque;
- raio de patrulha;
- raio de busca de árvores;
- quantidade de troncos/ações antes de voltar;
- quebrar folhas;
- replantar árvores.

A configuração antiga de **altura de pilar** não deve ter efeito funcional após a alpha.24. Se ainda aparecer na interface, pode ser removida em uma futura limpeza da UI.

Existem páginas/estrutura para outras profissões, incluindo:

- Minerador
- Fazendeiro
- Lenhador
- Ferreiro
- Construtor
- Guarda
- Arqueiro

Nem toda profissão possui todos os sistemas finais implementados ainda.

## 13. Trocas

### Minerador

As trocas já foram adicionadas anteriormente e seguem uma progressão por nível de villager.

Incluem recursos como:

- carvão;
- cobre;
- ferro;
- ouro;
- diamante;
- tochas;
- trilhos;
- picaretas.

### Lenhador

Também possui progressão própria de trocas relacionada a:

- troncos;
- gravetos;
- mudas;
- machados;
- itens ligados à madeira.

Não remover as trocas existentes sem pedido do usuário.

### Ferreiro

O Ferreiro ainda será trabalhado mais profundamente no futuro.

A antiga ideia de colocar uma Chave da Vila como última troca do Ferreiro foi **cancelada junto com o Baú da Vila**.

## 14. Estado geral do projeto

O projeto está sendo desenvolvido de forma incremental.

Áreas mais maduras:

- profissões registradas;
- visuais customizados;
- cabeça sem chapéu corrigida;
- Minerador;
- Lenhador;
- inventário de trabalho;
- armazenamento em baús/barris;
- ferramentas;
- estados de trabalho;
- configurações da vila;
- trades de Minerador/Lenhador.

Áreas que ainda podem receber desenvolvimento significativo:

- Ferreiro;
- Construtor;
- Guarda;
- Arqueiro;
- economia/demanda;
- comportamento coletivo mais profundo;
- polimento e compatibilidade com mods.

## 15. Máquina de estados

A arquitetura conceitual original do trabalhador segue:

`IDLE → CHECK_NEEDS → CHECK_INVENTORY → GET_REQUIRED_ITEMS → TRAVEL_TO_TASK → PERFORM_TASK → RETURN → STORE_ITEMS → SELECT_NEW_TASK`

Ao implementar novas profissões, preferir integrar-se a essa estrutura em vez de criar loops independentes sem controle de estado.

## 16. Cuidados ao alterar IA

Bugs já observados no desenvolvimento:

- villager girando infinitamente;
- pathfinding competindo com comportamento customizado;
- villager caindo de estruturas temporárias;
- estado de tarefa não sendo limpo;
- drops não sendo recolhidos;
- visual de ferramenta/cabeça interferindo com renderer vanilla.

Ao corrigir IA:

- limpar corretamente `WALK_TARGET` quando necessário;
- garantir transições explícitas de estado;
- ter timeout/recovery;
- evitar teleportes/NoGravity salvo se absolutamente necessário;
- preferir soluções que funcionem com o villager no chão;
- nunca deixar um worker preso em um estado que depende de um bloco que já não existe.

## 17. Compatibilidade com mods

O usuário testa o mod dentro de um modpack.

Não presumir que todos os blocos seguem exatamente comportamento vanilla.

Para recursos externos:

- preferir tags Forge/Minecraft;
- usar registry IDs;
- evitar listas fixas gigantes quando tags resolverem;
- quando necessário, adicionar fallback específico para mods populares.

## 18. Entrega de builds

Sempre seguir este padrão:

1. Fazer as alterações no repositório.
2. Criar/ajustar o workflow.
3. Push na `main`.
4. Esperar o GitHub Actions.
5. Se falhar, corrigir antes de responder.
6. Se passar:
   - baixar o artefato;
   - extrair o JAR;
   - renomear de forma clara;
   - criar ZIP de segurança;
   - entregar os dois links.

Formato de nomes preferido:

`LivingVillagers-1.20.1-Forge-47.4.0-alpha.XX.jar`

`LivingVillagers-alpha.XX-security.zip`

## 19. Política de versões

Continuar incrementando as alphas:

- alpha.17 → correção definitiva da cabeça;
- alpha.18 → grande implementação do Lenhador;
- alpha.19 → compatibilidade/replantio/drops/folhas;
- alpha.20 → Baú da Vila/Chave da Vila (IDEIA DESCARTADA);
- alpha.21 → novo sistema de configurações + tentativa de pilar;
- alpha.22 → remoção total do Baú da Vila/Chave;
- alpha.23 → tentativa final de estabilizar pilar;
- alpha.24 → **pilares removidos e substituídos por corte inteligente de árvore conectada**.
- alpha.25 → correção do travamento em `TRAVEL_TO_TASK`, recuperação automática de alvo e coleta de drops estabilizada.
- alpha.26 → Minerador com exploração de cavernas sem X-Ray, linha de visão para minérios, lampião visível com iluminação móvel, fuga acelerada de Zombies/Pillagers e limites diários configuráveis por profissão.
- alpha.26.1 → corrige a orientação do lampião e substitui a patrulha aleatória do Minerador por exploração de cavernas com detecção de entrada, checkpoints visitados, mineração apenas de minérios realmente visíveis e retorno para casa ao esgotar a caverna.

Ao criar a próxima build, continuar em **alpha.26.1**, salvo decisão explícita diferente.

## 20. Antes de responder em outro chat

Se o usuário disser algo como:

> "Continue meu mod Living Villagers"

Faça primeiro:

1. acessar `gbz1910/LivingVillagers`;
2. ler este `CONTINUE_HERE.md`;
3. conferir a branch `main`;
4. conferir o workflow atual;
5. verificar o último GitHub Actions;
6. só então começar a modificação.

## 21. Preferências do usuário para este projeto

- Prefere que as mudanças sejam feitas diretamente, não apenas receber código para copiar.
- Quer builds prontas para colocar na pasta `mods`.
- Sempre quer JAR + ZIP de segurança.
- Prefere comportamento visual e lógico natural.
- Se uma mecânica está instável depois de várias tentativas, é melhor simplificar a solução do que insistir numa implementação quebradiça.
- Não gerar imagens quando ele estiver pedindo arquivos/build do mod.
- Ao receber screenshot de bug, usar o screenshot para entender o problema e corrigir o projeto.

---

### Próximo ponto de continuação

Estado atual: **alpha.26.1 compilada com sucesso**.

A mudança principal mais recente é:

**Minerador sem X-Ray → detecta entradas de cavernas grandes próximas → explora checkpoints internos sem repetir caminho → minera apenas o que realmente enxerga → considera a caverna esgotada quando não há mais caminhos úteis → deposita recursos e volta para sua casa/cama. Lampião corrigido para a orientação correta.**

A próxima versão deve partir daqui.
