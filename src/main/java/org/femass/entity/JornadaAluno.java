package org.femass.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jornada_aluno")
public class JornadaAluno {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "campanha_id", nullable = false)
    public Campanha campanha;
    @Column(name = "cpf_hash", nullable = false, length = 64)
    public String cpfHash;
    @Column(name = "matricula_hash", nullable = false, length = 64)
    public String matriculaHash;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "comprovante_id")
    public Validacao comprovante;
    @ManyToMany
    @JoinTable(name = "jornada_aluno_validacao", joinColumns = @JoinColumn(name = "jornada_id"),
        inverseJoinColumns = @JoinColumn(name = "validacao_id"))
    public List<Validacao> participacoes = new ArrayList<>();
}
