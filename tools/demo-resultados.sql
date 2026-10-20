-- Demonstração LOCAL solicitada em 19/10/2026. Execute manualmente com psql.
-- Dez avaliações completas, dez identidades fictícias. Reexecutar não duplica dados.
-- Nenhum CPF, matrícula ou e-mail real é usado; os registros não geram comprovantes.
BEGIN;
DO $$
DECLARE
    scenario record;
    version_id bigint;
    audience text;
    validation_id bigint;
    evaluation_id bigint;
    question record;
    choice record;
    demo_marker text;
    expected integer;
BEGIN
    FOR scenario IN SELECT * FROM (VALUES
        (1, 'discente_disciplinas', 1::bigint, 0),
        (2, 'discente_disciplinas', 1::bigint, 1),
        (3, 'discente_disciplinas', 80::bigint, 2),
        (4, 'discente_disciplinas', 80::bigint, 3),
        (5, 'discente_instituicao', NULL::bigint, 1),
        (6, 'discente_instituicao', NULL::bigint, 4),
        (7, 'docente_gestao', NULL::bigint, 0),
        (8, 'docente_gestao', NULL::bigint, 3),
        (9, 'funcionario_auto', NULL::bigint, 2),
        (10, 'gestao_auto', NULL::bigint, 4)
    ) AS s(number, form_code, subject_id, offset_value) LOOP
        demo_marker := 'CPA_DEMO_RESULTADOS_20261019_' || lpad(scenario.number::text, 2, '0');
        IF EXISTS (SELECT 1 FROM validacao WHERE old_token = demo_marker) THEN CONTINUE; END IF;
        SELECT v.id, coalesce(v.publico, f.publico) INTO STRICT version_id, audience
        FROM formulario_versao v JOIN formulario f ON f.id = v.formulario_id
        JOIN campanha c ON c.id = f.campanha_id
        WHERE c.codigo = 'cpa-2026' AND f.codigo = scenario.form_code
          AND v.numero = 1 AND v.estado = 'PUBLICADA';

        validation_id := nextval('validacao_id_seq');
        INSERT INTO validacao (id, old_token, formulario_versao_id, validado,
            aceite_termos_condicoes_servico, data_criacao, expira_em, tentativas_validacao,
            cpf_hash, matricula_hash, email_digest)
        VALUES (validation_id, demo_marker, version_id, false, false, localtimestamp,
            localtimestamp, 0,
            CASE WHEN audience = 'ALUNO' THEN md5(demo_marker || ':cpf') || md5(demo_marker || ':cpf:demo') END,
            CASE WHEN audience = 'ALUNO' THEN md5(demo_marker || ':mat') || md5(demo_marker || ':mat:demo') END,
            CASE WHEN audience <> 'ALUNO' THEN md5(demo_marker || ':email') || md5(demo_marker || ':email:demo') END);

        evaluation_id := nextval('avaliacao_id_seq');
        INSERT INTO avaliacao (id, publico, formulario_versao_id, validacao_id, disciplina_id, comentario)
        SELECT evaluation_id, audience, version_id, validation_id, scenario.subject_id,
            CASE WHEN v.comentario_permitido THEN
                CASE WHEN scenario.number = 7 THEN '[DEMONSTRAÇÃO] A comunicação pode melhorar; a equipe tem bom atendimento.'
                ELSE '[DEMONSTRAÇÃO] Boa organização, com oportunidades de melhoria no planejamento.' END
            END FROM formulario_versao v WHERE v.id = version_id;

        FOR question IN SELECT id, ordem FROM pergunta_versao
            WHERE formulario_versao_id = version_id AND ativo ORDER BY ordem, id LOOP
            SELECT * INTO STRICT choice FROM opcao_pergunta
            WHERE pergunta_versao_id = question.id AND ativo
            ORDER BY ordem, id OFFSET ((question.ordem + scenario.offset_value) % 5) LIMIT 1;
            INSERT INTO resposta (id, avaliacao_id, pergunta_versao_id, nota, opcao_codigo, opcao_rotulo, nao_sei_responder)
            VALUES (nextval('resposta_id_seq'), evaluation_id, question.id,
                CASE WHEN choice.nao_sei_responder THEN NULL ELSE choice.valor END,
                choice.codigo, choice.rotulo, choice.nao_sei_responder);
        END LOOP;
        SELECT count(*) INTO expected FROM pergunta_versao WHERE formulario_versao_id = version_id AND ativo;
        IF expected = 0 OR (SELECT count(*) FROM resposta WHERE avaliacao_id = evaluation_id) <> expected THEN
            RAISE EXCEPTION 'Avaliação de demonstração incompleta: %', demo_marker;
        END IF;
    END LOOP;
END $$;
COMMIT;

SELECT a.publico, count(DISTINCT a.id) AS avaliacoes_demo, count(r.id) AS respostas_demo
FROM avaliacao a JOIN validacao v ON v.id = a.validacao_id
JOIN resposta r ON r.avaliacao_id = a.id
WHERE v.old_token LIKE 'CPA_DEMO_RESULTADOS_20261019_%'
GROUP BY a.publico ORDER BY a.publico;
