package org.femass.service;
public class FormularioJaRespondidoException extends RuntimeException {
    public FormularioJaRespondidoException() { super("Você já respondeu esta versão do formulário nesta campanha."); }
    public static boolean causadaPorUnicidade(Throwable erro) {
        for (Throwable cause = erro; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && ("uk_validacao_aluno_cpf_versao".equals(constraint.getConstraintName())
                    || "uk_validacao_aluno_matricula_versao".equals(constraint.getConstraintName()))) return true;
        }
        return false;
    }
}
