package com.eleicao.shared.exception;

/**
 * O usuário está autenticado e tem o perfil certo para a rota, mas uma regra que depende dos
 * DADOS impede o acesso (ex.: candidato tentando acompanhar outro cargo). Vira 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String mensagem) {
        super(mensagem);
    }
}
