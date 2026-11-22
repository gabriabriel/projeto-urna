package com.eleicao.cadastro.controller;

import com.eleicao.cadastro.dto.CandidatoRequest;
import com.eleicao.cadastro.dto.CandidatoResponse;
import com.eleicao.cadastro.dto.CandidatoUpdateRequest;
import com.eleicao.cadastro.service.CandidatoService;
import com.eleicao.shared.domain.Cargo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints de candidatos. Cadastrar, alterar e excluir são do Chefe da Seção; consultar é de
 * qualquer usuário logado (o eleitor precisa ver os candidatos para votar). A restrição por
 * perfil é configurada no módulo auth.
 */
@RestController
@RequestMapping("/candidatos")
@RequiredArgsConstructor
@Tag(name = "Candidatos", description = "Candidaturas (eleitor concorrendo a um cargo em uma eleição)")
public class CandidatoController {

    private final CandidatoService candidatoService;

    @PostMapping
    @Operation(summary = "Cadastra um candidato (a eleição precisa estar AGENDADA)")
    public ResponseEntity<CandidatoResponse> criar(@Valid @RequestBody CandidatoRequest request) {
        CandidatoResponse criado = candidatoService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criado.id())
                .toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista candidatos, com filtros opcionais por eleição e cargo")
    public List<CandidatoResponse> listar(@RequestParam(required = false) Long eleicaoId,
                                          @RequestParam(required = false) Cargo cargo) {
        return candidatoService.listar(eleicaoId, cargo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um candidato")
    public CandidatoResponse buscar(@PathVariable Long id) {
        return candidatoService.buscar(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera partido, cargo e número (a eleição precisa estar AGENDADA)")
    public CandidatoResponse atualizar(@PathVariable Long id, @Valid @RequestBody CandidatoUpdateRequest request) {
        return candidatoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um candidato (a eleição precisa estar AGENDADA)")
    public void excluir(@PathVariable Long id) {
        candidatoService.excluir(id);
    }
}
