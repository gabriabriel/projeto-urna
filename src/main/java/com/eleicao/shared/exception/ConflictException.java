package com.eleicao.shared.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String mensagem) {
        super(mensagem);
    }
}
