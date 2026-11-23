package com.eleicao.eleicao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Entrada do cadastro e da alteração de eleição (os campos são os mesmos nos dois casos).
 * A regra "encerramento depois da abertura" depende de dois campos juntos, por isso fica no
 * EleicaoService (422) e não aqui.
 */
public record EleicaoRequest(

        @NotNull(message = "é obrigatória")
        @FutureOrPresent(message = "não pode ser uma data passada")
        @Schema(example = "2026-12-06")
        LocalDate data,

        // Mesma faixa do CHECK do banco (ck_eleicao_turno)
        @NotNull(message = "é obrigatório")
        @Min(value = 1, message = "deve ser 1 ou 2")
        @Max(value = 2, message = "deve ser 1 ou 2")
        @Schema(example = "1")
        Integer turno,

        @NotNull(message = "é obrigatório")
        @Schema(type = "string", format = "time", example = "08:00:00")
        LocalTime horarioAbertura,

        @NotNull(message = "é obrigatório")
        @Schema(type = "string", format = "time", example = "17:00:00")
        LocalTime horarioEncerramento) {
}
