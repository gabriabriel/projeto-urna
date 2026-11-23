package com.eleicao.votacao.api;

import com.eleicao.shared.domain.Cargo;
import java.util.List;

public interface ContagemVotosApi {

    List<ContagemCandidato> contarPorCargo(Long eleicaoId, Cargo cargo);
}
