ALTER TABLE VALIDACAO ADD COLUMN cpf_hash VARCHAR(64);
ALTER TABLE VALIDACAO ADD COLUMN matricula_hash VARCHAR(64);
ALTER TABLE VALIDACAO ADD COLUMN formulario_versao_id BIGINT REFERENCES FORMULARIO_VERSAO(id);
ALTER TABLE VALIDACAO ADD CONSTRAINT ck_validacao_identidade_aluno CHECK (
    (cpf_hash IS NULL AND matricula_hash IS NULL) OR
    (cpf_hash IS NOT NULL AND matricula_hash IS NOT NULL AND formulario_versao_id IS NOT NULL
        AND cpf_hash ~ '^[0-9a-f]{64}$' AND matricula_hash ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX uk_validacao_aluno_cpf_versao
    ON VALIDACAO(formulario_versao_id, cpf_hash) WHERE cpf_hash IS NOT NULL;
CREATE UNIQUE INDEX uk_validacao_aluno_matricula_versao
    ON VALIDACAO(formulario_versao_id, matricula_hash) WHERE matricula_hash IS NOT NULL;
ALTER TABLE AVALIACAO ADD COLUMN validacao_id BIGINT REFERENCES VALIDACAO(id);
CREATE INDEX ix_avaliacao_validacao ON AVALIACAO(validacao_id);
