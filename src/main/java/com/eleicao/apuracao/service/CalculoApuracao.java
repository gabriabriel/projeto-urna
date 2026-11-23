package com.eleicao.apuracao.service;

import com.eleicao.apuracao.dto.ResultadoCandidatoResponse;
import com.eleicao.cadastro.domain.Candidato;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Cálculo puro da apuração de um cargo (sem banco, sem Spring): percentual de cada candidato e
 * quem foi o mais votado. Fica isolado para ser testado e reaproveitado pela apuração parcial e
 * pela final.
 *
 * - percentual = votos / total do cargo x 100, 2 casas, arredondamento HALF_UP. Sem votos: 0.00.
 * - maisVotado = tem o maior número de votos do cargo E esse número é maior que zero.
 *   Mais de um maisVotado no mesmo cargo significa empate.
 */
final class CalculoApuracao {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private CalculoApuracao() {
    }

    /**
     * @param candidatos         candidatos do cargo (com eleitor, usuário e partido carregados)
     * @param votosPorCandidato  votos por id de candidato; quem não aparece tem zero votos
     */
    static List<ResultadoCandidatoResponse> calcular(List<Candidato> candidatos, Map<Long, Long> votosPorCandidato) {
        long total = candidatos.stream()
                .mapToLong(c -> votosPorCandidato.getOrDefault(c.getId(), 0L))
                .sum();
        long maior = candidatos.stream()
                .mapToLong(c -> votosPorCandidato.getOrDefault(c.getId(), 0L))
                .max()
                .orElse(0L);

        return candidatos.stream()
                .map(candidato -> {
                    long votos = votosPorCandidato.getOrDefault(candidato.getId(), 0L);
                    return ResultadoCandidatoResponse.of(
                            candidato, votos, percentual(votos, total), maior > 0 && votos == maior);
                })
                .toList();
    }

    static BigDecimal percentual(long votos, long total) {
        if (total == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(votos).multiply(CEM).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
