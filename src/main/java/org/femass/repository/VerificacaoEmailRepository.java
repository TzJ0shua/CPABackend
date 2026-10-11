package org.femass.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import org.femass.entity.PublicoAvaliacao;
import org.femass.entity.StatusVerificacaoEmail;
import org.femass.entity.VerificacaoEmail;

@ApplicationScoped
public class VerificacaoEmailRepository implements PanacheRepositoryBase<VerificacaoEmail, Long> {

    public VerificacaoEmail findPendenteForUpdate(String emailDigest, PublicoAvaliacao publico,
                                                   String campanha, String formulario, Integer versao) {
        return find("emailDigest = ?1 and publico = ?2 and campanha = ?3 and formulario = ?4 and versaoFormulario = ?5 and status = ?6 order by id desc",
                emailDigest, publico, campanha, formulario, versao, StatusVerificacaoEmail.PENDENTE)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
    }

    public VerificacaoEmail findByIdForUpdate(Long id) {
        return findById(id, LockModeType.PESSIMISTIC_WRITE);
    }

    public VerificacaoEmail findByAutorizacaoDigestForUpdate(String digest) {
        return find("autorizacaoDigest", digest)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
    }
}
