CREATE TABLE jornada_aluno (
    id BIGSERIAL PRIMARY KEY,
    campanha_id BIGINT NOT NULL REFERENCES campanha(id),
    cpf_hash VARCHAR(64) NOT NULL CHECK (cpf_hash ~ '^[0-9a-f]{64}$'),
    matricula_hash VARCHAR(64) NOT NULL CHECK (matricula_hash ~ '^[0-9a-f]{64}$'),
    comprovante_id BIGINT UNIQUE REFERENCES validacao(id) ON DELETE CASCADE,
    UNIQUE (campanha_id, cpf_hash),
    UNIQUE (campanha_id, matricula_hash)
);
CREATE TABLE jornada_aluno_validacao (
    jornada_id BIGINT NOT NULL REFERENCES jornada_aluno(id) ON DELETE CASCADE,
    validacao_id BIGINT NOT NULL REFERENCES validacao(id) ON DELETE CASCADE,
    PRIMARY KEY (jornada_id, validacao_id)
);
CREATE INDEX ix_jornada_aluno_validacao ON jornada_aluno_validacao(validacao_id);
