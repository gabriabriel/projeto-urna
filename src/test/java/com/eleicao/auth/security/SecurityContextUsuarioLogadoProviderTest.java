package com.eleicao.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.shared.domain.Perfil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextUsuarioLogadoProviderTest {

    private final SecurityContextUsuarioLogadoProvider provider = new SecurityContextUsuarioLogadoProvider();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("devolve o usuário que o filtro JWT colocou no contexto")
    void atual_comUsuarioNoContexto_devolveUsuario() {
        UsuarioLogado eleitor = new UsuarioLogado(2L, Perfil.ELEITOR);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                eleitor, null, List.of(new SimpleGrantedAuthority("ROLE_ELEITOR"))));

        assertThat(provider.atual()).isEqualTo(eleitor);
    }

    @Test
    @DisplayName("sem ninguém logado, lança exceção de autenticação (vira 401)")
    void atual_semUsuario_lancaExcecao() {
        assertThatThrownBy(provider::atual).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
