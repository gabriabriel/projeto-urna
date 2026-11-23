package com.eleicao.votacao.controller;

import com.eleicao.shared.domain.Cargo;
import com.eleicao.votacao.dto.CandidatoVotavelResponse;
import com.eleicao.votacao.dto.ComprovanteVotoResponse;
import com.eleicao.votacao.dto.ResumoVotoResponse;
import com.eleicao.votacao.dto.VotoRequest;
import com.eleicao.votacao.service.VotacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints da votação (perfil ELEITOR, configurado no módulo auth). Só recebe, valida e devolve:
 * a regra de negócio fica no VotacaoService.
 */
@RestController
@RequestMapping("/votacao")
@RequiredArgsConstructor
@Tag(name = "Votação", description = "Consulta de candidatos, registro e confirmação do voto (Eleitor)")
public class VotacaoController {

    private final VotacaoService votacaoService;

    @GetMapping("/eleicoes/{eleicaoId}/cargos/{cargo}/candidatos")
    @Operation(summary = "Lista os candidatos disponíveis para um cargo na eleição")
    public List<CandidatoVotavelResponse> listarCandidatos(@PathVariable Long eleicaoId,
                                                           @PathVariable Cargo cargo) {
        return votacaoService.listarCandidatos(eleicaoId, cargo);
    }

    @PostMapping("/eleicoes/{eleicaoId}/voto/registro")
    @Operation(summary = "Registra a escolha: confere e devolve o resumo, sem gravar (a eleição precisa estar ABERTA)")
    public ResumoVotoResponse registrar(@PathVariable Long eleicaoId, @Valid @RequestBody VotoRequest request) {
        return votacaoService.registrar(eleicaoId, request);
    }

    @PostMapping("/eleicoes/{eleicaoId}/voto/confirmacao")
    @Operation(summary = "Confirma o voto. Grava e não pode ser alterado (a eleição precisa estar ABERTA)")
    public ResponseEntity<ComprovanteVotoResponse> confirmar(@PathVariable Long eleicaoId,
                                                             @Valid @RequestBody VotoRequest request) {
        ComprovanteVotoResponse comprovante = votacaoService.confirmar(eleicaoId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comprovante);
    }
}
