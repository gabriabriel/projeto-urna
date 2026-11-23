package com.eleicao.votacao.dto;

import com.eleicao.cadastro.domain.Candidato;

/**
 * Candidato que aparece na cédula. Não traz CPF nem título, e não traz o id do eleitor.
 */
public record CandidatoVotavelResponse(
        Long id,
        Integer numero,
        String nome,
        String partidoSigla,
        Integer partidoNumero) {

    /** Precisa ser chamado com eleitor, usuário e partido já carregados (dentro da transação). */
    public static CandidatoVotavelResponse from(Candidato candidato) {
        return new CandidatoVotavelResponse(
                candidato.getId(),
                candidato.getNumero(),
                candidato.getEleitor().getUsuario().getNome(),
                candidato.getPartido().getSigla(),
                candidato.getPartido().getNumero());
    }
}
