package com.eleicao.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eleicao.auth.domain.Usuario;
import com.eleicao.auth.repository.UsuarioRepository;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.shared.exception.NotFoundException;
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
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    // BCrypt de verdade (custo baixo só para o teste ser rápido): testamos o hash real.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
    }

    @Test
    @DisplayName("criar: grava o hash BCrypt da senha, nunca a senha pura")
    void criar_gravaHashDaSenha() {
        when(usuarioRepository.existsByLogin("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario criado = usuarioService.criar("Maria Silva", "maria", "senha123", Perfil.ELEITOR);

        assertThat(criado.getSenhaHash()).isNotEqualTo("senha123").startsWith("$2a$");
        assertThat(passwordEncoder.matches("senha123", criado.getSenhaHash())).isTrue();
        assertThat(criado.getNome()).isEqualTo("Maria Silva");
        assertThat(criado.getLogin()).isEqualTo("maria");
        assertThat(criado.getPerfil()).isEqualTo(Perfil.ELEITOR);
        assertThat(criado.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("criar: login já usado gera 409 e não grava nada")
    void criar_loginDuplicado_lancaConflito() {
        when(usuarioRepository.existsByLogin("maria")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criar("Outra Maria", "maria", "senha123", Perfil.ELEITOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("maria");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: senha em branco é recusada")
    void criar_senhaEmBranco_lancaRegraDeNegocio() {
        assertThatThrownBy(() -> usuarioService.criar("Maria", "maria", "  ", Perfil.ELEITOR))
                .isInstanceOf(BusinessRuleException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("atualizar: muda nome e login e mantém senha e perfil")
    void atualizar_mudaNomeELogin() {
        Usuario usuario = usuario(5L, "Maria", "maria", Perfil.ELEITOR);
        String hashOriginal = usuario.getSenhaHash();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByLoginAndIdNot("maria.silva", 5L)).thenReturn(false);

        Usuario atualizado = usuarioService.atualizar(5L, "Maria Silva", "maria.silva");

        assertThat(atualizado.getNome()).isEqualTo("Maria Silva");
        assertThat(atualizado.getLogin()).isEqualTo("maria.silva");
        assertThat(atualizado.getSenhaHash()).isEqualTo(hashOriginal);
        assertThat(atualizado.getPerfil()).isEqualTo(Perfil.ELEITOR);
    }

    @Test
    @DisplayName("atualizar: login que já é de outro usuário gera 409 e não altera nada")
    void atualizar_loginDeOutro_lancaConflito() {
        Usuario usuario = usuario(5L, "Maria", "maria", Perfil.ELEITOR);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByLoginAndIdNot("joao", 5L)).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.atualizar(5L, "Maria", "joao"))
                .isInstanceOf(ConflictException.class);
        assertThat(usuario.getLogin()).isEqualTo("maria");
    }

    @Test
    @DisplayName("atualizar: usuário inexistente gera 404")
    void atualizar_inexistente_lancaNaoEncontrado() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.atualizar(99L, "X", "xxx"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("remover: apaga o usuário existente")
    void remover_existente_apaga() {
        Usuario usuario = usuario(5L, "Maria", "maria", Perfil.ELEITOR);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        usuarioService.remover(5L);

        verify(usuarioRepository).delete(usuario);
    }

    @Test
    @DisplayName("remover: usuário inexistente não é erro")
    void remover_inexistente_naoFazNada() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        usuarioService.remover(99L);

        verify(usuarioRepository, never()).delete(any());
    }

    private Usuario usuario(Long id, String nome, String login, Perfil perfil) {
        return Usuario.builder()
                .id(id)
                .nome(nome)
                .login(login)
                .senhaHash(passwordEncoder.encode("senha123"))
                .perfil(perfil)
                .build();
    }
}
