package com.eleicao.eleicao.api;

import com.eleicao.eleicao.domain.Eleicao;

public interface EleicaoApi {

    Eleicao buscar(Long eleicaoId);

    Eleicao exigirAgendada(Long eleicaoId);

    Eleicao exigirAberta(Long eleicaoId);
}
