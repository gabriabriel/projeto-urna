package com.eleicao.apuracao.service;

import com.eleicao.apuracao.domain.Apuracao;
import com.eleicao.apuracao.domain.ResultadoCandidato;
import com.eleicao.apuracao.domain.TipoApuracao;
import com.eleicao.apuracao.dto.ApuracaoResponse;
import com.eleicao.apuracao.dto.ResultadoCandidatoResponse;
import com.eleicao.apuracao.dto.ResultadoCargoResponse;
import com.eleicao.apuracao.repository.ApuracaoRepository;
import com.eleicao.apuracao.repository.ResultadoCandidatoRepository;
import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.auth.api.UsuarioLogadoProvider;
import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.cadastro.api.EleitorApi;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.eleicao.api.EleicaoApi;
import com.eleicao.eleicao.api.EleicaoEncerradaEvent;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.domain.StatusEleicao;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ForbiddenException;
import com.eleicao.votacao.api.ContagemCandidato;
import com.eleicao.votacao.api.ContagemVotosApi;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acompanhamento da votação (apuração parcial) e resultado final.
 *
 * - Parcial: só com a eleição ABERTA. Calculada na hora a partir dos votos; NÃO grava nada.
 *   O eleitor acompanha todos os cargos; o eleitor que é candidato nesta eleição acompanha só o
 *   cargo da candidatura dele (regra de dados, por isso fica aqui e não em RegrasDeAcesso).
 * - Final: só com a eleição ENCERRADA. É gravada (Apuracao + ResultadoCandidato) uma única vez,
 *   no encerramento (EleicaoEncerradaEvent), e as consultas seguintes leem o que foi gravado.
 *
 * Os outros módulos só são acessados pelas interfaces api/ deles: EleicaoApi, CandidatoApi,
 * EleitorApi, ContagemVotosApi (votação) e UsuarioLogadoProvider (auth).
 */
@Service
@RequiredArgsConstructor
public class ApuracaoService {

    private final EleicaoApi eleicaoApi;
    private final CandidatoApi candidatoApi;
    private final EleitorApi eleitorApi;
    private final ContagemVotosApi contagemVotosApi;
    private final UsuarioLogadoProvider usuarioLogadoProvider;
    private final ApuracaoRepository apuracaoRepository;
    private final ResultadoCandidatoRepository resultadoCandidatoRepository;

    /** Apuração parcial (acompanhamento). cargo é opcional: sem ele, todos os cargos visíveis. */
    @Transactional(readOnly = true)
    public ApuracaoResponse parcial(Long eleicaoId, Cargo cargo) {
        Eleicao eleicao = eleicaoApi.exigirAberta(eleicaoId);
        List<Cargo> cargos = cargosVisiveis(eleicaoId, cargo);

        List<ResultadoCargoResponse> resultados = new ArrayList<>();
        for (Cargo c : cargos) {
            List<Candidato> candidatos = candidatoApi.listarPorEleicaoECargo(eleicaoId, c);
            if (candidatos.isEmpty() && cargos.size() > 1) {
                continue; // na visão geral, cargos sem candidatos não aparecem
            }
            resultados.add(ResultadoCargoResponse.de(c,
                    CalculoApuracao.calcular(candidatos, votosPorCandidato(eleicaoId, c))));
        }

        return resposta(eleicao, TipoApuracao.PARCIAL, LocalDateTime.now(), resultados);
    }

    /**
     * Resultado final. cargo é opcional: sem ele, todos os cargos com candidatos.
     * Disponível para qualquer usuário logado depois do encerramento.
     */
    @Transactional
    public ApuracaoResponse resultadoFinal(Long eleicaoId, Cargo cargo) {
        Eleicao eleicao = eleicaoApi.buscar(eleicaoId);
        if (eleicao.getStatus() != StatusEleicao.ENCERRADA) {
            throw new BusinessRuleException("O resultado final só fica disponível depois que a votação "
                    + "é encerrada (a eleição está " + eleicao.getStatus() + ")");
        }

        // Normalmente já foi gravado no encerramento. Se não foi (ex.: eleição encerrada direto no
        // banco), grava agora: o resultado de uma eleição encerrada não muda mais.
        Apuracao apuracao = apuracaoRepository.findByEleicaoId(eleicaoId)
                .orElseGet(() -> gravarResultadoFinal(eleicao));

        Map<Cargo, List<ResultadoCandidato>> linhasPorCargo = resultadoCandidatoRepository
                .findByApuracaoId(apuracao.getId()).stream()
                .collect(Collectors.groupingBy(ResultadoCandidato::getCargo));

        List<Cargo> cargos = cargo == null ? List.of(Cargo.values()) : List.of(cargo);
        List<ResultadoCargoResponse> resultados = new ArrayList<>();
        for (Cargo c : cargos) {
            List<ResultadoCandidato> linhas = linhasPorCargo.getOrDefault(c, List.of());
            if (linhas.isEmpty() && cargo == null) {
                continue;
            }
            Map<Long, Candidato> candidatos = candidatoApi.listarPorEleicaoECargo(eleicaoId, c).stream()
                    .collect(Collectors.toMap(Candidato::getId, Function.identity()));
            List<ResultadoCandidatoResponse> respostas = linhas.stream()
                    .map(linha -> ResultadoCandidatoResponse.of(
                            candidatos.get(linha.getCandidato().getId()),
                            linha.getVotos(), linha.getPercentual(), linha.isMaisVotado()))
                    .toList();
            resultados.add(ResultadoCargoResponse.de(c, respostas));
        }

        return resposta(eleicao, TipoApuracao.FINAL, apuracao.getApuradaEm(), resultados);
    }

