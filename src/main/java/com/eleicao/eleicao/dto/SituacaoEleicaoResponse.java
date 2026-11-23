package com.eleicao.eleicao.dto;

import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.StatusEleicao;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Situação atual da eleição: status, quando abriu/encerrou e quantos candidatos há por cargo
 * (o Chefe precisa de pelo menos 2 candidatos para abrir a votação).
 */
public record SituacaoEleicaoResponse(
        Long eleicaoId,
        StatusEleicao status,
        LocalDateTime abertaEm,
        LocalDateTime encerradaEm,
        int totalCandidatos,
        Map<Cargo, Integer> candidatosPorCargo) {
}
