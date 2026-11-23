package com.eleicao.eleicao.service;

import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.eleicao.api.EleicaoEncerradaEvent;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.eleicao.dto.EleicaoRequest;
import com.eleicao.eleicao.dto.EleicaoResponse;
import com.eleicao.eleicao.dto.SituacaoEleicaoResponse;
import com.eleicao.eleicao.repository.EleicaoRepository;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.StatusEleicao;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.shared.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do Chefe da Seção sobre a eleição e seu ciclo de vida:
 *
 *   AGENDADA --abrir--> ABERTA --encerrar--> ENCERRADA
 *
 * - Só se altera ou exclui uma eleição AGENDADA.
 * - Só se abre uma eleição AGENDADA com pelo menos 2 candidatos.
 * - Só se encerra uma eleição ABERTA. Ao encerrar, publica EleicaoEncerradaEvent (o módulo apuracao
 *   escuta e grava o resultado final).
 * - Não há volta: uma eleição nunca retorna a um status anterior.
 *
 * Os candidatos pertencem ao módulo cadastro e só são acessados pela interface CandidatoApi.
 */
@Service
@RequiredArgsConstructor
public class EleicaoService {

    /** Mínimo de candidatos (somando todos os cargos) para a votação poder ser aberta. */
    static final int MINIMO_CANDIDATOS_PARA_ABRIR = 2;

    private final EleicaoRepository eleicaoRepository;
    private final CandidatoApi candidatoApi;
    private final ApplicationEventPublisher eventos;

    @Transactional
    public EleicaoResponse criar(EleicaoRequest request) {
        exigirHorariosValidos(request);
        if (eleicaoRepository.existsByDataAndTurno(request.data(), request.turno())) {
            throw new ConflictException(
                    "Já existe uma eleição em " + request.data() + " no " + request.turno() + "º turno");
        }

        Eleicao eleicao = eleicaoRepository.save(Eleicao.builder()
                .data(request.data())
                .turno(request.turno())
                .horarioAbertura(request.horarioAbertura())
                .horarioEncerramento(request.horarioEncerramento())
                .status(StatusEleicao.AGENDADA)
                .build());

        return EleicaoResponse.from(eleicao);
    }

