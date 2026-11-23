package com.eleicao.votacao.service;

import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.votacao.api.ContagemCandidato;
import com.eleicao.votacao.api.ContagemVotosApi;
import com.eleicao.votacao.repository.VotoRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de ContagemVotosApi (o que a apuração usa). Devolve um item por candidato do cargo,
 * inclusive os que estão com zero votos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContagemVotosService implements ContagemVotosApi {

    private final VotoRepository votoRepository;
    private final CandidatoApi candidatoApi;

    @Override
    public List<ContagemCandidato> contarPorCargo(Long eleicaoId, Cargo cargo) {
        Map<Long, Long> votosPorCandidato = new HashMap<>();
        for (Object[] linha : votoRepository.contarPorCandidato(eleicaoId, cargo)) {
            votosPorCandidato.put((Long) linha[0], (Long) linha[1]);
        }
        return candidatoApi.listarPorEleicaoECargo(eleicaoId, cargo).stream()
                .map(candidato -> new ContagemCandidato(
                        candidato.getId(),
                        votosPorCandidato.getOrDefault(candidato.getId(), 0L)))
                .toList();
    }
}
