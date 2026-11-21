package com.eleicao.auth.exception;

/**
 * Login ou senha errados (ou usuário inativo). A mensagem é sempre a mesma, de propósito:
 * não contamos para quem está tentando adivinhar se o login existe ou se só a senha está errada.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Login ou senha inválidos");
    }
}
