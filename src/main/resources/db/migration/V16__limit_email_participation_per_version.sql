ALTER TABLE VALIDACAO ADD COLUMN email_digest VARCHAR(64);
ALTER TABLE VALIDACAO ADD CONSTRAINT ck_validacao_email_digest
    CHECK (email_digest IS NULL OR email_digest ~ '^[0-9a-f]{64}$');
CREATE UNIQUE INDEX uk_validacao_email_versao
    ON VALIDACAO(formulario_versao_id, email_digest) WHERE email_digest IS NOT NULL;

ALTER TABLE VERIFICACAO_EMAIL ADD COLUMN acesso_chave_mestra BOOLEAN NOT NULL DEFAULT FALSE;
-- Autorizações por chave não passaram pela confirmação de PIN, que incrementa tentativas.
UPDATE VERIFICACAO_EMAIL SET acesso_chave_mestra = TRUE
    WHERE autorizacao_digest IS NOT NULL AND codigo_validado_em IS NOT NULL AND tentativas = 0;
