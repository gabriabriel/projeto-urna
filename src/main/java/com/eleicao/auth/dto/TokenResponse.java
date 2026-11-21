package com.eleicao.auth.dto;

import com.eleicao.shared.domain.Perfil;

/** Resposta do login. O cliente manda o token de volta em "Authorization: Bearer <token>". */
public record TokenResponse(
        String token,
        String tipo,
        long expiraEmSegundos,
        Long usuarioId,
        String nome,
        Perfil perfil) {
}