    /**
     * Chamado pelo módulo eleicao ao encerrar a votação (mesma transação do encerramento).
     * Idempotente: se o resultado já existe, não grava de novo.
     */
    @EventListener
    @Transactional
    public void aoEncerrarEleicao(EleicaoEncerradaEvent evento) {
        if (!apuracaoRepository.existsByEleicaoId(evento.eleicaoId())) {
            gravarResultadoFinal(eleicaoApi.buscar(evento.eleicaoId()));
        }
    }

    // ------------------------------------------------------------------ privados

    /** Calcula todos os cargos com candidatos e grava Apuracao + ResultadoCandidato. */
    private Apuracao gravarResultadoFinal(Eleicao eleicao) {
        Long eleicaoId = eleicao.getId();
        List<ResultadoCandidatoPendente> pendentes = new ArrayList<>();
        long totalVotos = 0;

        for (Cargo cargo : Cargo.values()) {
            List<Candidato> candidatos = candidatoApi.listarPorEleicaoECargo(eleicaoId, cargo);
            if (candidatos.isEmpty()) {
                continue;
            }
            Map<Long, Candidato> porId = candidatos.stream()
                    .collect(Collectors.toMap(Candidato::getId, Function.identity()));
            for (ResultadoCandidatoResponse linha : CalculoApuracao.calcular(candidatos,
                    votosPorCandidato(eleicaoId, cargo))) {
                pendentes.add(new ResultadoCandidatoPendente(porId.get(linha.candidatoId()), cargo, linha));
                totalVotos += linha.votos();
            }
        }

        Apuracao apuracao = apuracaoRepository.save(Apuracao.builder()
                .eleicao(eleicao)
                .totalVotos(totalVotos)
                .build());

        resultadoCandidatoRepository.saveAll(pendentes.stream()
                .map(p -> ResultadoCandidato.builder()
                        .apuracao(apuracao)
                        .candidato(p.candidato())
                        .cargo(p.cargo())
                        .votos(p.linha().votos())
                        .percentual(p.linha().percentual())
                        .maisVotado(p.linha().maisVotado())
                        .build())
                .toList());

        return apuracao;
    }

    /**
     * Cargos que o usuário logado pode acompanhar na eleição. O eleitor que é candidato nesta
     * eleição só acompanha o cargo da própria candidatura; pedir outro cargo dá 403.
     */
    private List<Cargo> cargosVisiveis(Long eleicaoId, Cargo filtro) {
        UsuarioLogado usuario = usuarioLogadoProvider.atual();
        if (usuario.perfil() == Perfil.ELEITOR) {
            Eleitor eleitor = eleitorApi.buscarPorUsuario(usuario.usuarioId());
            Optional<Candidato> candidatura = candidatoApi.buscarPorEleitorEEleicao(eleitor.getId(), eleicaoId);
            if (candidatura.isPresent()) {
                Cargo proprio = candidatura.get().getCargo();
                if (filtro != null && filtro != proprio) {
                    throw new ForbiddenException("Como candidato a " + proprio
                            + ", você só pode acompanhar a votação desse cargo");
                }
                return List.of(proprio);
            }
        }
        return filtro != null ? List.of(filtro) : List.of(Cargo.values());
    }

    private Map<Long, Long> votosPorCandidato(Long eleicaoId, Cargo cargo) {
        return contagemVotosApi.contarPorCargo(eleicaoId, cargo).stream()
                .collect(Collectors.toMap(ContagemCandidato::candidatoId, ContagemCandidato::votos));
    }

    private ApuracaoResponse resposta(Eleicao eleicao, TipoApuracao tipo, LocalDateTime apuradaEm,
                                      List<ResultadoCargoResponse> resultados) {
        long total = resultados.stream().mapToLong(ResultadoCargoResponse::totalVotos).sum();
        return new ApuracaoResponse(eleicao.getId(), eleicao.getStatus(), tipo, apuradaEm, total, resultados);
    }

    private record ResultadoCandidatoPendente(Candidato candidato, Cargo cargo, ResultadoCandidatoResponse linha) {
    }
}
