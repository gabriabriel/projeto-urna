package com.eleicao.cadastro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Entrada da alteração de eleitor. A senha não muda por aqui. */
public record EleitorUpdateRequest(

        @NotBlank(message = "é obrigatório")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "é obrigatório")
        @Size(min = 3, max = 60, message = "deve ter entre 3 e 60 caracteres")
        String login,

        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "\\d{12}", message = "deve ter exatamente 12 dígitos")
        String tituloEleitor,

        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "deve ter exatamente 11 dígitos")
        String cpf) {
}
