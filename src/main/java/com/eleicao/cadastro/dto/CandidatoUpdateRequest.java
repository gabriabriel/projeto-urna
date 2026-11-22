package com.eleicao.cadastro.dto;

import com.eleicao.shared.domain.Cargo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada da alteração de candidato. Não se troca o eleitor nem a eleição:
 * se foi cadastrado errado, exclui a candidatura e cadastra de novo.
 */
public record CandidatoUpdateRequest(

        @NotNull(message = "é obrigatório")
        Long partidoId,

        @NotNull(message = "é obrigatório")
        Cargo cargo,

        @NotNull(message = "é obrigatório")
        @Min(value = 1, message = "deve ser no mínimo 1")
        @Max(value = 99999, message = "deve ser no máximo 99999")
        Integer numero) {
}
