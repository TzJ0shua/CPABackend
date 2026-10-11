# CPA Backend - Códigos de validação

Sistema backend em **Quarkus** que gera códigos opacos de validação para QR Codes.
O código entregue ao cliente é um segredo aleatório de 128 bits, gerado com `SecureRandom`.
Novos códigos armazenam em `NEW_HASH` (UUID) os primeiros 128 bits do SHA-256 do segredo. OLD_TOKEN preserva os tokens legados, aceita NULL e string vazia, e não participa da validação.

Após uma validação bem-sucedida, `PUT /validacao/validar-hash` retorna também
`codigoDigestFinal`: os 10 últimos caracteres de `NEW_HASH` do registro validado.
`GET /validacao/historico` inclui o mesmo campo em cada uma das 10 últimas validações,
ordenadas pela data de validação, da mais recente para a mais antiga. O digest é
hexadecimal, então esse identificador pode conter números e letras de `a` a `f`,
incluindo zeros à esquerda. O digest completo não é retornado.
`POST /formulario` também retorna `codigoDigestFinal` junto de `qrCode` e
`codigoValidacao`, permitindo ao frontend exibir o mesmo identificador no comprovante.

## 🚀 Início Rápido

### Opção 1: Com Docker Compose (Recomendado)

```bash
docker compose up --build
```

O backend estará disponível em `http://localhost:8080`
O PostgreSQL estará em `localhost:5433`

### Imagem publicada com versão numérica

A imagem desta entrega é `contatocpa/cpa-backend-test:1.0.0`, para Linux AMD64. No Compose da VPS, substitua o bloco `build` do serviço `backend` por `image: contatocpa/cpa-backend-test:1.0.0`, preservando `environment`, dependências, rede e portas. Mantenha o PostgreSQL em volume persistente e faça backup do banco antes da atualização; o Flyway aplica as migrações na inicialização.

```bash
docker compose pull backend
docker compose up -d --no-build backend
```

As variáveis do backend são lidas em tempo de execução. Configure banco, CORS, Keycloak e os segredos na VPS conforme a referência abaixo. O `.env` precisa ser encaminhado pelo bloco `environment` do Compose; não é incorporado à imagem. Preserve `VALIDACAO_ALUNO_HASH_SEED` e `VERIFICACAO_EMAIL_DIGEST_SECRET` entre atualizações.

O frontend correspondente é `contatocpa/cpa-frontend-test:1.0.0` e aceita API e Keycloak pelo ambiente do contêiner; veja o Compose de exemplo no README do frontend. Use outra tag numérica para novas releases, como `1.0.1`; esta publicação não altera a tag `latest` existente.

## Identificação de alunos e comprovantes

A migração `V15__add_student_identity_and_validation_links.sql` adiciona `cpf_hash`, `matricula_hash` e `formulario_versao_id` em `VALIDACAO`, além de `AVALIACAO.validacao_id`. Respostas pertencem à avaliação; esse vínculo permite identificar todas as disciplinas e respostas de um comprovante. Registros anteriores permanecem sem esses vínculos, pois não há identificação suficiente para preenchê-los retroativamente.

`POST /formulario` exige `respondent.cpf` válido (11 dígitos, com ou sem máscara) e `respondent.matricula` com exatamente 10 dígitos para alunos. CPF e matrícula são normalizados e armazenados somente como HMAC-SHA-256, com separação por tipo e campanha. As restrições únicas por versão e hash impedem repetição pelo CPF **ou** pela matrícula, inclusive em requisições simultâneas. Outra versão do mesmo formulário, outro formulário e outra campanha são permitidos. Todas as disciplinas desejadas devem ser enviadas juntas; erros de validação não gravam uma participação parcial.

Configure `VALIDACAO_ALUNO_HASH_SEED` no `.env` antes de aceitar alunos e recrie o backend. Gere um segredo aleatório de pelo menos 32 bytes, conserve-o entre reinicializações e proteja seu backup. Alterar a seed produz hashes diferentes e compromete a detecção das participações anteriores. Configuração ausente ou curta retorna `503` com `code: IDENTIFICACAO_INDISPONIVEL`; participação repetida retorna `409` com `code: FORMULARIO_JA_RESPONDIDO`. Docentes e funcionários mantêm a autorização por email ou chave mestra.

Leitura simples, leitura detalhada e histórico em `/validacao` incluem `resumo`: `validacaoId`, campanha (código/nome), formulário (código/nome/versão/público/escopo) e avaliações (ID, curso, disciplina/professor e `respostaIds`). O resumo permanece consultável após desativação dos cadastros e não contém CPF, matrícula, hashes pessoais, notas, opções ou comentários. Comprovantes antigos ou avulsos podem não ter contexto de formulário.

## Configuração por `.env`

### Resposta única por email

A migração `V16__limit_email_participation_per_version.sql` adiciona `VALIDACAO.email_digest` e um índice único por versão e digest de email. Docentes e funcionários que entram por PIN podem responder uma vez por versão de formulário da campanha. Outra versão, outro formulário ou outra campanha são permitidos. Pedir um novo PIN não libera uma versão já respondida; requisições simultâneas também retornam `409` com `code: FORMULARIO_JA_RESPONDIDO` para a participação repetida, sem gravar respostas parciais.

A identidade vem exclusivamente da autorização confirmada no servidor. O email informado em `respondent` não substitui essa identidade. O endereço é normalizado sem espaços nas extremidades e em minúsculas, e apenas seu HMAC-SHA-256 é persistido. Mantenha `VERIFICACAO_EMAIL_DIGEST_SECRET` estável: trocá-lo altera os digests e compromete o controle das participações anteriores. A autorização pode continuar sendo usada para outros formulários durante sua validade. Todas as disciplinas de uma versão devem ser enviadas juntas.

Não há limite de solicitações, reenvios por janela ou tentativas de confirmação do PIN imposto pelo backend. Reenviar gera um novo código e invalida o PIN anterior; um PIN incorreto retorna `400` sem bloquear novas tentativas de um código ainda válido. A expiração do PIN e da autorização, a validação do email institucional e a resposta única por email e versão continuam em vigor. Quotas impostas pelo provedor Gmail não são configuradas por esta aplicação.

**A chave mestra é uma exceção ao limite por email**, inclusive quando o serviço de email está indisponível. A migração identifica autorizações antigas por chave que não passaram pela confirmação de PIN. Validações salvas antes da V16 não possuem o digest de identidade e não podem ser associadas retroativamente a um email; o controle começa nos novos envios. O email e seu digest não são retornados no comprovante ou no resumo do validador.

