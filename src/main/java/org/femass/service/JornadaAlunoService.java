package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.femass.dto.ProgressoAlunoDTO;
import org.femass.dto.QRCodeResponseDTO;
import org.femass.entity.*;
import org.femass.repository.FormularioVersaoRepository;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JornadaAlunoService {
    @Inject EntityManager em;
    @Inject DisponibilidadeCampanhaService disponibilidade;
    @Inject IdentidadeAlunoService identidade;
    @Inject FormularioVersaoRepository versoes;
    @Inject ValidacaoService validacoes;

    @Transactional
    public ProgressoAlunoDTO consultar(ProgressoAlunoDTO.Consulta consulta) {
        if (consulta == null || consulta.campaign() == null || consulta.campaign().isBlank()
                || !Boolean.TRUE.equals(consulta.aceiteTermosCondicoesServico()))
            throw new IllegalArgumentException("Campanha e aceite dos termos são obrigatórios.");
        var hashes = identidade.calcular(consulta.cpf(), consulta.matricula(), consulta.campaign());
        // Serialize receipt creation/recovery. Campaign read lock also coordinates deletion.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
            .setParameter("key", "cpa:jornada-aluno:" + consulta.campaign()).getSingleResult();
        disponibilidade.exigirDisponivel(consulta.campaign());
        var catalogo = versoes.findCatalogo(consulta.campaign(), PublicoAvaliacao.ALUNO);
        List<ProgressoAlunoDTO.Formulario> itens = new ArrayList<>();
        List<Validacao> participacoes = new ArrayList<>();
        for (var versao : catalogo) {
            var lista = em.createQuery("from Validacao where formularioVersao.id = :id and (cpfHash = :cpf or matriculaHash = :matricula)", Validacao.class)
                .setParameter("id", versao.getId()).setParameter("cpf", hashes.cpf()).setParameter("matricula", hashes.matricula()).getResultList();
            // Both identifiers must match to recover a receipt, never merge different students.
            if (lista.stream().anyMatch(v -> !hashes.cpf().equals(v.getCpfHash()) || !hashes.matricula().equals(v.getMatriculaHash())))
                throw new IllegalArgumentException("CPF e matrícula não correspondem à identificação da participação anterior.");
            itens.add(new ProgressoAlunoDTO.Formulario(versao.getFormulario().getCodigo(), versao.getNumero(), !lista.isEmpty()));
            participacoes.addAll(lista);
        }
        if (catalogo.isEmpty() || itens.stream().anyMatch(i -> !i.respondido()))
            return new ProgressoAlunoDTO(itens, false, null);

        var campanha = catalogo.get(0).getFormulario().getCampanha();
        var existentes = em.createQuery("from JornadaAluno where campanha.id = :id and (cpfHash = :cpf or matriculaHash = :matricula)", JornadaAluno.class)
            .setParameter("id", campanha.getId()).setParameter("cpf", hashes.cpf()).setParameter("matricula", hashes.matricula()).getResultList();
        JornadaAluno jornada;
        if (existentes.isEmpty()) {
            jornada = new JornadaAluno(); jornada.campanha = campanha;
            jornada.cpfHash = hashes.cpf(); jornada.matriculaHash = hashes.matricula();
            em.persist(jornada); em.flush();
        } else {
            jornada = existentes.get(0);
            if (!hashes.cpf().equals(jornada.cpfHash) || !hashes.matricula().equals(jornada.matriculaHash))
                throw new IllegalArgumentException("CPF e matrícula não correspondem à identificação do comprovante.");
        }
        String codigo = identidade.codigoJornada(jornada.id, jornada.cpfHash, jornada.matriculaHash);
        if (jornada.comprovante == null) jornada.comprovante = validacoes.armazenarCodigoValidacao(codigo, true);
        for (var participacao : participacoes)
            if (jornada.participacoes.stream().noneMatch(v -> v.getId().equals(participacao.getId()))) jornada.participacoes.add(participacao);
        em.flush();
        String digest = jornada.comprovante.getCodigoDigest();
        return new ProgressoAlunoDTO(itens, true, new QRCodeResponseDTO(codigo, codigo, digest.substring(digest.length() - 10)));
    }
}
