package com.eleicao.cadastro.service;

import com.eleicao.cadastro.api.EleitorApi;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.repository.EleitorRepository;
import com.eleicao.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que o módulo cadastro oferece aos outros módulos (votação, apuração).
 * Fica separado do EleitorService, que atende o Chefe da Seção. O eleitor volta com o usuário já carregado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EleitorApiImpl implements EleitorApi {

    private final EleitorRepository eleitorRepository;

    @Override
    public Eleitor buscar(Long eleitorId) {
        return eleitorRepository.findDetalhadoById(eleitorId)
                .orElseThrow(() -> NotFoundException.de("Eleitor", eleitorId));
    }

    /** NotFoundException se o usuário não é eleitor (é o caso do Chefe da Seção). */
    @Override
    public Eleitor buscarPorUsuario(Long usuarioId) {
        return eleitorRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> NotFoundException.de("Eleitor do usuário", usuarioId));
    }
}
