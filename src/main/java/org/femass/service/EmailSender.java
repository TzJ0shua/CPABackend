package org.femass.service;

public interface EmailSender {
    boolean configurado();
    void enviarPin(String destinatario, String pin);
}
