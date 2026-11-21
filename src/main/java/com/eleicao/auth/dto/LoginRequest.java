package com.eleicao.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credenciais do login. Os limites são os mesmos do cadastro (o BCrypt só usa 72 bytes da senha). */
public record LoginRequest(

        @NotBlank(message = "é obrigatório")
        @Size(max = 60, message = "deve ter no máximo 60 caracteres")
        String login,

        @NotBlank(message = "é obrigatória")
        @Size(max = 72, message = "deve ter no máximo 72 caracteres")
        String senha) {
}
