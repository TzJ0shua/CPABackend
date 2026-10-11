ALTER TABLE campanha ADD COLUMN data_inicio DATE;
ALTER TABLE campanha ADD COLUMN data_fim DATE;

-- Existing campaigns require explicit scheduling before accepting new responses.
ALTER TABLE campanha ADD CONSTRAINT ck_campanha_periodo CHECK (
    (data_inicio IS NULL AND data_fim IS NULL) OR
    (data_inicio IS NOT NULL AND data_fim IS NOT NULL AND data_inicio <= data_fim)
);