### Campo de comentários

Comentários dependem de `commentAllowed` (`FORMULARIO_VERSAO.comentario_permitido`) em cada versão. A configuração inicial desabilita comentários em vários formulários, incluindo os formulários de alunos; novos rascunhos também começam com o campo desabilitado. O frontend exibe **Comentários adicionais** somente quando o catálogo retorna `commentAllowed: true`, e envia o conteúdo em `comment` (avaliação geral) ou `subjects[].comment` (por disciplina). O backend salva em `AVALIACAO.comentario`, remove tags HTML e valida o limite de 1000 caracteres.

No painel, abra a versão em rascunho, marque **Permitir comentário**, preencha **Aviso sobre o comentário**, salve e publique. Para versões já publicadas, clone primeiro; a configuração publicada permanece imutável. Confirme o período, aprove e abra a campanha para disponibilizar a nova versão. Habilitar comentários não os torna obrigatórios. Envios com comentários em versões que não os permitem retornam `400`.

Para Docker Compose, crie `.env` na raiz. Ele é ignorado pelo Git. Os valores abaixo são exemplos locais; substitua todos os placeholders e nunca versione credenciais reais.

O arquivo [`.env.example`](.env.example) reúne as variáveis do backend, uma referência do frontend e alternativas para os domínios de teste. Copie-o para `.env` e ajuste os valores. Ele usa as portas externas `5435`, `8081` e `5175`; o nome do banco permanece `cpa-femass` para corresponder ao healthcheck atual. As opções antigas e específicas de outras formas de execução estão comentadas. Para o frontend, copie somente seu próprio `.env.example`, sem segredos. As URLs e portas do bloco abaixo são uma alternativa local às do arquivo.

```env
# Infraestrutura local
POSTGRES_DB=cpa-femass
POSTGRES_USER=postgres
QUARKUS_DATASOURCE_PASSWORD_VARIABLE=troque-esta-senha
POSTGRES_PORT=5433
BACKEND_PORT=8080
FRONTEND_PORT=5173

# CORS e Keycloak
QUARKUS_HTTP_CORS_ORIGINS_VARIABLE=http://localhost:5173
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=troque-admin-keycloak
KEYCLOAK_CPA_ADMIN_PASSWORD=troque-admin-cpa
KEYCLOAK_PORT=8180
KEYCLOAK_PUBLIC_URL=http://localhost:8180
KEYCLOAK_FRONTEND_URL=http://localhost:5173
KEYCLOAK_AUTH_SERVER_URL=http://keycloak:8080/realms/cpa
KEYCLOAK_CLIENT_ID=cpa-backend

# Banco, caso seja necessário sobrescrever o endereço interno do Compose
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/cpa-femass
QUARKUS_DATASOURCE_USERNAME=postgres

# Códigos de validação e PIN
VALIDACAO_CODIGO_EXPIRACAO=PT336H
# Segredo estável e aleatório, com pelo menos 32 bytes; não use este placeholder.
VALIDACAO_ALUNO_HASH_SEED=substitua-por-um-segredo-aleatorio-com-32-bytes-ou-mais
VERIFICACAO_EMAIL_PIN_EXPIRACAO=PT2H
VERIFICACAO_EMAIL_AUTORIZACAO_EXPIRACAO=PT15M
VERIFICACAO_EMAIL_DIGEST_SECRET=gere-um-segredo-longo-e-aleatorio
VERIFICACAO_EMAIL_CHAVE_MESTRA=

# Gmail API — habilite após configurar OAuth, remetente e segredo de digest.
VERIFICACAO_EMAIL_GMAIL_ENABLED=false
VERIFICACAO_EMAIL_GMAIL_CLIENT_ID=seu-client-id.apps.googleusercontent.com
VERIFICACAO_EMAIL_GMAIL_CLIENT_SECRET=seu-client-secret
VERIFICACAO_EMAIL_GMAIL_REFRESH_TOKEN=seu-refresh-token
VERIFICACAO_EMAIL_GMAIL_FROM=remetente@example.com
VERIFICACAO_EMAIL_GMAIL_CONNECT_TIMEOUT=PT10S
VERIFICACAO_EMAIL_GMAIL_REQUEST_TIMEOUT=PT15S

# Frontend: copie esta parte também para o .env do CPAFrontend.
# São configurações públicas, incorporadas ao bundle durante o build.
VITE_API_BASE_URL=http://localhost:8080
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=cpa
VITE_KEYCLOAK_CLIENT_ID=cpa-frontend
VITE_CPA_CAMPAIGN_UNAVAILABLE_MESSAGE=A pesquisa está indisponível no momento. Tente novamente mais tarde.

# Opcional, exportar no terminal ao executar os testes do frontend.
PLAYWRIGHT_CHANNEL=msedge
```

As durações usam o formato ISO-8601 (`PT15M`, `PT2H`, `PT336H`). Cada repositório tem seu próprio `.env` e Compose: as variáveis do frontend acima não são encaminhadas pelo Compose do backend. O `.env` fornece valores para a interpolação do Compose; somente as chaves declaradas em `environment` chegam aos contêineres.

### Referência completa: infraestrutura e autenticação

