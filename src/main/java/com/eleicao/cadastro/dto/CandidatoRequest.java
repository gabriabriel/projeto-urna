package com.eleicao.cadastro.dto;

import com.eleicao.shared.domain.Cargo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Entrada do cadastro de candidato: um eleitor concorrendo a um cargo em uma eleição. */
public record CandidatoRequest(

        @NotNull(message = "é obrigatório")
        Long eleitorId,

        @NotNull(message = "é obrigatória")
        Long eleicaoId,

        @NotNull(message = "é obrigatório")
        Long partidoId,

        @NotNull(message = "é obrigatório")
        Cargo cargo,

        // Mesma faixa do CHECK do banco (ck_candidato_numero)
        @NotNull(message = "é obrigatório")
        @Min(value = 1, message = "deve ser no mínimo 1")
        @Max(value = 99999, message = "deve ser no máximo 99999")
        Integer numero) {
}
