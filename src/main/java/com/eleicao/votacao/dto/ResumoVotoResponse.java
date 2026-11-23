package com.eleicao.votacao.dto;

import com.eleicao.shared.domain.Cargo;

/**
 * Resposta do registro: mostra o que será confirmado. Nada foi gravado ainda.
 */
public record ResumoVotoResponse(
        Long eleicaoId,
        Cargo cargo,
        CandidatoVotavelResponse candidato) {
}
