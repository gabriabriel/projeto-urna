package com.eleicao.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.auth.dto.LoginRequest;
import com.eleicao.auth.dto.TokenResponse;
import com.eleicao.auth.exception.CredenciaisInvalidasException;
import com.eleicao.auth.repository.UsuarioRepository;
import com.eleicao.auth.security.JwtProperties;
import com.eleicao.auth.security.JwtService;
import com.eleicao.shared.domain.Perfil;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService(
            new JwtProperties("segredo-de-teste-com-pelo-menos-32-bytes!!", Duration.ofHours(2)));

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("login correto devolve um token válido com o id e o perfil do usuário")
    void login_credenciaisCorretas_devolveToken() {
        Usuario chefe = usuario(1L, "chefe", "admin123", Perfil.CHEFE_SECAO, true);
        when(usuarioRepository.findByLogin("chefe")).thenReturn(Optional.of(chefe));

        TokenResponse resposta = authService.login(new LoginRequest("chefe", "admin123"));

        assertThat(resposta.tipo()).isEqualTo("Bearer");
        assertThat(resposta.expiraEmSegundos()).isEqualTo(7200);
        assertThat(resposta.usuarioId()).isEqualTo(1L);
        assertThat(resposta.perfil()).isEqualTo(Perfil.CHEFE_SECAO);
        assertThat(jwtService.validar(resposta.token()))
                .contains(new UsuarioLogado(1L, Perfil.CHEFE_SECAO));
    }

    @Test
    @DisplayName("senha errada é recusada")
    void login_senhaErrada_lancaCredenciaisInvalidas() {
        Usuario maria = usuario(2L, "maria", "senha123", Perfil.ELEITOR, true);
        when(usuarioRepository.findByLogin("maria")).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> authService.login(new LoginRequest("maria", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("login inexistente dá exatamente a mesma resposta que senha errada")
    void login_inexistente_mesmaMensagemDeSenhaErrada() {
        when(usuarioRepository.findByLogin("ninguem")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ninguem", "qualquer")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage(new CredenciaisInvalidasException().getMessage());
    }

    @Test
    @DisplayName("usuário inativo não consegue logar, mesmo com a senha certa")
    void login_usuarioInativo_lancaCredenciaisInvalidas() {
        Usuario inativo = usuario(3L, "inativo", "senha123", Perfil.ELEITOR, false);
        when(usuarioRepository.findByLogin("inativo")).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> authService.login(new LoginRequest("inativo", "senha123")))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    private Usuario usuario(Long id, String login, String senha, Perfil perfil, boolean ativo) {
        return Usuario.builder()
                .id(id)
                .nome(login)
                .login(login)
                .senhaHash(passwordEncoder.encode(senha))
                .perfil(perfil)
                .ativo(ativo)
                .build();
    }
}
