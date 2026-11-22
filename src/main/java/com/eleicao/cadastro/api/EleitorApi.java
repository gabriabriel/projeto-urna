package com.eleicao.cadastro.api;

import com.eleicao.cadastro.domain.Eleitor;

public interface EleitorApi {

    Eleitor buscar(Long eleitorId);

    Eleitor buscarPorUsuario(Long usuarioId);
}
