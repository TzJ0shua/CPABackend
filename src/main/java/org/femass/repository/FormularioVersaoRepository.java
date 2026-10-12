package org.femass.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.femass.entity.FormularioVersao;

@ApplicationScoped
public class FormularioVersaoRepository implements PanacheRepositoryBase<FormularioVersao, Long> {
    @jakarta.inject.Inject org.femass.service.DisponibilidadeCampanhaService disponibilidade;

    public FormularioVersao findByFormularioAndNumero(Long formularioId, Integer numero) {
        return find("formulario.id = ?1 and numero = ?2", formularioId, numero).firstResult();
    }

    public FormularioVersao findLatest(Long formularioId) {
        return find("formulario.id = ?1 order by numero desc", formularioId).firstResult();
    }
    public FormularioVersao findPublicada(String campanha, String codigo, org.femass.entity.PublicoAvaliacao publico, Integer numero) {
        if (!disponibilidade.disponivel(campanhaEntity(campanha))) return null;
        return find("formulario.campanha.codigo = ?1 and formulario.campanha.estado = 'ABERTA' and formulario.campanha.ativa = true and formulario.ativo = true and ativo = true and formulario.codigo = ?2 and publico = ?3 and numero = ?4 and estado = 'PUBLICADA'",
                campanha, codigo, publico, numero).firstResult();
    }

    public java.util.List<FormularioVersao> findCatalogo(String campanha, org.femass.entity.PublicoAvaliacao publico) {
        if (campanha != null && !campanha.isBlank() && !disponibilidade.disponivel(campanhaEntity(campanha))) return java.util.List.of();
        if (campanha == null || campanha.isBlank()) {
            return find("publico = ?1 and formulario.codigo <> 'legado' and estado = 'PUBLICADA' and formulario.campanha.estado = 'ABERTA' and formulario.campanha.ativa = true and formulario.ativo = true and ativo = true order by ordem, formulario.codigo, numero", publico).list().stream().filter(v -> disponibilidade.disponivel(v.getFormulario().getCampanha())).toList();
        }
        return find("formulario.campanha.codigo = ?1 and formulario.campanha.estado = 'ABERTA' and formulario.campanha.ativa = true and formulario.ativo = true and ativo = true and publico = ?2 and formulario.codigo <> 'legado' and estado = 'PUBLICADA' order by ordem, formulario.codigo, numero",
                campanha, publico).list();
    }
    private org.femass.entity.Campanha campanhaEntity(String codigo) {
        return getEntityManager().createQuery("from Campanha where codigo = :codigo", org.femass.entity.Campanha.class)
            .setParameter("codigo", codigo).getResultStream().findFirst().orElse(null);
    }
}
