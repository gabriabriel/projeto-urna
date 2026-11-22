package com.eleicao.cadastro.dto;

import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.shared.domain.Cargo;

/**
 * Saída do candidato. Qualquer usuário logado pode consultar, então NÃO inclui CPF nem título.
 */
public record CandidatoResponse(
        Long id,
        Long eleitorId,
        String nome,
        String partidoSigla,
        Integer partidoNumero,
        Cargo cargo,
        Integer numero,
        Long eleicaoId) {

    /** Precisa ser chamado com eleitor, usuário e partido já carregados (dentro da transação). */
    public static CandidatoResponse from(Candidato candidato) {
        return new CandidatoResponse(
                candidato.getId(),
                candidato.getEleitor().getId(),
                candidato.getEleitor().getUsuario().getNome(),
                candidato.getPartido().getSigla(),
                candidato.getPartido().getNumero(),
                candidato.getCargo(),
                candidato.getNumero(),
                candidato.getEleicao().getId());
    }
}
