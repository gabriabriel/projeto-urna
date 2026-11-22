package com.eleicao.cadastro.service;

import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.domain.Partido;
import com.eleicao.cadastro.dto.CandidatoRequest;
import com.eleicao.cadastro.dto.CandidatoResponse;
import com.eleicao.cadastro.dto.CandidatoUpdateRequest;
import com.eleicao.cadastro.repository.CandidatoRepository;
import com.eleicao.cadastro.repository.EleitorRepository;
import com.eleicao.cadastro.repository.PartidoRepository;
import com.eleicao.eleicao.api.EleicaoApi;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.shared.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do Chefe da Seção sobre candidaturas. O candidato é um eleitor que concorre a um
 * cargo em uma eleição. A eleição pertence ao módulo eleicao e só é acessada pela interface EleicaoApi.
 */
@Service
@RequiredArgsConstructor
public class CandidatoService {

    private final CandidatoRepository candidatoRepository;
    private final EleitorRepository eleitorRepository;
    private final PartidoRepository partidoRepository;
    private final EleicaoApi eleicaoApi;

    @Transactional
    public CandidatoResponse criar(CandidatoRequest request) {
        // Só se mexe nas candidaturas antes de a votação abrir.
        // (EleicaoApi: NotFoundException se não existe, BusinessRuleException se não está AGENDADA)
        Eleicao eleicao = eleicaoApi.exigirAgendada(request.eleicaoId());

        Eleitor eleitor = eleitorRepository.findById(request.eleitorId())
                .orElseThrow(() -> NotFoundException.de("Eleitor", request.eleitorId()));
        Partido partido = buscarPartido(request.partidoId());
        exigirNumeroDoPartido(partido, request.numero());

        if (candidatoRepository.existsByEleitorIdAndEleicaoId(eleitor.getId(), eleicao.getId())) {
            throw new ConflictException("Este eleitor já é candidato nesta eleição");
        }
        exigirNumeroLivre(eleicao.getId(), request.cargo(), request.numero(), null);

        Candidato candidato = candidatoRepository.save(Candidato.builder()
                .eleitor(eleitor)
                .eleicao(eleicao)
                .partido(partido)
                .cargo(request.cargo())
                .numero(request.numero())
                .build());

        return CandidatoResponse.from(candidato);
    }

    /** Filtros opcionais: sem nenhum, lista todos. */
    @Transactional(readOnly = true)
    public List<CandidatoResponse> listar(Long eleicaoId, Cargo cargo) {
        List<Candidato> candidatos;
        if (eleicaoId != null && cargo != null) {
            candidatos = candidatoRepository.findByEleicaoIdAndCargoOrderByNumeroAsc(eleicaoId, cargo);
        } else if (eleicaoId != null) {
            candidatos = candidatoRepository.findByEleicaoIdOrderByCargoAscNumeroAsc(eleicaoId);
        } else if (cargo != null) {
            candidatos = candidatoRepository.findByCargoOrderByEleicaoIdAscNumeroAsc(cargo);
        } else {
            candidatos = candidatoRepository.findAllByOrderByEleicaoIdAscCargoAscNumeroAsc();
        }
        return candidatos.stream().map(CandidatoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CandidatoResponse buscar(Long id) {
        return CandidatoResponse.from(buscarEntidade(id));
    }

    @Transactional
    public CandidatoResponse atualizar(Long id, CandidatoUpdateRequest request) {
        Candidato candidato = buscarEntidade(id);
        Long eleicaoId = candidato.getEleicao().getId();
        eleicaoApi.exigirAgendada(eleicaoId);

        Partido partido = buscarPartido(request.partidoId());
        exigirNumeroDoPartido(partido, request.numero());
        exigirNumeroLivre(eleicaoId, request.cargo(), request.numero(), id);

        candidato.setPartido(partido);
        candidato.setCargo(request.cargo());
        candidato.setNumero(request.numero());

        return CandidatoResponse.from(candidato);
    }

    @Transactional
    public void excluir(Long id) {
        Candidato candidato = buscarEntidade(id);

        // Como só se exclui com a eleição AGENDADA, o candidato ainda não pode ter recebido voto
        // (a eleição nunca volta para AGENDADA). Por isso não precisamos checar votos.
        eleicaoApi.exigirAgendada(candidato.getEleicao().getId());

        candidatoRepository.delete(candidato);
    }

    // ------------------------------------------------------------------ privados

    private Candidato buscarEntidade(Long id) {
        return candidatoRepository.findDetalhadoById(id)
                .orElseThrow(() -> NotFoundException.de("Candidato", id));
    }

    private Partido buscarPartido(Long partidoId) {
        return partidoRepository.findById(partidoId)
                .orElseThrow(() -> NotFoundException.de("Partido", partidoId));
    }

    /**
     * O número do candidato começa com o número do partido (ex.: PT = 13 → 13, 131, 13456),
     * como na urna real. Regra de negócio, por isso fica aqui e não no DTO (que não conhece o partido).
     */
    private void exigirNumeroDoPartido(Partido partido, Integer numero) {
        if (!String.valueOf(numero).startsWith(String.valueOf(partido.getNumero()))) {
            throw new BusinessRuleException(
                    "O número do candidato deve começar com o número do partido (" + partido.getNumero() + ")");
        }
    }

    /**
     * Número único por cargo dentro da eleição. idAtual é o candidato que está sendo alterado
     * (ignorado na checagem) ou null ao criar. A UNIQUE do banco continua sendo a trava final
     * caso duas requisições simultâneas passem por esta checagem.
     */
    private void exigirNumeroLivre(Long eleicaoId, Cargo cargo, Integer numero, Long idAtual) {
        candidatoRepository.findByEleicaoIdAndCargoAndNumero(eleicaoId, cargo, numero)
                .filter(outro -> !outro.getId().equals(idAtual))
                .ifPresent(outro -> {
                    throw new ConflictException(
                            "O número " + numero + " já está em uso para o cargo " + cargo + " nesta eleição");
                });
    }
}
