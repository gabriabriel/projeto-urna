package com.eleicao.votacao.service;

import com.eleicao.auth.api.UsuarioLogadoProvider;
import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.cadastro.api.EleitorApi;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.eleicao.api.EleicaoApi;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.votacao.domain.Participacao;
import com.eleicao.votacao.domain.Voto;
import com.eleicao.votacao.dto.CandidatoVotavelResponse;
import com.eleicao.votacao.dto.ComprovanteVotoResponse;
import com.eleicao.votacao.dto.ResumoVotoResponse;
import com.eleicao.votacao.dto.VotoRequest;
import com.eleicao.votacao.repository.ParticipacaoRepository;
import com.eleicao.votacao.repository.VotoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do Eleitor na votação.
 *
 * Fluxo em duas etapas:
 *  1. registrar: valida tudo e devolve um resumo. NÃO grava nada.
 *  2. confirmar: valida tudo de novo e grava participacao + voto na mesma transação.
 *
 * O sigilo vem do modelo: participacao não sabe em quem votou, e voto não sabe quem votou.
 */
@Service
@RequiredArgsConstructor
public class VotacaoService {

    private final EleicaoApi eleicaoApi;
    private final EleitorApi eleitorApi;
    private final CandidatoApi candidatoApi;
    private final UsuarioLogadoProvider usuarioLogadoProvider;
    private final ParticipacaoRepository participacaoRepository;
    private final VotoRepository votoRepository;

    /** Candidatos disponíveis para um cargo, na ordem do número. */
    @Transactional(readOnly = true)
    public List<CandidatoVotavelResponse> listarCandidatos(Long eleicaoId, Cargo cargo) {
        eleicaoApi.buscar(eleicaoId);
        return candidatoApi.listarPorEleicaoECargo(eleicaoId, cargo).stream()
                .map(CandidatoVotavelResponse::from)
                .toList();
    }

    /** Etapa 1: confere o voto e devolve o resumo. Não grava. */
    @Transactional(readOnly = true)
    public ResumoVotoResponse registrar(Long eleicaoId, VotoRequest request) {
        Eleitor eleitor = eleitorLogado();
        eleicaoApi.exigirAberta(eleicaoId);
        Candidato candidato = exigirCandidatura(eleicaoId, request);
        exigirQueNaoVotou(eleicaoId, eleitor, request.cargo());

        return new ResumoVotoResponse(eleicaoId, request.cargo(), CandidatoVotavelResponse.from(candidato));
    }

    /** Etapa 2: confere de novo e grava o voto. Depois disso não há como alterá-lo. */
    @Transactional
    public ComprovanteVotoResponse confirmar(Long eleicaoId, VotoRequest request) {
        Eleitor eleitor = eleitorLogado();
        Eleicao eleicao = eleicaoApi.exigirAberta(eleicaoId);
        Candidato candidato = exigirCandidatura(eleicaoId, request);
        exigirQueNaoVotou(eleicaoId, eleitor, request.cargo());

        // saveAndFlush: se outra requisição ganhou a corrida, a UNIQUE do banco estoura já aqui
        // (vira 409 no GlobalExceptionHandler) e nada é gravado.
        Participacao participacao = participacaoRepository.saveAndFlush(Participacao.builder()
                .eleicao(eleicao)
                .eleitor(eleitor)
                .cargo(request.cargo())
                .build());

        votoRepository.save(Voto.builder()
                .candidato(candidato)
                .build());

        return new ComprovanteVotoResponse(eleicaoId, request.cargo(), participacao.getConfirmadoEm());
    }

    // ------------------------------------------------------------------ privados

    private Eleitor eleitorLogado() {
        return eleitorApi.buscarPorUsuario(usuarioLogadoProvider.atual().usuarioId());
    }

    /** O candidato precisa existir, concorrer nesta eleição e ao cargo escolhido. */
    private Candidato exigirCandidatura(Long eleicaoId, VotoRequest request) {
        Candidato candidato = candidatoApi.buscar(request.candidatoId());
        if (!candidato.getEleicao().getId().equals(eleicaoId)) {
            throw new BusinessRuleException("O candidato não concorre nesta eleição");
        }
        if (candidato.getCargo() != request.cargo()) {
            throw new BusinessRuleException("O candidato não concorre ao cargo " + request.cargo());
        }
        return candidato;
    }

    /** Um eleitor vota uma única vez por cargo. A UNIQUE do banco é a trava final. */
    private void exigirQueNaoVotou(Long eleicaoId, Eleitor eleitor, Cargo cargo) {
        if (participacaoRepository.existsByEleicaoIdAndEleitorIdAndCargo(eleicaoId, eleitor.getId(), cargo)) {
            throw new ConflictException("Você já votou para o cargo " + cargo + " nesta eleição");
        }
    }
}
