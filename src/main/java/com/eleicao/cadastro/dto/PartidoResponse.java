package com.eleicao.cadastro.dto;

import com.eleicao.cadastro.domain.Partido;

/** Saída do partido. Só consulta: partidos não são criados nem alterados pela API. */
public record PartidoResponse(Long id, Integer numero, String sigla, String nome) {

    public static PartidoResponse from(Partido partido) {
        return new PartidoResponse(partido.getId(), partido.getNumero(), partido.getSigla(), partido.getNome());
    }
}
