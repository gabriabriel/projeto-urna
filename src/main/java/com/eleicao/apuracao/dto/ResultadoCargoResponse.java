package com.eleicao.apuracao.dto;

import com.eleicao.apuracao.domain.SituacaoCargo;
import com.eleicao.shared.domain.Cargo;
import java.util.Comparator;
import java.util.List;

/**
 * Apuração de um cargo: candidatos do mais para o menos votado, total de votos e como ficou a
 * disputa. "vencedor" só vem preenchido quando situacao = VENCEDOR; em caso de EMPATE os empatados
 * estão em "empatados".
 */
public record ResultadoCargoResponse(
        Cargo cargo,
        long totalVotos,
        SituacaoCargo situacao,
        ResultadoCandidatoResponse vencedor,
        List<ResultadoCandidatoResponse> empatados,
        List<ResultadoCandidatoResponse> candidatos) {

    private static final Comparator<ResultadoCandidatoResponse> MAIS_VOTADO_PRIMEIRO =
            Comparator.comparingLong(ResultadoCandidatoResponse::votos).reversed()
                    .thenComparing(ResultadoCandidatoResponse::numero);

    /** Monta o resultado do cargo a partir das linhas já calculadas (votos, percentual e mais votado). */
    public static ResultadoCargoResponse de(Cargo cargo, List<ResultadoCandidatoResponse> linhas) {
        List<ResultadoCandidatoResponse> ordenados = linhas.stream().sorted(MAIS_VOTADO_PRIMEIRO).toList();
        long total = ordenados.stream().mapToLong(ResultadoCandidatoResponse::votos).sum();
        List<ResultadoCandidatoResponse> maisVotados = ordenados.stream()
                .filter(ResultadoCandidatoResponse::maisVotado)
                .toList();

        SituacaoCargo situacao;
        if (ordenados.isEmpty()) {
            situacao = SituacaoCargo.SEM_CANDIDATOS;
        } else if (total == 0) {
            situacao = SituacaoCargo.SEM_VOTOS;
        } else if (maisVotados.size() == 1) {
            situacao = SituacaoCargo.VENCEDOR;
        } else {
            situacao = SituacaoCargo.EMPATE;
        }

        return new ResultadoCargoResponse(
                cargo,
                total,
                situacao,
                situacao == SituacaoCargo.VENCEDOR ? maisVotados.get(0) : null,
                situacao == SituacaoCargo.EMPATE ? maisVotados : List.of(),
                ordenados);
    }
}
