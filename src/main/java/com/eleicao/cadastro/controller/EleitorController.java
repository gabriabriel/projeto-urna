package com.eleicao.cadastro.controller;

import com.eleicao.cadastro.dto.EleitorRequest;
import com.eleicao.cadastro.dto.EleitorResponse;
import com.eleicao.cadastro.dto.EleitorUpdateRequest;
import com.eleicao.cadastro.service.EleitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints de eleitores (uso do Chefe da Seção). O controller só recebe, valida e devolve:
 * nenhuma regra de negócio fica aqui. A restrição por perfil é configurada no módulo auth.
 */
@RestController
@RequestMapping("/eleitores")
@RequiredArgsConstructor
@Tag(name = "Eleitores", description = "Cadastro de eleitores (Chefe da Seção)")
public class EleitorController {

    private final EleitorService eleitorService;

    @PostMapping
    @Operation(summary = "Cadastra um eleitor e cria o login dele")
    public ResponseEntity<EleitorResponse> criar(@Valid @RequestBody EleitorRequest request) {
        EleitorResponse criado = eleitorService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criado.id())
                .toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista os eleitores por ordem de nome")
    public List<EleitorResponse> listar() {
        return eleitorService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um eleitor")
    public EleitorResponse buscar(@PathVariable Long id) {
        return eleitorService.buscar(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera nome, login, título e CPF de um eleitor")
    public EleitorResponse atualizar(@PathVariable Long id, @Valid @RequestBody EleitorUpdateRequest request) {
        return eleitorService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um eleitor (bloqueado se já votou ou se é candidato)")
    public void excluir(@PathVariable Long id) {
        eleitorService.excluir(id);
    }
}
