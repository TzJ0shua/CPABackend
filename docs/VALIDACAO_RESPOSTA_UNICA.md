# Verificação de participação única

Auditoria realizada em 12/10/2026 nas branches de período de campanha.

## Regra verificada

O limite é uma participação por **versão do formulário na campanha**, conforme a decisão de permitir outras versões. Não é um limite único por público: a mesma pessoa pode responder formulários diferentes, outra versão ou outra campanha.

| Acesso | Identidade usada no bloqueio | Repetir a mesma versão |
| --- | --- | --- |
| Aluno | Hash do CPF **ou** da matrícula | HTTP 409 |
| Professor por email | Hash do email confirmado, normalizado | HTTP 409 |
| Funcionário por email | Hash do email confirmado, normalizado | HTTP 409 |
| Professor ou funcionário por chave mestra | Exceção previamente definida | Permitido |

O backend retorna `code: FORMULARIO_JA_RESPONDIDO` no conflito. Não confia no email informado no corpo do envio: utiliza a identidade da autorização confirmada. O frontend apresenta o aviso de repetição e não repete o POST automaticamente.

O limite também se aplica a formulários por disciplina. Trocar a disciplina não libera uma segunda participação na mesma versão; as disciplinas desejadas devem ser incluídas no envio inicial. Validar o QR Code ou expirar o comprovante não libera uma nova participação. Desativar e reativar o formulário preserva o vínculo de participação.

## Cenários e resultados

Não foi encontrada falha de duplicação nos fluxos testados. Foram ampliados os testes de regressão:

- Aluno: envio idêntico, mesmo CPF com outra matrícula e mesma matrícula com outro CPF.
- Professor e funcionário: novo PIN, nova autorização e mudança de maiúsculas/espaços no email.
- Todos os públicos: formulário geral e por disciplina, mudança de disciplina, outra versão, outro formulário e outra campanha.
- Envios simultâneos para todos os públicos e ambos os escopos: somente um sucesso, o outro HTTP 409, sem participação parcial adicional.
- Comprovante já validado ou expirado e autorização de email expirada; uma nova autorização não apaga a participação anterior.
- Autorizações ausentes, forjadas ou usadas em outro público/campanha são rejeitadas.
- A chave mestra permite repetir para professor e funcionário, mantendo a exceção aprovada.
- Navegador em desktop e celular: envio, recarregamento, nova identificação, rejeição da repetição e envio permitido em outra versão.

Validação executada: **90 testes do backend**, **86 testes unitários do frontend** e **54 testes de navegador**, todos aprovados; lint e build do frontend aprovados.

Os testes do backend utilizam HTTP e PostgreSQL reais em banco descartável; o envio de email é simulado. Os testes de navegador utilizam API simulada e verificam o comportamento da interface. O banco Docker em uso foi consultado somente para leitura: migrações V15 e V16 aplicadas e índices únicos de CPF, matrícula e email por versão presentes.

## Executar novamente

Use um PostgreSQL descartável e exclusivo para os testes, nunca o banco com respostas reais. Defina `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME` e `QUARKUS_DATASOURCE_PASSWORD` para esse banco e execute no backend:

```bash
./mvnw test
```

No frontend:

```bash
npm test
npm run lint
npm run build
npm run test:e2e
```

Os testes específicos são `StudentParticipationResourceTest`, `StaffParticipationResourceTest`, `ChaveMestraResourceTest` e `e2e/participation-limit.e2e.ts`.
