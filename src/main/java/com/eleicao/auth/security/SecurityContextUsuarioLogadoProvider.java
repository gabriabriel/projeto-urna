package com.eleicao.auth.security;

import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.auth.api.UsuarioLogadoProvider;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Implementação real do UsuarioLogadoProvider: lê o usuário que o JwtAuthenticationFilter
 * colocou no SecurityContext. É assim que os módulos de votação e apuração descobrem
 * "quem está fazendo esta requisição" sem conhecer nada de JWT ou Spring Security.
 */
@Component
public class SecurityContextUsuarioLogadoProvider implements UsuarioLogadoProvider {

    @Override
    public UsuarioLogado atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioLogado usuario) {
            return usuario;
        }
        // Só acontece se for chamado numa rota pública. Vira 401 no AuthExceptionHandler.
        throw new AuthenticationCredentialsNotFoundException("Nenhum usuário autenticado nesta requisição");
    }
}
