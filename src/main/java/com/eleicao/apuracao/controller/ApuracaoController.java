package com.eleicao.apuracao.controller;

import com.eleicao.apuracao.dto.ApuracaoResponse;
import com.eleicao.apuracao.service.ApuracaoService;
import com.eleicao.shared.domain.Cargo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acompanhamento da votação e resultado final. Qualquer usuário logado consulta (GET /apuracao/**
 * em RegrasDeAcesso); a restrição do candidato ao próprio cargo é aplicada no ApuracaoService.
 */
@RestController
@RequestMapping("/apuracao")
@RequiredArgsConstructor
@Tag(name = "Apuração", description = "Acompanhamento da votação (parcial) e resultado final")
public class ApuracaoController {

    private final ApuracaoService apuracaoService;

    @GetMapping("/eleicoes/{eleicaoId}/parcial")
    @Operation(summary = "Acompanha a votação (eleição ABERTA): votos por candidato, percentuais e quem lidera. "
            + "Candidato só acompanha o próprio cargo")
    public ApuracaoResponse parcial(@PathVariable Long eleicaoId,
                                    @RequestParam(required = false) Cargo cargo) {
        return apuracaoService.parcial(eleicaoId, cargo);
    }

    @GetMapping("/eleicoes/{eleicaoId}/resultado")
    @Operation(summary = "Resultado final (eleição ENCERRADA): votos de cada candidato e o mais votado por cargo")
    public ApuracaoResponse resultado(@PathVariable Long eleicaoId,
                                      @RequestParam(required = false) Cargo cargo) {
        return apuracaoService.resultadoFinal(eleicaoId, cargo);
    }
}