| Variável | Destino / finalidade | Padrão / observação |
| --- | --- | --- |
| `POSTGRES_DB` | Nome do banco inicializado pelo serviço PostgreSQL. | `cpa-femass`. |
| `POSTGRES_USER` | Usuário inicializado pelo PostgreSQL. | `postgres`. |
| `QUARKUS_DATASOURCE_PASSWORD_VARIABLE` | Entrada do Compose para a senha do PostgreSQL e do backend. | Defina uma senha própria; chega como `POSTGRES_PASSWORD` e `QUARKUS_DATASOURCE_PASSWORD`. |
| `POSTGRES_PORT` | Porta publicada do PostgreSQL no host. | `5433`; a porta interna continua `5432`. |
| `BACKEND_PORT` | Porta publicada da API no host. | `8080`; a porta interna continua `8080`. |
| `FRONTEND_PORT` | Porta publicada pelo Compose do CPAFrontend. | `5173`; não é utilizada pelo Compose deste backend. |
| `QUARKUS_DATASOURCE_JDBC_URL` | URL JDBC usada pela aplicação. | Compose: `jdbc:postgresql://postgres:5432/cpa-femass`; fora do Docker: `jdbc:postgresql://localhost:5433/cpa-femass`. |
| `QUARKUS_DATASOURCE_USERNAME` | Usuário usado pela aplicação para acessar o banco. | `postgres`; deve corresponder ao usuário do banco. |
| `QUARKUS_DATASOURCE_PASSWORD` | Senha usada diretamente pelo processo Java. | No Compose é preenchida por `QUARKUS_DATASOURCE_PASSWORD_VARIABLE`; fora do Docker, exporte esta chave diretamente. |
| `POSTGRES_PASSWORD` | Senha recebida pelo contêiner PostgreSQL. | No Compose é preenchida por `QUARKUS_DATASOURCE_PASSWORD_VARIABLE`; não é uma entrada independente desse Compose. |
| `QUARKUS_HTTP_CORS_ORIGINS_VARIABLE` | Entrada do Compose para as origens permitidas da API. | `http://localhost:5173`; mapeada para `QUARKUS_HTTP_CORS_ORIGINS`. |
| `QUARKUS_HTTP_CORS_ORIGINS` | Origens permitidas pelo processo Java. | `http://localhost:5173`; use a origem pública exata do frontend, com protocolo e porta. Para várias origens, separe por vírgulas. |
| `KEYCLOAK_ADMIN_USERNAME` | Administrador inicial do console Keycloak. | `admin`; mapeada para `KC_BOOTSTRAP_ADMIN_USERNAME`. |
| `KEYCLOAK_ADMIN_PASSWORD` | Senha inicial do administrador do console. | Configure uma senha própria; mapeada para `KC_BOOTSTRAP_ADMIN_PASSWORD`. |
| `KEYCLOAK_CPA_ADMIN_PASSWORD` | Senha inicial do usuário da aplicação `cpa-admin` no realm importado. | Configure uma senha própria. Não é a senha do administrador do console. |
| `KEYCLOAK_PORT` | Porta publicada do Keycloak no host. | `8180`; porta interna `8080`. |
| `KEYCLOAK_PUBLIC_URL` | Endereço público do Keycloak, acessível pelo navegador. | `http://localhost:8180`; mapeada para `KC_HOSTNAME`. |
| `KEYCLOAK_FRONTEND_URL` | Origem pública do frontend para redirecionamentos, logout e origens do cliente `cpa-frontend`. | `http://localhost:5173`, sem barra final; usada na importação do realm. |
| `KEYCLOAK_AUTH_SERVER_URL` | Endpoint do realm que a API usa para OIDC. | Compose: `http://keycloak:8080/realms/cpa`; fora dele, use uma URL acessível pelo processo Java. Inclua `/realms/cpa`. |
| `KEYCLOAK_CLIENT_ID` | Cliente / audiência esperada pela API no access token. | `cpa-backend`; deve corresponder ao mapper de audiência do frontend. |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | Alternativa nativa do Quarkus para sobrescrever `quarkus.oidc.auth-server-url`. | Opcional; se passada ao processo Java, prevalece sobre o valor de `KEYCLOAK_AUTH_SERVER_URL` referenciado no arquivo de propriedades. Não é encaminhada pelo Compose atual. |

Se mudar `POSTGRES_DB` ou `POSTGRES_USER`, ajuste também a URL JDBC, o usuário da aplicação e o healthcheck do PostgreSQL, que atualmente usa `cpa-femass` e `postgres` fixos. Dentro do Compose, use o nome do serviço `postgres` e a porta interna; fora dele, use o endereço e a porta publicados. Mudar credenciais no `.env` não modifica automaticamente usuários de um banco já inicializado.

Para executar o backend fora do Docker, exporte `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`, `QUARKUS_HTTP_CORS_ORIGINS` e as demais configurações necessárias no ambiente do processo. Os aliases com sufixo `_VARIABLE` são exclusivos do Compose. Para usar `QUARKUS_OIDC_AUTH_SERVER_URL` em um contêiner, adicione-a explicitamente ao `environment` ou passe-a ao iniciar o contêiner; apenas inseri-la no `.env` não basta.

O Compose também define `KC_HEALTH_ENABLED=true` e `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`. As chaves `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` e `KC_HOSTNAME` são preenchidas pelos aliases acima; declará-las apenas no `.env` não substitui esse mapeamento. Alterações de `KEYCLOAK_FRONTEND_URL` ou da senha inicial não atualizam um realm que já existe: configure o cliente ou a conta no Keycloak conforme a seção de painel administrativo.

### Referência completa: validação e e-mail

| Variável | Finalidade | Padrão |
| --- | --- | --- |
| `VALIDACAO_CODIGO_EXPIRACAO` | Validade dos códigos de comprovação de participação. | `PT336H` (14 dias). |
| `VALIDACAO_ALUNO_HASH_SEED` | Chave secreta do HMAC-SHA-256 usado para CPF e matrícula dos alunos. | Obrigatória para envios de alunos; mínimo de 32 bytes UTF-8, sem padrão. Nunca use `VITE_`. |
| `VERIFICACAO_EMAIL_PIN_EXPIRACAO` | Validade do PIN enviado por e-mail. | `PT2H`. |
| `VERIFICACAO_EMAIL_AUTORIZACAO_EXPIRACAO` | Validade da autorização de envio após confirmar o PIN. | `PT15M`. |
| `VERIFICACAO_EMAIL_DIGEST_SECRET` | Segredo usado para calcular o digest dos endereços de e-mail. | Configure um segredo longo e aleatório; não utilize os fallbacks locais em produção. |
| `VERIFICACAO_EMAIL_CHAVE_MESTRA` | Chave compartilhada para acesso aos formulários de professor e funcionário, como alternativa ao PIN de email. | Vazia por padrão (acesso por chave desabilitado). Configure somente no backend, com um valor longo e aleatório de até 1024 caracteres. |
| `VERIFICACAO_EMAIL_GMAIL_ENABLED` | Habilita envio real pela Gmail API. | `false`. |
| `VERIFICACAO_EMAIL_GMAIL_CLIENT_ID` | Client ID OAuth2 do Google. | `not-configured`; obrigatório para envio real. |
| `VERIFICACAO_EMAIL_GMAIL_CLIENT_SECRET` | Client secret OAuth2 do Google. | `not-configured`; obrigatório para envio real. |
| `VERIFICACAO_EMAIL_GMAIL_REFRESH_TOKEN` | Refresh token da conta remetente. | `not-configured`; obrigatório para envio real. |
| `VERIFICACAO_EMAIL_GMAIL_FROM` | Endereço da conta remetente autorizada. | `not-configured`; obrigatório para envio real. |
| `VERIFICACAO_EMAIL_GMAIL_CONNECT_TIMEOUT` | Timeout de conexão HTTP da integração Gmail. | `PT10S`. |
| `VERIFICACAO_EMAIL_GMAIL_REQUEST_TIMEOUT` | Timeout de cada requisição HTTP da integração Gmail. | `PT15S`. |

