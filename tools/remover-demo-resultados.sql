-- Remove apenas o lote fictício de resultados criado pelo script demo-resultados.sql.
BEGIN;
DELETE FROM resposta WHERE avaliacao_id IN (
    SELECT a.id FROM avaliacao a JOIN validacao v ON v.id = a.validacao_id
    WHERE v.old_token LIKE 'CPA_DEMO_RESULTADOS_20261019_%'
);
DELETE FROM avaliacao WHERE validacao_id IN (
    SELECT id FROM validacao WHERE old_token LIKE 'CPA_DEMO_RESULTADOS_20261019_%'
);
DELETE FROM validacao WHERE old_token LIKE 'CPA_DEMO_RESULTADOS_20261019_%';
COMMIT;
