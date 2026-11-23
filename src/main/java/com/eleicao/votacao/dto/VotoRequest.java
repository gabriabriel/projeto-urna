package com.eleicao.votacao.dto;

import com.eleicao.shared.domain.Cargo;
import jakarta.validation.constraints.NotNull;

/**
 * Escolha do eleitor para um cargo. Usada tanto no registro (prévia) quanto na confirmação.
 */
public record VotoRequest(
        @NotNull(message = "é obrigatório informar o cargo")
        Cargo cargo,

        @NotNull(message = "é obrigatório informar o candidato")
        Long candidatoId) {
}
