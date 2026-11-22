package com.eleicao.cadastro.dto;

import com.eleicao.cadastro.domain.Eleitor;

/** Saída do eleitor. Nunca inclui senha nem hash. */
public record EleitorResponse(Long id, String nome, String login, String tituloEleitor, String cpf) {

    /** Precisa ser chamado com o usuário do eleitor já carregado (dentro da transação). */
    public static EleitorResponse from(Eleitor eleitor) {
        return new EleitorResponse(
                eleitor.getId(),
                eleitor.getUsuario().getNome(),
                eleitor.getUsuario().getLogin(),
                eleitor.getTituloEleitor(),
                eleitor.getCpf());
    }
}
