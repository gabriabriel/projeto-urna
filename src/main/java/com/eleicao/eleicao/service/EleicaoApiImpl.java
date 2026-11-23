package com.eleicao.eleicao.service;

import com.eleicao.eleicao.api.EleicaoApi;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.eleicao.repository.EleicaoRepository;
import com.eleicao.shared.domain.StatusEleicao;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que o módulo eleicao oferece aos outros módulos (cadastro, votação, apuração).
 * Fica separado do EleicaoService, que atende o Chefe da Seção (mesmo padrão do módulo cadastro).
 *
 * - NotFoundException (404) se a eleição não existe;
 * - BusinessRuleException (422) se ela não está no status exigido.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EleicaoApiImpl implements EleicaoApi {

    private final EleicaoRepository eleicaoRepository;

    @Override
    public Eleicao buscar(Long eleicaoId) {
        return eleicaoRepository.findById(eleicaoId)
                .orElseThrow(() -> NotFoundException.de("Eleição", eleicaoId));
    }

    @Override
    public Eleicao exigirAgendada(Long eleicaoId) {
        return exigirStatus(buscar(eleicaoId), StatusEleicao.AGENDADA);
    }

    @Override
    public Eleicao exigirAberta(Long eleicaoId) {
        return exigirStatus(buscar(eleicaoId), StatusEleicao.ABERTA);
    }

    private Eleicao exigirStatus(Eleicao eleicao, StatusEleicao esperado) {
        if (eleicao.getStatus() != esperado) {
            throw new BusinessRuleException(
                    "A eleição precisa estar " + esperado + ", mas está " + eleicao.getStatus());
        }
        return eleicao;
    }
}
