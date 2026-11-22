package com.eleicao.cadastro.controller;

import com.eleicao.cadastro.dto.PartidoResponse;
import com.eleicao.cadastro.service.PartidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de partidos (qualquer usuário logado). Não há POST/PUT/DELETE: os partidos vêm da
 * migration V3. A regra de acesso por perfil fica no módulo auth.
 */
@RestController
@RequestMapping("/partidos")
@RequiredArgsConstructor
@Tag(name = "Partidos", description = "Consulta dos partidos registrados no TSE (somente leitura)")
public class PartidoController {

    private final PartidoService partidoService;

    @GetMapping
    @Operation(summary = "Lista os partidos por ordem de número da legenda")
    public List<PartidoResponse> listar() {
        return partidoService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um partido")
    public PartidoResponse buscar(@PathVariable Long id) {
        return partidoService.buscar(id);
    }
}