### Referência do frontend

| Variável | Finalidade | Padrão / observação |
| --- | --- | --- |
| `VITE_API_BASE_URL` | URL pública da API acessível pelo navegador. | Compose do frontend: `http://localhost:8080`; não use `http://backend:8080`. |
| `VITE_KEYCLOAK_URL` | URL pública do Keycloak, sem `/realms/cpa`. | Compose do frontend: `http://localhost:8180`. |
| `VITE_KEYCLOAK_REALM` | Realm do login administrativo. | Docker: `cpa`; configure ao executar o Vite. |
| `VITE_KEYCLOAK_CLIENT_ID` | Cliente público OIDC do frontend. | `cpa-frontend`. |
| `VITE_CPA_CAMPAIGN_UNAVAILABLE_MESSAGE` | Mensagem de indisponibilidade quando a API não informar uma. | `A pesquisa está indisponível no momento. Tente novamente mais tarde.` |
| `PLAYWRIGHT_CHANNEL` | Navegador dos testes E2E do frontend. | `msedge`; exporte no terminal, por exemplo `chrome` se instalado. |

As variáveis `VITE_` são públicas e lidas durante o build, não pelo Nginx em execução. Reconstrua o frontend após alterá-las. O Dockerfile do frontend recebe argumentos para a URL da API e as três configurações Keycloak; a mensagem personalizada é lida do `.env` / `.env.production` no contexto de build. Veja o README do CPAFrontend para os detalhes. Se mudar a origem do frontend, ajuste CORS e `KEYCLOAK_FRONTEND_URL`.

### Variáveis presentes em configurações antigas

| Variável | Situação atual |
| --- | --- |
| `VITE_CPA_CAMPAIGN` | Não é mais consumida. A campanha é selecionada automaticamente por `/campanhas/atual/disponibilidade`, considerando ativação, estado e período. Pode ser removida. |
| `KEYCLOAK_DB_PASSWORD` | Não é consumida pelo Compose atual. O Keycloak usa `start-dev`, sem banco externo configurado; esta chave não muda sua senha nem o banco da aplicação. Um banco externo exige configuração explícita do serviço Keycloak. |
| `VERIFICACAO_EMAIL_MAX_TENTATIVAS`, `VERIFICACAO_EMAIL_MAX_REENVIOS_JANELA`, `VERIFICACAO_EMAIL_JANELA_REENVIO` | Removidas: não são consumidas pelo backend ou pelo Compose. Podem ser retiradas do `.env`; não há limites de tentativas, solicitações ou reenvios na aplicação. |

### Variáveis das imagens Java alternativas

O Compose usa o **Dockerfile da raiz**, com `java -jar` em uma imagem Temurin. Os modelos `src/main/docker/Dockerfile.jvm` e `Dockerfile.legacy-jar` usam `run-java.sh` e documentam as opções abaixo; elas só se aplicam ao construir e executar essas imagens alternativas, não ao Compose atual.

| Variáveis | Uso nas imagens alternativas |
| --- | --- |
| `JAVA_OPTS`, `JAVA_OPTS_APPEND` | Substituir ou acrescentar opções da JVM; os modelos já definem host HTTP e gerenciador de logging em `JAVA_OPTS_APPEND`. |
| `JAVA_APP_JAR` | Caminho do JAR executado; definido pelos modelos como `/deployments/quarkus-run.jar`. |
| `LANGUAGE` | Idioma definido pelos modelos: `en_US:en`. |
| `JAVA_MAX_MEM_RATIO`, `JAVA_INITIAL_MEM_RATIO`, `JAVA_MAX_INITIAL_MEM` | Dimensionamento de heap a partir da memória do contêiner; padrões descritos nos modelos: `50`, `25` e `4096` MB, respectivamente. |
| `JAVA_DIAGNOSTICS`, `JAVA_DEBUG`, `JAVA_DEBUG_PORT` | Diagnósticos e depuração remota; desabilitados por padrão, porta de debug `5005`. |
| `CONTAINER_CORE_LIMIT`, `CONTAINER_MAX_MEMORY` | Limites de CPU e memória considerados pelo script de execução. |
| `GC_MIN_HEAP_FREE_RATIO`, `GC_MAX_HEAP_FREE_RATIO`, `GC_TIME_RATIO`, `GC_ADAPTIVE_SIZE_POLICY_WEIGHT` | Ajustes de proporções e política do coletor de lixo. |
| `GC_METASPACE_SIZE`, `GC_MAX_METASPACE_SIZE`, `GC_CONTAINER_OPTIONS` | Tamanho de metaspace e opções para selecionar/configurar o coletor de lixo. |
| `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` | Proxy e exceções de proxy do script/imagem alternativa. |

Esta referência cobre as variáveis declaradas nos arquivos de configuração, Compose, Dockerfiles e código do projeto, além das chaves da configuração anterior. Propriedades adicionais do Quarkus também podem ser configuradas pelo nome nativo de ambiente correspondente, mas só chegam a um contêiner quando forem explicitamente passadas a ele.

### Opção 2: Local com Postgres do Docker

1. **Inicie apenas o Postgres**
```bash
docker compose up postgres
```

2. **Rode o backend em modo dev**
```bash
./mvnw quarkus:dev
```

O backend estará em `http://localhost:8080`

Com o perfil de desenvolvimento ativo (incluindo `./mvnw quarkus:dev`), a documentação
interativa fica disponível em `http://localhost:8080/swagger-ui/`. O documento OpenAPI
correspondente está em `http://localhost:8080/openapi`. Esses endpoints são desabilitados
nos demais perfis, inclusive em produção.

### Opção 3: Totalmente Local

Se você tiver PostgreSQL instalado localmente na porta padrão `5432`, apenas:

```bash
./mvnw quarkus:dev
```

