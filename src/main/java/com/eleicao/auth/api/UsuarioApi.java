package com.eleicao.auth.api;

import com.eleicao.auth.domain.Usuario;
import com.eleicao.shared.domain.Perfil;


public interface UsuarioApi {


    Usuario criar(String nome, String login, String senhaPlana, Perfil perfil);

    Usuario atualizar(Long usuarioId, String nome, String login);

    void remover(Long usuarioId);
}
