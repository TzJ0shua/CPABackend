package org.femass.service;
public class IdentificacaoIndisponivelException extends RuntimeException {
    public IdentificacaoIndisponivelException() { super("Identificação de alunos indisponível no momento"); }
}
