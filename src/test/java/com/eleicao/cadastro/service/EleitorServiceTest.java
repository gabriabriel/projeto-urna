package com.eleicao.cadastro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eleicao.auth.api.UsuarioApi;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.dto.EleitorRequest;
import com.eleicao.cadastro.dto.EleitorResponse;
import com.eleicao.cadastro.dto.EleitorUpdateRequest;
import com.eleicao.cadastro.repository.CandidatoRepository;
import com.eleicao.cadastro.repository.EleitorRepository;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.shared.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class EleitorServiceTest {

    @Mock
    private EleitorRepository eleitorRepository;
    @Mock
    private CandidatoRepository candidatoRepository;
    @Mock
    private UsuarioApi usuarioApi;

    @InjectMocks
    private EleitorService eleitorService;

    @Test
    @DisplayName("criar: cria o login com perfil ELEITOR, grava o eleitor e devolve os dados sem senha")
    void criar_comDadosValidos_criaUsuarioEGravaEleitor() {
        EleitorRequest request = new EleitorRequest(
                "Maria Silva", "maria", "senha123", "123456789012", "12345678901");
        Usuario usuario = novoUsuario(10L, "Maria Silva", "maria");
        when(usuarioApi.criar("Maria Silva", "maria", "senha123", Perfil.ELEITOR)).thenReturn(usuario);
        when(eleitorRepository.save(any(Eleitor.class))).thenAnswer(invocacao -> {
            Eleitor salvo = invocacao.getArgument(0);
            salvo.setId(1L);
            return salvo;
        });

        EleitorResponse resposta = eleitorService.criar(request);

        assertThat(resposta.id()).isEqualTo(1L);
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.login()).isEqualTo("maria");
        assertThat(resposta.tituloEleitor()).isEqualTo("123456789012");
        assertThat(resposta.cpf()).isEqualTo("12345678901");
        verify(usuarioApi).criar("Maria Silva", "maria", "senha123", Perfil.ELEITOR);
        verify(eleitorRepository).save(any(Eleitor.class));
    }

    @Test
    @DisplayName("criar: título duplicado dá ConflictException (409) e não cria nada")
    void criar_comTituloDuplicado_lancaConflitoENaoCriaNada() {
        EleitorRequest request = new EleitorRequest(
                "Maria Silva", "maria", "senha123", "123456789012", "12345678901");
        Eleitor existente = novoEleitor(5L, 50L, "Outro", "outro", "123456789012", "99999999999");
        when(eleitorRepository.findByTituloEleitor("123456789012")).thenReturn(Optional.of(existente));

        ConflictException erro = assertThrows(ConflictException.class, () -> eleitorService.criar(request));

        assertThat(erro.getMessage()).contains("título");
        verify(usuarioApi, never()).criar(anyString(), anyString(), anyString(), any());
        verify(eleitorRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: CPF duplicado dá ConflictException (409) e não cria nada")
    void criar_comCpfDuplicado_lancaConflitoENaoCriaNada() {
        EleitorRequest request = new EleitorRequest(
                "Maria Silva", "maria", "senha123", "123456789012", "12345678901");
        Eleitor existente = novoEleitor(5L, 50L, "Outro", "outro", "000000000000", "12345678901");
        when(eleitorRepository.findByCpf("12345678901")).thenReturn(Optional.of(existente));

        ConflictException erro = assertThrows(ConflictException.class, () -> eleitorService.criar(request));

        assertThat(erro.getMessage()).contains("CPF");
        verify(usuarioApi, never()).criar(anyString(), anyString(), anyString(), any());
        verify(eleitorRepository, never()).save(any());
    }

    @Test
    @DisplayName("listar: converte cada eleitor em EleitorResponse")
    void listar_devolveTodosOsEleitores() {
        when(eleitorRepository.findAllByOrderByUsuarioNomeAsc()).thenReturn(List.of(
                novoEleitor(1L, 10L, "Ana", "ana", "111111111111", "11111111111"),
                novoEleitor(2L, 20L, "Bruno", "bruno", "222222222222", "22222222222")));

        List<EleitorResponse> resposta = eleitorService.listar();

        assertThat(resposta).extracting(EleitorResponse::nome).containsExactly("Ana", "Bruno");
    }

    @Test
    @DisplayName("buscar: devolve o eleitor quando existe")
    void buscar_quandoExiste_devolveEleitor() {
        when(eleitorRepository.findDetalhadoById(1L)).thenReturn(Optional.of(
                novoEleitor(1L, 10L, "Ana", "ana", "111111111111", "11111111111")));

        EleitorResponse resposta = eleitorService.buscar(1L);

        assertThat(resposta.id()).isEqualTo(1L);
        assertThat(resposta.login()).isEqualTo("ana");
    }

    @Test
    @DisplayName("buscar: id inexistente dá NotFoundException (404)")
    void buscar_quandoNaoExiste_lancaNaoEncontrado() {
        when(eleitorRepository.findDetalhadoById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eleitorService.buscar(999L));
    }

    @Test
    @DisplayName("atualizar: altera título e CPF aqui e delega nome/login ao módulo auth")
    void atualizar_comDadosValidos_alteraEleitor() {
        Eleitor eleitor = novoEleitor(1L, 10L, "Maria", "maria", "123456789012", "12345678901");
        when(eleitorRepository.findDetalhadoById(1L)).thenReturn(Optional.of(eleitor));
        EleitorUpdateRequest request = new EleitorUpdateRequest(
                "Maria Santos", "maria", "123456789012", "10987654321");

        EleitorResponse resposta = eleitorService.atualizar(1L, request);

        assertThat(resposta.cpf()).isEqualTo("10987654321");
        verify(usuarioApi).atualizar(10L, "Maria Santos", "maria");
    }

    @Test
    @DisplayName("atualizar: manter o PRÓPRIO CPF não é conflito")
    void atualizar_mantendoOProprioCpf_naoLancaConflito() {
        Eleitor eleitor = novoEleitor(1L, 10L, "Maria", "maria", "123456789012", "12345678901");
        when(eleitorRepository.findDetalhadoById(1L)).thenReturn(Optional.of(eleitor));
        // O banco "acha" o CPF, mas ele é do próprio eleitor que está sendo alterado
        when(eleitorRepository.findByCpf("12345678901")).thenReturn(Optional.of(eleitor));
        EleitorUpdateRequest request = new EleitorUpdateRequest(
                "Maria Santos", "maria", "123456789012", "12345678901");

        EleitorResponse resposta = eleitorService.atualizar(1L, request);

        assertThat(resposta.nome()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("atualizar: CPF de OUTRO eleitor dá ConflictException (409)")
    void atualizar_comCpfDeOutroEleitor_lancaConflito() {
        Eleitor eleitor = novoEleitor(1L, 10L, "Maria", "maria", "123456789012", "12345678901");
        Eleitor outro = novoEleitor(2L, 20L, "João", "joao", "222222222222", "55555555555");
        when(eleitorRepository.findDetalhadoById(1L)).thenReturn(Optional.of(eleitor));
        when(eleitorRepository.findByCpf("55555555555")).thenReturn(Optional.of(outro));
        EleitorUpdateRequest request = new EleitorUpdateRequest(
                "Maria", "maria", "123456789012", "55555555555");

        assertThrows(ConflictException.class, () -> eleitorService.atualizar(1L, request));

        verify(usuarioApi, never()).atualizar(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("atualizar: eleitor inexistente dá NotFoundException (404)")
    void atualizar_quandoNaoExiste_lancaNaoEncontrado() {
        when(eleitorRepository.findDetalhadoById(999L)).thenReturn(Optional.empty());
        EleitorUpdateRequest request = new EleitorUpdateRequest(
                "Maria", "maria", "123456789012", "12345678901");

        assertThrows(NotFoundException.class, () -> eleitorService.atualizar(999L, request));
    }

    @Test
    @DisplayName("excluir: remove o eleitor ANTES do usuário (a FK exige essa ordem)")
    void excluir_semCandidaturaESemVotos_removeEleitorDepoisUsuario() {
        Eleitor eleitor = novoEleitor(1L, 10L, "Maria", "maria", "123456789012", "12345678901");
        when(eleitorRepository.findDetalhadoById(1L)).thenReturn(Optional.of(eleitor));

        eleitorService.excluir(1L);
        InOrder ordem = inOrder(eleitorRepository, usuarioApi);
        ordem.verify(eleitorRepository).delete(eleitor);
        ordem.verify(eleitorRepository).flush();
        ordem.verify(usuarioApi).remover(10L);
    }

    @Test
    @DisplayName("excluir: eleitor que é candidato dá ConflictException (409) e nada é apagado")
    void excluir_quandoEhCandidato_lancaConflito() {
        Eleitor eleitor = novoEleitor(1L, 10L, "Maria", "maria", "123456789012", "12345678901");
        when(eleitorRepository.findDetalhadoById(1L)).thenReturn(Optional.of(eleitor));
        when(candidatoRepository.existsByEleitorId(1L)).thenReturn(true);

        ConflictException erro = assertThrows(ConflictException.class, () -> eleitorService.excluir(1L));

        assertThat(erro.getMessage()).contains("candidato");
        verify(eleitorRepository, never()).delete(any());
        verify(usuarioApi, never()).remover(anyLong());
    }

    @Test
    @DisplayName("excluir: eleitor que já votou (FK do banco barra) vira ConflictException (409)")
    void excluir_quandoJaVotou_lancaConflitoENaoRemoveUsuario() {
        Eleitor eleitor = novoEleitor(1L, 10L, "Maria", "maria", "123456789012", "12345678901");
        when(eleitorRepository.findDetalhadoById(1L)).thenReturn(Optional.of(eleitor));
        doThrow(new DataIntegrityViolationException("FK violada")).when(eleitorRepository).flush();

        ConflictException erro = assertThrows(ConflictException.class, () -> eleitorService.excluir(1L));

        assertThat(erro.getMessage()).contains("votação");
        verify(usuarioApi, never()).remover(anyLong());
    }

    @Test
    @DisplayName("excluir: eleitor inexistente dá NotFoundException (404)")
    void excluir_quandoNaoExiste_lancaNaoEncontrado() {
        when(eleitorRepository.findDetalhadoById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eleitorService.excluir(999L));

        verify(eleitorRepository, never()).delete(any());
    }

    private Usuario novoUsuario(Long id, String nome, String login) {
        Usuario usuario = Usuario.builder()
                .nome(nome)
                .login(login)
                .senhaHash("hash")
                .perfil(Perfil.ELEITOR)
                .build();
        usuario.setId(id);
        return usuario;
    }

    private Eleitor novoEleitor(Long id, Long usuarioId, String nome, String login, String titulo, String cpf) {
        return Eleitor.builder()
                .id(id)
                .usuario(novoUsuario(usuarioId, nome, login))
                .tituloEleitor(titulo)
                .cpf(cpf)
                .build();
    }
}
