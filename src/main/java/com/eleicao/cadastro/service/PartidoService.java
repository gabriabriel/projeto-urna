package com.eleicao.cadastro.service;

import com.eleicao.cadastro.dto.PartidoResponse;
import com.eleicao.cadastro.repository.PartidoRepository;
import com.eleicao.shared.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de partidos (somente leitura). O cadastro de candidato precisa mostrar os partidos
 * com o número da legenda, porque o número do candidato começa pelo número do partido.
 */
@Service
@RequiredArgsConstructor
public class PartidoService {

    private final PartidoRepository partidoRepository;

    @Transactional(readOnly = true)
    public List<PartidoResponse> listar() {
        return partidoRepository.findAllByOrderByNumeroAsc().stream()
                .map(PartidoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartidoResponse buscar(Long id) {
        return partidoRepository.findById(id)
                .map(PartidoResponse::from)
                .orElseThrow(() -> NotFoundException.de("Partido", id));
    }
}