    /** Filtro opcional por status: sem ele, lista todas (mais recentes primeiro). */
    @Transactional(readOnly = true)
    public List<EleicaoResponse> listar(StatusEleicao status) {
        List<Eleicao> eleicoes = status == null
                ? eleicaoRepository.findAllByOrderByDataDescTurnoAsc()
                : eleicaoRepository.findByStatusOrderByDataDescTurnoAsc(status);
        return eleicoes.stream().map(EleicaoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EleicaoResponse buscar(Long id) {
        return EleicaoResponse.from(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public SituacaoEleicaoResponse situacao(Long id) {
        Eleicao eleicao = buscarEntidade(id);
        Map<Cargo, Integer> porCargo = candidatosPorCargo(id);
        int total = porCargo.values().stream().mapToInt(Integer::intValue).sum();
        return new SituacaoEleicaoResponse(
                eleicao.getId(), eleicao.getStatus(), eleicao.getAbertaEm(), eleicao.getEncerradaEm(),
                total, porCargo);
    }

    @Transactional
    public EleicaoResponse atualizar(Long id, EleicaoRequest request) {
        Eleicao eleicao = buscarParaAlterar(id);
        exigirStatus(eleicao, StatusEleicao.AGENDADA, "alterada");
        exigirHorariosValidos(request);
        if (eleicaoRepository.existsByDataAndTurnoAndIdNot(request.data(), request.turno(), id)) {
            throw new ConflictException(
                    "Já existe outra eleição em " + request.data() + " no " + request.turno() + "º turno");
        }

        eleicao.setData(request.data());
        eleicao.setTurno(request.turno());
        eleicao.setHorarioAbertura(request.horarioAbertura());
        eleicao.setHorarioEncerramento(request.horarioEncerramento());

        return EleicaoResponse.from(eleicao);
    }

    @Transactional
    public void excluir(Long id) {
        Eleicao eleicao = buscarParaAlterar(id);
        exigirStatus(eleicao, StatusEleicao.AGENDADA, "excluída");

        // As candidaturas são do módulo cadastro: checamos pela CandidatoApi para dar uma mensagem clara.
        if (contarCandidatos(id) > 0) {
            throw new ConflictException(
                    "A eleição possui candidatos e não pode ser excluída. Exclua as candidaturas antes");
        }

        try {
            eleicaoRepository.delete(eleicao);
            // flush força o DELETE agora: se algum outro registro ainda apontar para a eleição,
            // a chave estrangeira do banco barra aqui (trava final) e vira 409.
            eleicaoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A eleição possui registros vinculados e não pode ser excluída");
        }
    }

    /** AGENDADA -> ABERTA. Exige pelo menos 2 candidatos cadastrados na eleição. */
    @Transactional
    public EleicaoResponse abrir(Long id) {
        Eleicao eleicao = buscarParaAlterar(id);
        exigirStatus(eleicao, StatusEleicao.AGENDADA, "aberta");

        int candidatos = contarCandidatos(id);
        if (candidatos < MINIMO_CANDIDATOS_PARA_ABRIR) {
            throw new BusinessRuleException("A votação só pode ser aberta com pelo menos "
                    + MINIMO_CANDIDATOS_PARA_ABRIR + " candidatos cadastrados (a eleição tem " + candidatos + ")");
        }

        eleicao.setStatus(StatusEleicao.ABERTA);
        eleicao.setAbertaEm(LocalDateTime.now());
        return EleicaoResponse.from(eleicao);
    }

    /** ABERTA -> ENCERRADA. A partir daqui nenhum voto é aceito e o resultado final é gravado. */
    @Transactional
    public EleicaoResponse encerrar(Long id) {
        Eleicao eleicao = buscarParaAlterar(id);
        exigirStatus(eleicao, StatusEleicao.ABERTA, "encerrada");

        eleicao.setStatus(StatusEleicao.ENCERRADA);
        eleicao.setEncerradaEm(LocalDateTime.now());

        // Síncrono e na mesma transação: o módulo apuracao grava o resultado final agora.
        eventos.publishEvent(new EleicaoEncerradaEvent(eleicao.getId()));

        return EleicaoResponse.from(eleicao);
    }

    // ------------------------------------------------------------------ privados

    private Eleicao buscarEntidade(Long id) {
        return eleicaoRepository.findById(id)
                .orElseThrow(() -> NotFoundException.de("Eleição", id));
    }

    private Eleicao buscarParaAlterar(Long id) {
        return eleicaoRepository.buscarParaAlterar(id)
                .orElseThrow(() -> NotFoundException.de("Eleição", id));
    }

    private void exigirStatus(Eleicao eleicao, StatusEleicao esperado, String acao) {
        if (eleicao.getStatus() != esperado) {
            throw new BusinessRuleException("A eleição só pode ser " + acao + " com status " + esperado
                    + ", mas está " + eleicao.getStatus());
        }
    }

    /** Mesma regra do CHECK do banco (ck_eleicao_horarios), aqui com mensagem clara. */
    private void exigirHorariosValidos(EleicaoRequest request) {
        if (!request.horarioEncerramento().isAfter(request.horarioAbertura())) {
            throw new BusinessRuleException("O horário de encerramento deve ser posterior ao horário de abertura");
        }
    }

    private Map<Cargo, Integer> candidatosPorCargo(Long eleicaoId) {
        Map<Cargo, Integer> porCargo = new EnumMap<>(Cargo.class);
        for (Cargo cargo : Cargo.values()) {
            porCargo.put(cargo, candidatoApi.listarPorEleicaoECargo(eleicaoId, cargo).size());
        }
        return porCargo;
    }

    private int contarCandidatos(Long eleicaoId) {
        return candidatosPorCargo(eleicaoId).values().stream().mapToInt(Integer::intValue).sum();
    }
}
