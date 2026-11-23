package com.eleicao.apuracao.dto;

import com.eleicao.apuracao.domain.TipoApuracao;
import com.eleicao.shared.domain.StatusEleicao;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resposta da apuração (parcial ou final).
 * - PARCIAL: calculada no momento da consulta (apuradaEm = agora), eleição ABERTA.
 * - FINAL: gravada no encerramento (apuradaEm = quando foi gravada), eleição ENCERRADA.
 * totalVotos soma os votos dos cargos apresentados.
 */
public record ApuracaoResponse(
        Long eleicaoId,
        StatusEleicao statusEleicao,
        TipoApuracao tipo,
        LocalDateTime apuradaEm,
        long totalVotos,
        List<ResultadoCargoResponse> cargos) {
}
