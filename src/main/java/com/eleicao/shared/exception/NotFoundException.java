package com.eleicao.shared.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String mensagem) {
        super(mensagem);
    }

    public static NotFoundException de(String recurso, Object id) {
        return new NotFoundException(recurso + " não encontrado(a): " + id);
    }
}
