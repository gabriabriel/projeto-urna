package com.eleicao.cadastro.service;

import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.repository.CandidatoRepository;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que o módulo cadastro oferece aos outros módulos (votação, apuração).
 * Os candidatos voltam com eleitor, usuário, partido e eleição já carregados,
 * então quem chama pode ler nome e sigla fora da transação.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidatoApiImpl implements CandidatoApi {

    private final CandidatoRepository candidatoRepository;

    @Override
    public Candidato buscar(Long candidatoId) {
        return candidatoRepository.findDetalhadoById(candidatoId)
                .orElseThrow(() -> NotFoundException.de("Candidato", candidatoId));
    }

    @Override
    public List<Candidato> listarPorEleicaoECargo(Long eleicaoId, Cargo cargo) {
        return candidatoRepository.findByEleicaoIdAndCargoOrderByNumeroAsc(eleicaoId, cargo);
    }

    @Override
    public Optional<Candidato> buscarPorEleitorEEleicao(Long eleitorId, Long eleicaoId) {
        return candidatoRepository.findByEleitorIdAndEleicaoId(eleitorId, eleicaoId);
    }
}
