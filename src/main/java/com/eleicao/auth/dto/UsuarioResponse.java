package com.eleicao.auth.dto;

import com.eleicao.auth.domain.Usuario;
import com.eleicao.shared.domain.Perfil;

/** Dados do usuário logado. Nunca inclui a senha nem o hash. */
public record UsuarioResponse(Long id, String nome, String login, Perfil perfil) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getLogin(), usuario.getPerfil());
    }
}
