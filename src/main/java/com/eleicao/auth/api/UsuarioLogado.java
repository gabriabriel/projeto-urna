package com.eleicao.auth.api;

import com.eleicao.shared.domain.Perfil;

public record UsuarioLogado(Long usuarioId, Perfil perfil) {
}
