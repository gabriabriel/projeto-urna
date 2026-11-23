package com.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.auth.api.UsuarioLogadoProvider;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.cadastro.api.EleitorApi;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.domain.Partido;
import com.eleicao.eleicao.api.EleicaoApi;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.domain.StatusEleicao;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.votacao.domain.Participacao;
import com.eleicao.votacao.domain.Voto;
import com.eleicao.votacao.dto.ComprovanteVotoResponse;
import com.eleicao.votacao.dto.ResumoVotoResponse;
import com.eleicao.votacao.dto.VotoRequest;
import com.eleicao.votacao.repository.ParticipacaoRepository;
import com.eleicao.votacao.repository.VotoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class VotacaoServiceTest {

    private static final Long ELEICAO_ID = 1L;
    private static final Long OUTRA_ELEICAO_ID = 2L;
    private static final Long USUARIO_ID = 10L;
    private static final Long ELEITOR_ID = 1L;
    private static final Long CANDIDATO_ID = 5L;

    @Mock
    private EleicaoApi eleicaoApi;
    @Mock
    private EleitorApi eleitorApi;
    @Mock
    private CandidatoApi candidatoApi;
    @Mock
    private UsuarioLogadoProvider usuarioLogadoProvider;
    @Mock
    private ParticipacaoRepository participacaoRepository;
    @Mock
    private VotoRepository votoRepository;

    @InjectMocks
    private VotacaoService votacaoService;

    @Test
    @DisplayName("registrar: escolha válida devolve o resumo e não grava nada")
    void registrar_comEscolhaValida_devolveResumoSemGravar() {
        prepararEleitorLogado();
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(novaEleicao(ELEICAO_ID));
        when(candidatoApi.buscar(CANDIDATO_ID)).thenReturn(novoCandidato(ELEICAO_ID, Cargo.GOVERNADOR));
        when(participacaoRepository.existsByEleicaoIdAndEleitorIdAndCargo(ELEICAO_ID, ELEITOR_ID, Cargo.GOVERNADOR))
                .thenReturn(false);

        ResumoVotoResponse resumo = votacaoService.registrar(ELEICAO_ID, pedido(Cargo.GOVERNADOR));

        assertThat(resumo.eleicaoId()).isEqualTo(ELEICAO_ID);
        assertThat(resumo.cargo()).isEqualTo(Cargo.GOVERNADOR);
        assertThat(resumo.candidato().nome()).isEqualTo("Maria Silva");
        assertThat(resumo.candidato().numero()).isEqualTo(13);
        verify(participacaoRepository, never()).saveAndFlush(any());
        verify(votoRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrar: eleição que não está ABERTA dá BusinessRuleException (422)")
    void registrar_comEleicaoNaoAberta_lancaRegraDeNegocio() {
        prepararEleitorLogado();
        when(eleicaoApi.exigirAberta(ELEICAO_ID))
                .thenThrow(new BusinessRuleException("A eleição precisa estar ABERTA, mas está AGENDADA"));

        assertThrows(BusinessRuleException.class,
                () -> votacaoService.registrar(ELEICAO_ID, pedido(Cargo.GOVERNADOR)));

        verify(candidatoApi, never()).buscar(any());
    }

    @Test
    @DisplayName("registrar: candidato de outra eleição dá BusinessRuleException (422)")
    void registrar_comCandidatoDeOutraEleicao_lancaRegraDeNegocio() {
        prepararEleitorLogado();
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(novaEleicao(ELEICAO_ID));
        when(candidatoApi.buscar(CANDIDATO_ID)).thenReturn(novoCandidato(OUTRA_ELEICAO_ID, Cargo.GOVERNADOR));

        assertThrows(BusinessRuleException.class,
                () -> votacaoService.registrar(ELEICAO_ID, pedido(Cargo.GOVERNADOR)));
    }

    @Test
    @DisplayName("registrar: candidato de outro cargo dá BusinessRuleException (422)")
    void registrar_comCandidatoDeOutroCargo_lancaRegraDeNegocio() {
        prepararEleitorLogado();
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(novaEleicao(ELEICAO_ID));
        when(candidatoApi.buscar(CANDIDATO_ID)).thenReturn(novoCandidato(ELEICAO_ID, Cargo.SENADOR));

        assertThrows(BusinessRuleException.class,
                () -> votacaoService.registrar(ELEICAO_ID, pedido(Cargo.GOVERNADOR)));
    }

    @Test
    @DisplayName("registrar: eleitor que já votou neste cargo dá ConflictException (409)")
    void registrar_comCargoJaVotado_lancaConflito() {
        prepararEleitorLogado();
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(novaEleicao(ELEICAO_ID));
        when(candidatoApi.buscar(CANDIDATO_ID)).thenReturn(novoCandidato(ELEICAO_ID, Cargo.GOVERNADOR));
        when(participacaoRepository.existsByEleicaoIdAndEleitorIdAndCargo(ELEICAO_ID, ELEITOR_ID, Cargo.GOVERNADOR))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> votacaoService.registrar(ELEICAO_ID, pedido(Cargo.GOVERNADOR)));
    }

    @Test
    @DisplayName("confirmar: grava a participação e o voto, cada um com seu papel")
    void confirmar_comEscolhaValida_gravaParticipacaoEVoto() {
        prepararEleitorLogado();
        Eleicao eleicao = novaEleicao(ELEICAO_ID);
        Candidato candidato = novoCandidato(ELEICAO_ID, Cargo.GOVERNADOR);
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(eleicao);
        when(candidatoApi.buscar(CANDIDATO_ID)).thenReturn(candidato);
        when(participacaoRepository.existsByEleicaoIdAndEleitorIdAndCargo(ELEICAO_ID, ELEITOR_ID, Cargo.GOVERNADOR))
                .thenReturn(false);
        when(participacaoRepository.saveAndFlush(any(Participacao.class))).thenAnswer(invocacao -> {
            Participacao gravada = invocacao.getArgument(0);
            return gravada;
        });

        ComprovanteVotoResponse comprovante = votacaoService.confirmar(ELEICAO_ID, pedido(Cargo.GOVERNADOR));

        assertThat(comprovante.eleicaoId()).isEqualTo(ELEICAO_ID);
        assertThat(comprovante.cargo()).isEqualTo(Cargo.GOVERNADOR);
        assertThat(comprovante.confirmadoEm()).isNotNull();

        ArgumentCaptor<Participacao> participacao = ArgumentCaptor.forClass(Participacao.class);
        verify(participacaoRepository).saveAndFlush(participacao.capture());
        assertThat(participacao.getValue().getEleitor().getId()).isEqualTo(ELEITOR_ID);
        assertThat(participacao.getValue().getEleicao()).isSameAs(eleicao);
        assertThat(participacao.getValue().getCargo()).isEqualTo(Cargo.GOVERNADOR);

        ArgumentCaptor<Voto> voto = ArgumentCaptor.forClass(Voto.class);
        verify(votoRepository).save(voto.capture());
        assertThat(voto.getValue().getCandidato()).isSameAs(candidato);
    }

    @Test
    @DisplayName("confirmar: se o banco recusa o segundo voto, nada do voto é gravado")
    void confirmar_quandoBancoRecusaDuplicidade_naoGravaVoto() {
        prepararEleitorLogado();
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(novaEleicao(ELEICAO_ID));
        when(candidatoApi.buscar(CANDIDATO_ID)).thenReturn(novoCandidato(ELEICAO_ID, Cargo.GOVERNADOR));
        when(participacaoRepository.existsByEleicaoIdAndEleitorIdAndCargo(ELEICAO_ID, ELEITOR_ID, Cargo.GOVERNADOR))
                .thenReturn(false);
        when(participacaoRepository.saveAndFlush(any(Participacao.class)))
                .thenThrow(new DataIntegrityViolationException("uk_participacao_eleicao_eleitor_cargo"));

        assertThrows(DataIntegrityViolationException.class,
                () -> votacaoService.confirmar(ELEICAO_ID, pedido(Cargo.GOVERNADOR)));

        verify(votoRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmar: eleição que não está ABERTA dá BusinessRuleException e não grava")
    void confirmar_comEleicaoNaoAberta_naoGrava() {
        prepararEleitorLogado();
        when(eleicaoApi.exigirAberta(ELEICAO_ID))
                .thenThrow(new BusinessRuleException("A eleição precisa estar ABERTA, mas está ENCERRADA"));

        assertThrows(BusinessRuleException.class,
                () -> votacaoService.confirmar(ELEICAO_ID, pedido(Cargo.GOVERNADOR)));

        verify(participacaoRepository, never()).saveAndFlush(any());
        verify(votoRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ auxiliares

    private VotoRequest pedido(Cargo cargo) {
        return new VotoRequest(cargo, CANDIDATO_ID);
    }

    private void prepararEleitorLogado() {
        when(usuarioLogadoProvider.atual()).thenReturn(new UsuarioLogado(USUARIO_ID, Perfil.ELEITOR));
        when(eleitorApi.buscarPorUsuario(USUARIO_ID)).thenReturn(novoEleitor());
    }

    private Eleicao novaEleicao(Long id) {
        return Eleicao.builder()
                .id(id)
                .data(LocalDate.of(2026, 12, 1))
                .turno(1)
                .horarioAbertura(LocalTime.of(8, 0))
                .horarioEncerramento(LocalTime.of(17, 0))
                .status(StatusEleicao.ABERTA)
                .abertaEm(LocalDateTime.of(2026, 12, 1, 8, 0))
                .build();
    }

    private Eleitor novoEleitor() {
        Usuario usuario = Usuario.builder()
                .nome("Maria Silva")
                .login("maria")
                .senhaHash("hash")
                .perfil(Perfil.ELEITOR)
                .build();
        usuario.setId(USUARIO_ID);
        return Eleitor.builder()
                .id(ELEITOR_ID)
                .usuario(usuario)
                .tituloEleitor("123456789012")
                .cpf("12345678901")
                .build();
    }

    private Candidato novoCandidato(Long eleicaoId, Cargo cargo) {
        Partido partido = Partido.builder().id(4L).numero(13).sigla("PT").nome("Partido dos Trabalhadores").build();
        return Candidato.builder()
                .id(CANDIDATO_ID)
                .eleitor(novoEleitor())
                .eleicao(novaEleicao(eleicaoId))
                .partido(partido)
                .cargo(cargo)
                .numero(13)
                .build();
    }
}
