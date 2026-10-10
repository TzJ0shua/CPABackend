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

## Configuração por `.env`

Para Docker Compose, crie `.env` na raiz. Ele é ignorado pelo Git. Os valores abaixo são exemplos locais; substitua todos os placeholders e nunca versione credenciais reais.

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
KEYCLOAK_AUTH_SERVER_URL=http://keycloak:8080/realms/cpa
KEYCLOAK_CLIENT_ID=cpa-backend

# Banco, caso seja necessário sobrescrever o endereço interno do Compose
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/cpa-femass
QUARKUS_DATASOURCE_USERNAME=postgres

# Códigos de validação e PIN
VALIDACAO_CODIGO_EXPIRACAO=PT336H
VERIFICACAO_EMAIL_PIN_EXPIRACAO=PT2H
VERIFICACAO_EMAIL_AUTORIZACAO_EXPIRACAO=PT15M
VERIFICACAO_EMAIL_MAX_TENTATIVAS=5
VERIFICACAO_EMAIL_MAX_REENVIOS_JANELA=3
VERIFICACAO_EMAIL_JANELA_REENVIO=PT1H
VERIFICACAO_EMAIL_DIGEST_SECRET=gere-um-segredo-longo-e-aleatorio

# Gmail API — mantenha false até configurar todas as cinco variáveis seguintes.
VERIFICACAO_EMAIL_GMAIL_ENABLED=false
VERIFICACAO_EMAIL_GMAIL_CLIENT_ID=seu-client-id.apps.googleusercontent.com
VERIFICACAO_EMAIL_GMAIL_CLIENT_SECRET=seu-client-secret
VERIFICACAO_EMAIL_GMAIL_REFRESH_TOKEN=seu-refresh-token
VERIFICACAO_EMAIL_GMAIL_FROM=remetente@example.com
VERIFICACAO_EMAIL_GMAIL_CONNECT_TIMEOUT=PT10S
VERIFICACAO_EMAIL_GMAIL_REQUEST_TIMEOUT=PT15S
```

As durações usam o formato ISO-8601 (`PT15M`, `PT2H`, `PT336H`). O Compose encaminha todas essas variáveis ao serviço correspondente.

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
