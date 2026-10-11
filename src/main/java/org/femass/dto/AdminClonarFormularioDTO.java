package org.femass.dto;

public record AdminClonarFormularioDTO(String campanhaOrigem, String formularioOrigem,
                                      Integer versaoOrigem, String codigo, String nome) {}
