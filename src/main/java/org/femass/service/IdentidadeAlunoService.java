package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.femass.util.ValidacaoCPFUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class IdentidadeAlunoService {
    @ConfigProperty(name = "validacao.aluno-hash-seed") Optional<String> seed;
    public record Hashes(String cpf, String matricula) { }

    public Hashes calcular(String cpf, String matricula, String campanha) {
        if (cpf == null || !cpf.matches("(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})")
                || !ValidacaoCPFUtil.validarCPF(cpf)) throw new IllegalArgumentException("CPF inválido");
        if (matricula == null || !matricula.matches("[0-9]{10}"))
            throw new IllegalArgumentException("Matrícula deve ter 10 dígitos");
        if (seed.isEmpty() || seed.get().isBlank() || seed.get().getBytes(StandardCharsets.UTF_8).length < 32
                || "not-configured".equals(seed.get())) throw new IdentificacaoIndisponivelException();
        return new Hashes(hash("cpf:" + campanha + ":" + cpf.replaceAll("\\D", "")),
            hash("matricula:" + campanha + ":" + matricula));
    }
    private String hash(String entrada) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(seed.orElseThrow().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(entrada.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IdentificacaoIndisponivelException(); }
    }
}
