package com.eleicao.cadastro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Entrada do cadastro de eleitor. Cria o eleitor e também o login dele. */
public record EleitorRequest(

        @NotBlank(message = "é obrigatório")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "é obrigatório")
        @Size(min = 3, max = 60, message = "deve ter entre 3 e 60 caracteres")
        String login,

        // O BCrypt só considera os primeiros 72 bytes da senha, por isso o limite.
        @NotBlank(message = "é obrigatória")
        @Size(min = 6, max = 72, message = "deve ter entre 6 e 72 caracteres")
        String senha,

        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "\\d{12}", message = "deve ter exatamente 12 dígitos")
        String tituloEleitor,

        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "deve ter exatamente 11 dígitos")
        String cpf) {
}
