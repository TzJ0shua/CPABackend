package org.femass.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import org.femass.entity.Validacao;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ValidacaoRepository implements PanacheRepositoryBase<Validacao, Long> {

    public boolean existsByStudentIdentity(Long versionId, String cpfHash, String matriculaHash) {
        return count("formularioVersao.id = ?1 and (cpfHash = ?2 or matriculaHash = ?3)",
                versionId, cpfHash, matriculaHash) > 0;
    }

    public Validacao findByCodigoDigest(UUID digest) {
        return find("newHash", digest).firstResult();
    }

    public Validacao findByCodigoDigestForUpdate(UUID digest) {
        return find("newHash", digest)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
    }

    public List<Validacao> findLastValidated(int limit) {
        return find("validado = true and newHash is not null and dataValidacao is not null order by dataValidacao desc")
                .range(0, Math.max(0, limit - 1))
                .list();
    }
}