## ✉️ Envio real de verificação por Gmail API

### Acesso por email ou chave mestra

Configure `VERIFICACAO_EMAIL_CHAVE_MESTRA` no `.env` deste backend para permitir que professores e funcionários acessem a pesquisa com uma chave fornecida pela comissão. O Compose encaminha essa variável ao container; recrie o backend após alterá-la. Não use prefixo `VITE_`, não versione a chave e não a coloque no frontend. Sem valor, a opção fica indisponível. Use uma chave longa e aleatória e distribua-a apenas aos participantes autorizados.

Ao entrar na etapa de acesso, o frontend consulta `GET /verificacao-email/configuracao`, que retorna somente `{ "emailDisponivel": boolean, "chaveMestraDisponivel": boolean }`, sem segredos e sem cache. O email está disponível quando Gmail está habilitado, client ID, client secret e refresh token estão preenchidos (sem o placeholder `not-configured`), o remetente tem formato válido e `VERIFICACAO_EMAIL_DIGEST_SECRET` foi configurado. Essa consulta verifica a configuração local; credenciais revogadas, quotas e falhas de rede do Google são detectadas no envio, que retorna `503` e também permite recorrer à chave mestra.

Com email configurado, o participante pode escolher PIN institucional ou chave. Sem configuração, o email fica somente para leitura, com **Serviço de email indisponível** e orientação para inserir a chave mestra. Se nenhuma alternativa estiver configurada, o participante deve contatar a comissão.

`POST /verificacao-email/chave-mestra` recebe `{ "chave": "<segredo>", "publico": "professor", "campaign": "cpa-2026", "form": "docente_gestao", "formVersion": 1 }`. Aceita somente professor ou funcionário e formulário publicado da campanha disponível. Retorna `{ "submissionToken": "<autorização opaca>" }`; chave incorreta retorna `401`, dados inválidos `400`, campanha indisponível `409` e chave não configurada `503`. A chave é comparada no backend, não é persistida e não segue no envio das respostas. A autorização usa `VERIFICACAO_EMAIL_AUTORIZACAO_EXPIRACAO`, fica vinculada ao público e à campanha e permite concluir sua jornada de formulários, como a autorização do PIN. As regras de período, ativação e publicação continuam sendo verificadas em cada envio.

O envio dos PINs para professores e funcionários usa a Gmail API com OAuth2. O backend
recebe somente configurações por ambiente; client secret, refresh token e o segredo HMAC
do e-mail não devem ser commitados nem registrados em logs.

O escopo utilizado é somente `https://www.googleapis.com/auth/gmail.send`. A conta que
autoriza o OAuth é a conta remetente; os destinatários dos PINs não precisam autorizar o
aplicativo.

### Variáveis de ambiente

Defina as variáveis abaixo no ambiente de execução:

```text
VERIFICACAO_EMAIL_GMAIL_ENABLED=true
VERIFICACAO_EMAIL_GMAIL_CLIENT_ID=<client-id>
VERIFICACAO_EMAIL_GMAIL_CLIENT_SECRET=<client-secret>
VERIFICACAO_EMAIL_GMAIL_REFRESH_TOKEN=<refresh-token>
VERIFICACAO_EMAIL_GMAIL_FROM=<conta-gmail-remetente>
VERIFICACAO_EMAIL_DIGEST_SECRET=<segredo-aleatorio-longo>
VERIFICACAO_EMAIL_GMAIL_CONNECT_TIMEOUT=PT10S
VERIFICACAO_EMAIL_GMAIL_REQUEST_TIMEOUT=PT15S
```

Nunca coloque valores reais em `application.properties`, `docker-compose.yml`, logs,
respostas HTTP ou commits.

### Configuração para desenvolvimento local

Para um projeto pequeno, uma conta pessoal Gmail dedicada (`@gmail.com`) pode ser usada
em desenvolvimento e em produção de baixo volume.

