package com.eleicao.apuracao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eleicao.apuracao.domain.Apuracao;
import com.eleicao.apuracao.domain.ResultadoCandidato;
import com.eleicao.apuracao.domain.SituacaoCargo;
import com.eleicao.apuracao.domain.TipoApuracao;
import com.eleicao.apuracao.dto.ApuracaoResponse;
import com.eleicao.apuracao.dto.ResultadoCargoResponse;
import com.eleicao.apuracao.repository.ApuracaoRepository;
import com.eleicao.apuracao.repository.ResultadoCandidatoRepository;
import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.auth.api.UsuarioLogadoProvider;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.cadastro.api.EleitorApi;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.domain.Partido;
import com.eleicao.eleicao.api.EleicaoApi;
import com.eleicao.eleicao.api.EleicaoEncerradaEvent;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.domain.StatusEleicao;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ForbiddenException;
import com.eleicao.votacao.api.ContagemCandidato;
import com.eleicao.votacao.api.ContagemVotosApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApuracaoServiceTest {

    private static final Long ELEICAO_ID = 1L;
    private static final Long USUARIO_ID = 50L;
    private static final Long ELEITOR_ID = 60L;

    private static final Candidato ANA = candidato(11L, "Ana", 13, Cargo.GOVERNADOR);
    private static final Candidato BETO = candidato(12L, "Beto", 22, Cargo.GOVERNADOR);
    private static final Candidato CAIO = candidato(21L, "Caio", 15, Cargo.SENADOR);
    private static final Candidato DORA = candidato(22L, "Dora", 45, Cargo.SENADOR);

    @Mock
    private EleicaoApi eleicaoApi;
    @Mock
    private CandidatoApi candidatoApi;
    @Mock
    private EleitorApi eleitorApi;
    @Mock
    private ContagemVotosApi contagemVotosApi;
    @Mock
    private UsuarioLogadoProvider usuarioLogadoProvider;
    @Mock
    private ApuracaoRepository apuracaoRepository;
    @Mock
    private ResultadoCandidatoRepository resultadoCandidatoRepository;

    @InjectMocks
    private ApuracaoService apuracaoService;

    // ------------------------------------------------------------------ parcial

    @Test
    @DisplayName("parcial: eleição que não está ABERTA dá BusinessRuleException (422) e não conta votos")
    void parcial_eleicaoNaoAberta_422() {
        when(eleicaoApi.exigirAberta(ELEICAO_ID))
                .thenThrow(new BusinessRuleException("A eleição precisa estar ABERTA, mas está ENCERRADA"));

        assertThrows(BusinessRuleException.class, () -> apuracaoService.parcial(ELEICAO_ID, null));
        verifyNoInteractions(contagemVotosApi);
    }

    @Test
    @DisplayName("parcial: eleitor comum vê todos os cargos que têm candidatos, com votos e percentuais; nada é gravado")
    void parcial_eleitorComum_veTodosOsCargos() {
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(eleicao(StatusEleicao.ABERTA));
        logadoComo(Perfil.ELEITOR);
        when(eleitorApi.buscarPorUsuario(USUARIO_ID)).thenReturn(eleitor());
        when(candidatoApi.buscarPorEleitorEEleicao(ELEITOR_ID, ELEICAO_ID)).thenReturn(Optional.empty());
        prepararCandidatosEVotos();

        ApuracaoResponse resposta = apuracaoService.parcial(ELEICAO_ID, null);

        assertThat(resposta.tipo()).isEqualTo(TipoApuracao.PARCIAL);
        assertThat(resposta.cargos()).extracting(ResultadoCargoResponse::cargo)
                .containsExactly(Cargo.SENADOR, Cargo.GOVERNADOR);
        ResultadoCargoResponse governador = resposta.cargos().get(1);
        assertThat(governador.situacao()).isEqualTo(SituacaoCargo.VENCEDOR);
        assertThat(governador.vencedor().nome()).isEqualTo("Beto");
        assertThat(governador.vencedor().percentual()).isEqualByComparingTo("75.00");
        assertThat(resposta.cargos().get(0).situacao()).isEqualTo(SituacaoCargo.EMPATE);
        assertThat(resposta.totalVotos()).isEqualTo(6);
        verifyNoInteractions(apuracaoRepository, resultadoCandidatoRepository);
    }

    @Test
    @DisplayName("parcial: eleitor que é candidato a Governador só vê Governador")
    void parcial_candidato_soVeOProprioCargo() {
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(eleicao(StatusEleicao.ABERTA));
        logadoComo(Perfil.ELEITOR);
        when(eleitorApi.buscarPorUsuario(USUARIO_ID)).thenReturn(eleitor());
        when(candidatoApi.buscarPorEleitorEEleicao(ELEITOR_ID, ELEICAO_ID)).thenReturn(Optional.of(ANA));
        when(candidatoApi.listarPorEleicaoECargo(ELEICAO_ID, Cargo.GOVERNADOR)).thenReturn(List.of(ANA, BETO));
        when(contagemVotosApi.contarPorCargo(ELEICAO_ID, Cargo.GOVERNADOR))
                .thenReturn(List.of(new ContagemCandidato(11L, 1), new ContagemCandidato(12L, 3)));

        ApuracaoResponse resposta = apuracaoService.parcial(ELEICAO_ID, null);

        assertThat(resposta.cargos()).extracting(ResultadoCargoResponse::cargo).containsExactly(Cargo.GOVERNADOR);
    }

    @Test
    @DisplayName("parcial: candidato a Governador pedindo Senador dá ForbiddenException (403)")
    void parcial_candidatoPedindoOutroCargo_403() {
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(eleicao(StatusEleicao.ABERTA));
        logadoComo(Perfil.ELEITOR);
        when(eleitorApi.buscarPorUsuario(USUARIO_ID)).thenReturn(eleitor());
        when(candidatoApi.buscarPorEleitorEEleicao(ELEITOR_ID, ELEICAO_ID)).thenReturn(Optional.of(ANA));

        assertThrows(ForbiddenException.class, () -> apuracaoService.parcial(ELEICAO_ID, Cargo.SENADOR));
        verifyNoInteractions(contagemVotosApi);
    }

    @Test
    @DisplayName("parcial: Chefe da Seção vê todos os cargos (não é eleitor, não consulta candidatura)")
    void parcial_chefe_veTodos() {
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(eleicao(StatusEleicao.ABERTA));
        logadoComo(Perfil.CHEFE_SECAO);
        prepararCandidatosEVotos();

        ApuracaoResponse resposta = apuracaoService.parcial(ELEICAO_ID, null);

        assertThat(resposta.cargos()).hasSize(2);
        verifyNoInteractions(eleitorApi);
    }

    @Test
    @DisplayName("parcial: filtro por cargo sem candidatos devolve o cargo com SEM_CANDIDATOS")
    void parcial_filtroCargoSemCandidatos() {
        when(eleicaoApi.exigirAberta(ELEICAO_ID)).thenReturn(eleicao(StatusEleicao.ABERTA));
        logadoComo(Perfil.CHEFE_SECAO);
        when(candidatoApi.listarPorEleicaoECargo(ELEICAO_ID, Cargo.PRESIDENTE)).thenReturn(List.of());
        when(contagemVotosApi.contarPorCargo(ELEICAO_ID, Cargo.PRESIDENTE)).thenReturn(List.of());

        ApuracaoResponse resposta = apuracaoService.parcial(ELEICAO_ID, Cargo.PRESIDENTE);

        assertThat(resposta.cargos()).singleElement()
                .satisfies(c -> assertThat(c.situacao()).isEqualTo(SituacaoCargo.SEM_CANDIDATOS));
    }

    // ------------------------------------------------------------------ resultado final

    @ParameterizedTest
    @EnumSource(value = StatusEleicao.class, names = {"AGENDADA", "ABERTA"})
    @DisplayName("resultado final antes do encerramento dá BusinessRuleException (422) e nada é gravado")
    void resultadoFinal_antesDoEncerramento_422(StatusEleicao status) {
        when(eleicaoApi.buscar(ELEICAO_ID)).thenReturn(eleicao(status));

        assertThrows(BusinessRuleException.class, () -> apuracaoService.resultadoFinal(ELEICAO_ID, null));
        verify(apuracaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("encerramento: grava Apuracao e um ResultadoCandidato por candidato, com o mais votado marcado")
    void aoEncerrar_gravaResultadoFinal() {
        Eleicao eleicao = eleicao(StatusEleicao.ENCERRADA);
        when(apuracaoRepository.existsByEleicaoId(ELEICAO_ID)).thenReturn(false);
        when(eleicaoApi.buscar(ELEICAO_ID)).thenReturn(eleicao);
        prepararCandidatosEVotos();
        when(apuracaoRepository.save(any(Apuracao.class))).thenAnswer(inv -> inv.getArgument(0));

        apuracaoService.aoEncerrarEleicao(new EleicaoEncerradaEvent(ELEICAO_ID));

        ArgumentCaptor<Apuracao> apuracao = ArgumentCaptor.forClass(Apuracao.class);
        verify(apuracaoRepository).save(apuracao.capture());
        assertThat(apuracao.getValue().getEleicao()).isSameAs(eleicao);
        assertThat(apuracao.getValue().getTotalVotos()).isEqualTo(6L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResultadoCandidato>> linhas = ArgumentCaptor.forClass(List.class);
        verify(resultadoCandidatoRepository).saveAll(linhas.capture());
        assertThat(linhas.getValue()).hasSize(4);
        assertThat(linhas.getValue()).filteredOn(ResultadoCandidato::isMaisVotado)
                .extracting(l -> l.getCandidato().getId())
                .containsExactlyInAnyOrder(12L, 21L, 22L); // Beto (Governador) + empate Caio/Dora (Senador)
    }

    @Test
    @DisplayName("encerramento: se o resultado já foi gravado, não grava de novo")
    void aoEncerrar_jaGravado_naoGravaDeNovo() {
        when(apuracaoRepository.existsByEleicaoId(ELEICAO_ID)).thenReturn(true);

        apuracaoService.aoEncerrarEleicao(new EleicaoEncerradaEvent(ELEICAO_ID));

        verify(apuracaoRepository, never()).save(any());
        verifyNoInteractions(contagemVotosApi);
    }

    @Test
    @DisplayName("resultado final: lê o que foi gravado (não recalcula a partir dos votos)")
    void resultadoFinal_leDoGravado() {
        Eleicao eleicao = eleicao(StatusEleicao.ENCERRADA);
        when(eleicaoApi.buscar(ELEICAO_ID)).thenReturn(eleicao);
        Apuracao apuracao = Apuracao.builder().id(7L).eleicao(eleicao).totalVotos(4L).build();
        when(apuracaoRepository.findByEleicaoId(ELEICAO_ID)).thenReturn(Optional.of(apuracao));
        when(resultadoCandidatoRepository.findByApuracaoId(7L)).thenReturn(List.of(
                linha(apuracao, ANA, 1, "25.00", false),
                linha(apuracao, BETO, 3, "75.00", true)));
        when(candidatoApi.listarPorEleicaoECargo(ELEICAO_ID, Cargo.GOVERNADOR)).thenReturn(List.of(ANA, BETO));

        ApuracaoResponse resposta = apuracaoService.resultadoFinal(ELEICAO_ID, null);

        assertThat(resposta.tipo()).isEqualTo(TipoApuracao.FINAL);
        assertThat(resposta.statusEleicao()).isEqualTo(StatusEleicao.ENCERRADA);
        assertThat(resposta.cargos()).singleElement().satisfies(cargo -> {
            assertThat(cargo.cargo()).isEqualTo(Cargo.GOVERNADOR);
            assertThat(cargo.situacao()).isEqualTo(SituacaoCargo.VENCEDOR);
            assertThat(cargo.vencedor().nome()).isEqualTo("Beto");
            assertThat(cargo.totalVotos()).isEqualTo(4);
        });
        verifyNoInteractions(contagemVotosApi);
        verify(apuracaoRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ helpers

    /** Governador: Ana 1, Beto 3. Senador: Caio 1, Dora 1 (empate). Demais cargos sem candidatos. */
    private void prepararCandidatosEVotos() {
        when(candidatoApi.listarPorEleicaoECargo(eq(ELEICAO_ID), any(Cargo.class))).thenAnswer(inv -> {
            Cargo cargo = inv.getArgument(1);
            return switch (cargo) {
                case GOVERNADOR -> List.of(ANA, BETO);
                case SENADOR -> List.of(CAIO, DORA);
                default -> List.of();
            };
        });
        when(contagemVotosApi.contarPorCargo(ELEICAO_ID, Cargo.GOVERNADOR))
                .thenReturn(List.of(new ContagemCandidato(11L, 1), new ContagemCandidato(12L, 3)));
        when(contagemVotosApi.contarPorCargo(ELEICAO_ID, Cargo.SENADOR))
                .thenReturn(List.of(new ContagemCandidato(21L, 1), new ContagemCandidato(22L, 1)));
    }

    private void logadoComo(Perfil perfil) {
        when(usuarioLogadoProvider.atual()).thenReturn(new UsuarioLogado(USUARIO_ID, perfil));
    }

    private Eleitor eleitor() {
        return Eleitor.builder().id(ELEITOR_ID).tituloEleitor("123456789012").cpf("12345678901").build();
    }

    private static Eleicao eleicao(StatusEleicao status) {
        return Eleicao.builder()
                .id(ELEICAO_ID)
                .data(LocalDate.of(2030, 10, 6))
                .turno(1)
                .horarioAbertura(LocalTime.of(8, 0))
                .horarioEncerramento(LocalTime.of(17, 0))
                .status(status)
                .build();
    }

    private static ResultadoCandidato linha(Apuracao apuracao, Candidato candidato, long votos, String percentual,
                                            boolean maisVotado) {
        return ResultadoCandidato.builder()
                .apuracao(apuracao)
                .candidato(candidato)
                .cargo(candidato.getCargo())
                .votos(votos)
                .percentual(new BigDecimal(percentual))
                .maisVotado(maisVotado)
                .build();
    }

    private static Candidato candidato(Long id, String nome, int numero, Cargo cargo) {
        Usuario usuario = Usuario.builder().nome(nome).login(nome.toLowerCase()).senhaHash("x")
                .perfil(Perfil.ELEITOR).build();
        Eleitor eleitor = Eleitor.builder().id(id + 100).usuario(usuario)
                .tituloEleitor("t" + id).cpf("c" + id).build();
        Partido partido = Partido.builder().id((long) numero).numero(numero).sigla("P" + numero)
                .nome("Partido " + numero).build();
        return Candidato.builder().id(id).eleitor(eleitor).partido(partido).cargo(cargo).numero(numero).build();
    }
}
