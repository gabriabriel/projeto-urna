package com.eleicao.apuracao.dto;

import com.eleicao.cadastro.domain.Candidato;
import java.math.BigDecimal;

/**
 * Votos de um candidato na apuração. Não traz CPF, título nem id do eleitor.
 * percentual = votos do candidato / votos do cargo x 100, com 2 casas decimais.
 */
public record ResultadoCandidatoResponse(
        Long candidatoId,
        Integer numero,
        String nome,
        String partidoSigla,
        Integer partidoNumero,
        long votos,
        BigDecimal percentual,
        boolean maisVotado) {

    /** Precisa ser chamado com eleitor, usuário e partido já carregados (a CandidatoApi já devolve assim). */
    public static ResultadoCandidatoResponse of(Candidato candidato, long votos, BigDecimal percentual,
                                                boolean maisVotado) {
        return new ResultadoCandidatoResponse(
                candidato.getId(),
                candidato.getNumero(),
                candidato.getEleitor().getUsuario().getNome(),
                candidato.getPartido().getSigla(),
                candidato.getPartido().getNumero(),
                votos,
                percentual,
                maisVotado);
    }
}