1. Crie uma conta Gmail dedicada para o sistema.
2. Crie um projeto no [Google Cloud Console](https://console.cloud.google.com/).
3. Em **APIs e serviços → Biblioteca**, ative a **Gmail API**.
4. Em **Google Auth Platform → Branding**, informe o nome do aplicativo, e-mail de
   suporte e e-mail de contato.
5. Em **Público-alvo**, escolha **Externo** e adicione a conta remetente em **Usuários
   de teste**.
6. Em **Acesso a dados**, adicione somente o escopo
   `https://www.googleapis.com/auth/gmail.send`.
7. Em **Clientes**, crie um cliente OAuth do tipo **Desktop app** e baixe o JSON.
8. Execute o utilitário local deste repositório para iniciar o fluxo OAuth com
   `access_type=offline` e `prompt=consent`:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\generate-gmail-refresh-token.ps1
   ```

   O script procura o JSON baixado em `Downloads\client_secret_*.json`, mostra uma URL
   para o navegador e aguarda o retorno local em `127.0.0.1:8766`. Faça login somente
   com a conta remetente e aceite o escopo `gmail.send`. O refresh token é salvo em
   `Downloads\cpabackend-gmail-refresh-token.txt` e nunca é impresso pelo script.
   Nunca envie o JSON, o client secret ou o refresh token para o repositório ou para
   uma conversa.

   Para usar outro JSON, porta ou arquivo de saída:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\generate-gmail-refresh-token.ps1 `
     -ClientJsonPath 'C:\caminho\client_secret.json' `
     -Port 8766 `
     -OutputPath 'C:\caminho\cpabackend-gmail-refresh-token.txt'
   ```

   Se o arquivo de saída já existir, o script interrompe a execução para evitar
   sobrescrita acidental. Use `-Force` somente durante uma rotação intencional, depois
   de revogar o token anterior no Google Cloud.

Na raiz do projeto, crie um arquivo `.env` local. Ele já é ignorado pelo Git:

```env
VERIFICACAO_EMAIL_GMAIL_ENABLED=true
VERIFICACAO_EMAIL_GMAIL_CLIENT_ID=<client-id-do-json>
VERIFICACAO_EMAIL_GMAIL_CLIENT_SECRET=<client-secret-do-json>
VERIFICACAO_EMAIL_GMAIL_REFRESH_TOKEN=<refresh-token-gerado-localmente>
VERIFICACAO_EMAIL_GMAIL_FROM=<conta-gmail-remetente>
VERIFICACAO_EMAIL_DIGEST_SECRET=<segredo-aleatorio-longo>
VERIFICACAO_EMAIL_GMAIL_CONNECT_TIMEOUT=PT10S
VERIFICACAO_EMAIL_GMAIL_REQUEST_TIMEOUT=PT15S
```

Suba ou recrie o backend para carregar o arquivo:

```bash
docker compose up -d --force-recreate backend
```

O `docker-compose.yml` já mapeia essas variáveis para o container. Para verificar a
configuração sem revelar segredos, confirme que o Gmail está habilitado e que os cinco
valores sensíveis estão presentes no container. Não use `docker inspect` para imprimir
o ambiente completo.

### Colocar o aplicativo OAuth em produção

O modo **Testando** é adequado para desenvolvimento, mas autorizações de usuários de
teste podem expirar após sete dias, inclusive o refresh token de um fluxo offline.
Para produção, faça o procedimento a seguir:

1. Crie um projeto Google Cloud separado para produção. Mantenha projetos distintos para
   desenvolvimento, homologação e produção.
2. Ative a **Gmail API** no projeto de produção.
3. Repita a configuração de **Branding**, **Público-alvo** e **Acesso a dados** usando
   os dados reais do aplicativo.
4. Confirme que o público está como **Externo** quando a conta remetente for pessoal
   `@gmail.com`. O modo **Interno** só se aplica a uma organização Google Workspace.
5. Crie um cliente OAuth de produção do tipo **Desktop app** e autorize novamente a conta
   remetente. Gere um refresh token próprio para produção; não reutilize o token de
   desenvolvimento.
6. Em **Público-alvo**, conclua o Branding e clique em **Publicar app**. Confirme a
   publicação como **Em produção**.
7. Se o Google solicitar verificação, siga a **Central de verificação**. O escopo
   `gmail.send` é classificado como sensível e pode exigir verificação para uso mais
   amplo. Para uso pessoal limitado, o Google pode permitir continuar com o aviso de
   aplicativo não verificado, mas isso não substitui a verificação caso o projeto cresça.
8. Armazene o client secret, o refresh token e o segredo HMAC em Secret Manager, Vault
   ou mecanismo equivalente. Não use um `.env` versionado em produção.
9. Configure as mesmas variáveis de ambiente no serviço de produção e reinicie/recrie
   o backend para que os valores sejam carregados.

Para uma conta pessoal Gmail, mantenha o volume baixo e observe as cotas e as políticas
de envio do Gmail. Para maior volume ou identidade institucional, prefira uma conta
Google Workspace dedicada com domínio próprio.

### Teste manual e rotação

Após iniciar o backend, solicite uma verificação para um endereço institucional permitido:

```bash
curl -X POST http://localhost:8080/verificacao-email/solicitar \
  -H 'Content-Type: application/json' \
  -d '{"email":"destinatario@femass.edu.br","publico":"professor","campaign":"cpa-2026","form":"docente_gestao","formVersion":1}'
```

Confirme o PIN recebido e execute o fluxo de submissão usando o `submissionToken` retornado.
Uma resposta `202 Accepted` indica que a solicitação foi aceita e que o envio não retornou
erro; a entrega final ainda deve ser conferida na caixa de entrada ou spam.

Para rotacionar credenciais, revogue o cliente/token antigo no Google Cloud, gere novas
credenciais, atualize o gerenciador de segredos e reinicie o serviço. O
`VERIFICACAO_EMAIL_DIGEST_SECRET` também deve ser tratado como segredo de produção; sua
troca invalida os digests existentes.

## 📋 Endpoints Disponíveis

### 1. Gerar código de validação
```bash
POST /qrcode/gerar
Content-Type: application/json

{"aceiteTermosCondicoesServico":true}
```

**Resposta:**
```json
{
  "qrCode": "codigo-opaco-base64url",
  "codigoValidacao": "codigo-opaco-base64url"
}
```

O banco persiste em `NEW_HASH` um UUID com os primeiros 128 bits do SHA-256 do segredo. O segredo `codigoValidacao`
nunca é recuperável pelo banco após a geração.

### 2. Validar código
```bash
PUT /validacao/validar-hash?codigoValidacao=CODIGO_VALIDACAO
```

O parâmetro `hash` permanece aceito temporariamente como alias de compatibilidade.
O código é de uso único e expira após `validacao.codigo-expiracao`, configurável pela variável
`VALIDACAO_CODIGO_EXPIRACAO` (padrão: 14 dias / `PT336H`).

O endpoint `/qrcode/decodificar` não decodifica payload: códigos são opacos e não carregam CPF,
matrícula, curso ou disciplinas.

### 3. Buscar código (compatibilidade)
```bash
GET /validacao/buscar-hash?codigoValidacao=SEU_CODIGO
```

### 4. Verificar Status
```bash
GET /validacao/verificar-status?codigoValidacao=SEU_CODIGO
```

**Resposta:**
```json
{
  "codigoValidacao": "codigo-opaco-base64url",
  "validado": false,
  "status": "PENDENTE",
  "mensagem": "Este codigo ainda está pendente de validação"
}
```

### 5. Validar código (Detalhado)
```bash
PUT /validacao/validar-hash/detalhado?codigoValidacao=SEU_CODIGO
```

**Resposta:**
```json
{
  "codigoValido": true,
  "id": 1,
  "codigoValidacao": "codigo-opaco-base64url",
  "validado": true,
  "dataCriacao": "2026-05-15T10:30:45.123456",
  "dataValidacao": "2026-05-15T10:32:15.654321",
  "tentativasValidacao": 1,
  "tempoDecorridoMs": 90531
}
```

## 🛠️ Desenvolvimento

### Convenção de acesso a dados

Os repositories em `src/main/java/org/femass/repository` são responsáveis por consultas e operações de persistência usando Panache. Os services mantêm as validações, regras de negócio e a orquestração dos casos de uso, além de definir os limites transacionais com `@Transactional`. Novas entidades devem seguir essa separação: consultas específicas ficam no repository correspondente e não devem ser executadas diretamente pelos services.

### Modo Dev com Live Reload
```bash
./mvnw quarkus:dev
```

Qualquer alteração no código é recarregada automaticamente.

Dev UI disponível em: `http://localhost:8080/q/dev/`

Swagger UI disponível somente em desenvolvimento: `http://localhost:8080/swagger-ui/`

### Compilação e Packaging

**Build JAR:**
```bash
./mvnw package
```

**Rodar JAR:**
```bash
java -jar target/quarkus-app/quarkus-run.jar
```

**Build Nativo (GraalVM):**
```bash
./mvnw package -Dnative
```

## 🐘 Configuração PostgreSQL

### Via Docker Compose
Automático com credenciais:
- **Host**: `postgres` (interno) / `localhost:5433` (externo)
- **BD**: `cpa-femass`
- **User**: `postgres`
- **Password**: `Cp2af0em2as6s`

### Variáveis de Ambiente
Você pode sobrescrever as credenciais via variáveis:

```bash
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://seu-host:5432/seu-bd
export QUARKUS_DATASOURCE_USERNAME=seu-user
export QUARKUS_DATASOURCE_PASSWORD=sua-senha
./mvnw quarkus:dev
```

## 📊 Estrutura de Dados

### Entidade: Validacao

```java
@Entity
public class Validacao extends PanacheEntity {
    private UUID newHash;                    // primeiros 128 bits do SHA-256 do código
    private Boolean validado;               // true/false
    private LocalDateTime dataCriacao;      // Quando foi criado
    private LocalDateTime expiraEm;         // Prazo configurável
    private LocalDateTime dataValidacao;    // Quando foi validado
    private Integer tentativasValidacao;    // Contador de tentativas
}
```

## 🔄 Fluxo de Uso Recomendado

```
1. Frontend solicita um código opaco
   POST /qrcode/gerar
   ← Recebe: codigoValidacao

2. Frontend gera QR code com a biblioteca (ex: qrcode.js)
   Usa o codigoValidacao recebido

3. Frontend exibe QR code ao usuário

4. Usuário escaneia QR code
   ← Captura o codigoValidacao

5. Frontend valida o código
   PUT /validacao/validar-hash?codigoValidacao=CODIGO_CAPTURADO
   ← Código marcado como validado e inutilizado
```

## 📦 Dependências Principais

- **Quarkus 3.35.3**
- **Hibernate ORM Panache**
- **PostgreSQL JDBC Driver**
- **REST Jackson** (JSON)

## ✅ Testes

Rodar a suíte de testes:

```bash
./mvnw test
```

Testes incluem validação do endpoint `/validacao`.

## 📝 Notas Importantes

### Conexão Recusada (Docker)
Se o backend rodar antes do Postgres subir completamente, espere e reexecute. O `docker-compose.yml` inclui `healthcheck` para evitar isso.

### Porta 5433 vs 5432
- **5433**: Porta mapeada no host (usada por aplicações locais)
- **5432**: Porta interna do container (usada entre serviços no Compose)

## 🚢 Deployment

### Com Docker Compose
```bash
docker compose up -d
```

Logs:
```bash
docker compose logs -f backend
```

Parar:
```bash
docker compose down
```

### Em Produção
1. Crie build nativo:
```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

2. Configure variáveis de ambiente com credenciais do BD

3. Suba a imagem Docker

## 📞 Suporte Rápido

| Problema | Solução |
|----------|---------|
| "Connection refused" | Certifique-se de que Postgres está rodando na porta 5433 (local) ou via Compose |
| "No such service" | Use `docker compose up postgres` ou `docker compose up` (sem nome do serviço) |
| "Bad source file" | Rode `./mvnw clean compile` para limpar a cache |
| Porta 8080 em uso | Mude a porta no `docker-compose.yml` ou encerre o processo que usa 8080 |

## 📄 Estrutura de Diretórios

```
cpa-backend/
├── src/
│   ├── main/
│   │   ├── java/org/femass/
│   │   │   ├── entity/Validacao.java
│   │   │   ├── service/ValidacaoService.java
│   │   │   ├── resource/ValidacaoResource.java
│   │   │   └── dto/
│   │   │       ├── ValidacaoResponseDTO.java
│   │   │       ├── ValidacaoDetailResponseDTO.java
│   │   │       ├── ValidacaoStatusDTO.java
│   │   │       └── ErrorResponseDTO.java
│   │   ├── resources/application.properties
│   │   └── docker/
│   │       └── Dockerfile.jvm
│   └── test/
├── docker-compose.yml
├── Dockerfile.compose
├── pom.xml
└── README.md
```

## 📚 Referências

- [Quarkus Documentation](https://quarkus.io/guides/)
- [Quarkus REST](https://quarkus.io/guides/rest)
- [Hibernate ORM Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [PostgreSQL JDBC](https://jdbc.postgresql.org/)

---

**Desenvolvido com ❤️ em Quarkus**
# Códigos de validação

Os códigos entregues pelo QR Code são segredos opacos de 128 bits, gerados por `SecureRandom`.
O código não contém CPF, matrícula, curso ou disciplinas. Novos registros armazenam em
`NEW_HASH` um UUID com os primeiros 128 bits do SHA-256 do segredo; não existe endpoint
de decodificação nem recuperação do código. Cada código é de uso único. A V4 preserva
os tokens legados em `OLD_TOKEN`, mas eles não participam da validação ou do histórico.

### Migração da chave primária de `VALIDACAO`

A migration V6 adiciona `ID` como chave primária técnica identity e define
`NEW_HASH` como chave de negócio com índice `UNIQUE`. `NEW_HASH` aceita NULL para
preservar registros legados; o backend sempre preenche o UUID de novos códigos.
V4–V6 foram revisadas: bancos que aplicaram as versões anteriores precisam ser
recriados para executar esta sequência, pois os checksums do Flyway mudaram.

Em caso de rollback, faça backup e confirme que não existem FKs dependentes;
depois aplique uma migration corretiva compatível com os registros legados,
que têm `NEW_HASH` nulo. `OLD_TOKEN` não deve voltar a ser usado na validação.
# Administração autenticada

## Clonagem entre campanhas e remoção de perguntas

`POST /admin/campanhas/{codigoDestino}/formularios/clonar` exige `cpa-admin` e cria um novo formulário ativo com versão 1 em rascunho:

```json
{
  "campanhaOrigem": "cpa-2025",
  "formularioOrigem": "discente_gestao",
  "versaoOrigem": 1,
  "codigo": "discente_gestao",
  "nome": "Avaliação de gestão"
}
```

A resposta `201` contém `{ "codigo": "discente_gestao", "versao": 1 }`. O nome é opcional: quando omitido, é copiado da versão escolhida. Origem e destino devem ser campanhas diferentes; a origem pode estar encerrada ou inativa, e pode-se selecionar qualquer versão existente. O destino precisa estar em rascunho ou aprovado e volta a rascunho após a cópia. Metadados, comentários, perguntas, opções e seus estados individuais de ativação são copiados com novos identificadores, sem alterar a origem ou suas respostas. Os bloqueios das campanhas são adquiridos em ordem para preservar a consistência em operações concorrentes. Código duplicado no destino e destino fora da configuração retornam `409`, dados inválidos `400` e origem/destino/versão inexistente `404`.

A edição de rascunhos continua em `PUT /admin/campanhas/{codigo}/formularios/{formulario}/versoes/{numero}`. Perguntas removidas da lista ou enviadas com `ativo: false` são desativadas, preservando identificadores e opções. Uma lista vazia `perguntas: []` pode ser salva como rascunho; publicação exige pelo menos uma pergunta ativa com opções ativas. Versões publicadas não podem ter perguntas removidas ou editadas; clone uma versão antes de alterar seu conteúdo. A clonagem da versão mais recente dentro do mesmo formulário continua disponível no endpoint existente.

As APIs `/admin/*` exigem token OIDC válido, audiência `cpa-backend` (ou o valor de
`KEYCLOAK_CLIENT_ID`) e papel de realm `cpa-admin`. O frontend administrativo usa
o cliente público `cpa-frontend`, com Authorization Code e PKCE S256.

O arquivo `docker/keycloak/realm-cpa.json` inclui o cliente para instalações novas.
Configure `KEYCLOAK_FRONTEND_URL` com a origem do frontend (sem barra final;
padrão `http://localhost:5173`). Configure também `KEYCLOAK_PUBLIC_URL` com a URL
pública do Keycloak. O backend pode usar a URL interna em
`KEYCLOAK_AUTH_SERVER_URL`; os tokens devem ter o issuer público anunciado pelo
Keycloak e a audiência da API.

## Realm já existente

O `--import-realm` não atualiza um realm existente. No console do Keycloak:

1. Crie o cliente OpenID Connect `cpa-frontend`, com Client authentication
   desabilitado, Standard Flow habilitado e Direct Access Grants desabilitado.
2. Em Advanced, exija PKCE `S256`. Cadastre apenas a origem real do frontend em
   Web Origins; em Valid Redirect URIs, cadastre `ORIGEM/admin` e `ORIGEM/admin/*`;
   em Valid Post Logout Redirect URIs, cadastre `ORIGEM/`.
3. No client scope dedicado, adicione um mapper Audience com Included Client
   Audience `cpa-backend`, Add to access token habilitado e Add to ID token
   desabilitado. Garanta que o scope padrão `roles` inclua `realm_access.roles`.
4. Atribua o papel de realm `cpa-admin` aos usuários autorizados. A gestão de contas
   continua no Keycloak.
5. Se usar o cliente antigo `cpa-dev-cli`, adicione nele a mesma audiência da API.
6. Configure no frontend `VITE_KEYCLOAK_URL` (URL pública sem `/realms/cpa`),
   `VITE_KEYCLOAK_REALM=cpa` e `VITE_KEYCLOAK_CLIENT_ID=cpa-frontend`; reconstrua
   a imagem. Configure `QUARKUS_HTTP_CORS_ORIGINS` para a origem exata do frontend.

## Cadastros e desativação

Há listagem, consulta e edição em `/admin/campanhas`, `/admin/cursos` e
`/admin/disciplinas` (filtro opcional `cursoId`). Formulários e versões estão sob
`/admin/campanhas/{codigo}/formularios`. Os endpoints existentes de clonagem,
publicação, aprovação, abertura e encerramento permanecem disponíveis.

Use `PATCH .../status` com `{"ativo":false}` para desativar e `{"ativo":true}`
para reativar campanhas, cursos, disciplinas, formulários e versões. A migração
Flyway V13 mantém os registros anteriores ativos. Não há exclusão física.
Filhos de pais inativos ficam indisponíveis na pesquisa, mas mantêm o próprio
estado. Respostas históricas continuam acessíveis.

Perguntas e opções têm `ativo` no payload administrativo (padrão `true` quando
omitido). Ao salvar um rascunho, códigos existentes preservam seus IDs; itens
omitidos são desativados. Versões publicadas não aceitam edição de conteúdo.
Publicação exige perguntas ativas com opções ativas. Os endpoints públicos
filtram inativos e rejeitam novos envios referenciando cadastros desativados.

Erros administrativos retornam JSON com `message`: `400` para validação,
`404` para registros inexistentes, `409` para conflitos, `401` para ausência de
autenticação e `403` para falta de permissão.

## Testes administrativos

`AdminCadastrosResourceTest` verifica edição, desativação, hierarquia,
preservação de IDs e histórico. `AdminOidcResourceTest` habilita OIDC e usa um
servidor de teste com discovery/JWKS e tokens RSA assinados, sem `TestSecurity`;
verifica assinatura, expiração, audiência e papéis. Não utiliza o Keycloak real.

Execute os testes contra um banco PostgreSQL descartável, definindo
`QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME` e
`QUARKUS_DATASOURCE_PASSWORD`, e use Java 21 com `./mvnw test`.

## Período das campanhas

A migração V14 preserva campanhas existentes sem datas. Após atualizar, configure `dataInicio` e `dataFim` no painel; sem elas a pesquisa fica indisponível. Novos cadastros e edições exigem ambas as datas no formato `YYYY-MM-DD`. Os limites são inclusivos no fuso `America/Sao_Paulo`. A campanha precisa estar ativa e `ABERTA`; a abertura manual pode ocorrer antes do início. O fim do período bloqueia respostas sem modificar seu estado ou o histórico.

`GET /campanhas/atual/disponibilidade` seleciona a campanha vigente. Sem campanha, retorna HTTP 200 com `campanha`, `estado`, `dataInicio`, `dataFim` nulos e `disponivelParaResposta: false`. A consulta por código também valida o período. O catálogo oculta campanhas fora do período; envios indisponíveis retornam 409 em JSON antes de consumir autorizações ou persistir respostas.

Abertura, reativação e edição impedem sobreposição entre campanhas ativas e abertas, inclusive futuras, com HTTP 409 identificando o conflito. Um bloqueio transacional consultivo PostgreSQL serializa essas operações. Rascunhos e campanhas inativas podem ter períodos sobrepostos.
