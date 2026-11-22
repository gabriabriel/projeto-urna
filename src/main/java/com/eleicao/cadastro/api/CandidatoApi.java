package com.eleicao.cadastro.api;

import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.shared.domain.Cargo;
import java.util.List;
import java.util.Optional;

public interface CandidatoApi {

    Candidato buscar(Long candidatoId);

    List<Candidato> listarPorEleicaoECargo(Long eleicaoId, Cargo cargo);

    Optional<Candidato> buscarPorEleitorEEleicao(Long eleitorId, Long eleicaoId);
}