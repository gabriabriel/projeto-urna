package com.eleicao.eleicao.controller;

import com.eleicao.eleicao.dto.EleicaoRequest;
import com.eleicao.eleicao.dto.EleicaoResponse;
import com.eleicao.eleicao.dto.SituacaoEleicaoResponse;
import com.eleicao.eleicao.service.EleicaoService;
import com.eleicao.shared.domain.StatusEleicao;
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
 * Endpoints da eleição. Consultar é de qualquer usuário logado; cadastrar, alterar, excluir,
 * abrir e encerrar são do Chefe da Seção (regras em RegrasDeAcesso, módulo auth).
 * O controller só recebe, valida e devolve: a regra de negócio fica no EleicaoService.
 */
@RestController
@RequestMapping("/eleicoes")
@RequiredArgsConstructor
@Tag(name = "Eleições", description = "Cadastro da eleição e abertura/encerramento da votação (Chefe da Seção)")
public class EleicaoController {

    private final EleicaoService eleicaoService;

    @PostMapping
    @Operation(summary = "Cadastra uma eleição (data, turno e horários). Ela nasce AGENDADA")
    public ResponseEntity<EleicaoResponse> criar(@Valid @RequestBody EleicaoRequest request) {
        EleicaoResponse criada = eleicaoService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criada.id())
                .toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @GetMapping
    @Operation(summary = "Lista as eleições, com filtro opcional por status")
    public List<EleicaoResponse> listar(@RequestParam(required = false) StatusEleicao status) {
        return eleicaoService.listar(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma eleição")
    public EleicaoResponse buscar(@PathVariable Long id) {
        return eleicaoService.buscar(id);
    }

    @GetMapping("/{id}/situacao")
    @Operation(summary = "Consulta a situação atual da eleição (status, datas reais e candidatos por cargo)")
    public SituacaoEleicaoResponse situacao(@PathVariable Long id) {
        return eleicaoService.situacao(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera data, turno e horários (a eleição precisa estar AGENDADA)")
    public EleicaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody EleicaoRequest request) {
        return eleicaoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma eleição (precisa estar AGENDADA e sem candidatos)")
    public void excluir(@PathVariable Long id) {
        eleicaoService.excluir(id);
    }

    @PostMapping("/{id}/abertura")
    @Operation(summary = "Abre a votação (AGENDADA -> ABERTA). Exige pelo menos 2 candidatos")
    public EleicaoResponse abrir(@PathVariable Long id) {
        return eleicaoService.abrir(id);
    }

    @PostMapping("/{id}/encerramento")
    @Operation(summary = "Encerra a votação (ABERTA -> ENCERRADA) e grava o resultado final")
    public EleicaoResponse encerrar(@PathVariable Long id) {
        return eleicaoService.encerrar(id);
    }
}
