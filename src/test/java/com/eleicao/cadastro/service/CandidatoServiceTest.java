package com.eleicao.cadastro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.eleicao.auth.domain.Usuario;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.domain.Partido;
import com.eleicao.cadastro.dto.CandidatoRequest;
import com.eleicao.cadastro.dto.CandidatoResponse;
import com.eleicao.cadastro.dto.CandidatoUpdateRequest;
import com.eleicao.cadastro.repository.CandidatoRepository;
import com.eleicao.cadastro.repository.EleitorRepository;
import com.eleicao.cadastro.repository.PartidoRepository;
import com.eleicao.eleicao.api.EleicaoApi;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.shared.exception.NotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidatoServiceTest {

    private static final Long ELEICAO_ID = 1L;
    private static final Long ELEITOR_ID = 1L;
    private static final Long PARTIDO_ID = 4L;

    @Mock
    private CandidatoRepository candidatoRepository;
    @Mock
    private EleitorRepository eleitorRepository;
    @Mock
    private PartidoRepository partidoRepository;
    @Mock
    private EleicaoApi eleicaoApi;

    @InjectMocks
    private CandidatoService candidatoService;

    @Test
    @DisplayName("criar: com dados válidos grava o candidato e devolve a resposta")
    void criar_comDadosValidos_gravaCandidato() {
        prepararCriacaoValida();
        when(candidatoRepository.save(any(Candidato.class))).thenAnswer(invocacao -> {
            Candidato salvo = invocacao.getArgument(0);
            salvo.setId(7L);
            return salvo;
        });

        CandidatoResponse resposta = candidatoService.criar(requestCriar(13));

        assertThat(resposta.id()).isEqualTo(7L);
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.partidoSigla()).isEqualTo("PT");
        assertThat(resposta.partidoNumero()).isEqualTo(13);
        assertThat(resposta.cargo()).isEqualTo(Cargo.GOVERNADOR);
        assertThat(resposta.numero()).isEqualTo(13);
        assertThat(resposta.eleicaoId()).isEqualTo(ELEICAO_ID);
    }

    @ParameterizedTest(name = "número {0} começa com 13 → aceito")
    @ValueSource(ints = {13, 131, 1399, 13456})
    @DisplayName("criar: aceita qualquer número que comece com o número do partido")
    void criar_comNumeroComPrefixoDoPartido_aceita(int numero) {
        prepararCriacaoValida();
        when(candidatoRepository.save(any(Candidato.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        CandidatoResponse resposta = candidatoService.criar(requestCriar(numero));

        assertThat(resposta.numero()).isEqualTo(numero);
    }

    @ParameterizedTest(name = "número {0} não começa com 13 → recusado")
    @ValueSource(ints = {1, 14, 31, 113, 99999})
    @DisplayName("criar: número fora do prefixo do partido dá BusinessRuleException (422)")
    void criar_comNumeroForaDoPrefixoDoPartido_lancaRegraDeNegocio(int numero) {
        prepararCriacaoValida();

        BusinessRuleException erro = assertThrows(BusinessRuleException.class,
                () -> candidatoService.criar(requestCriar(numero)));

        assertThat(erro.getMessage()).contains("13");
        verify(candidatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: eleição que não está AGENDADA dá BusinessRuleException (422) e nem consulta o resto")
    void criar_comEleicaoNaoAgendada_lancaRegraDeNegocio() {
        when(eleicaoApi.exigirAgendada(ELEICAO_ID))
                .thenThrow(new BusinessRuleException("A eleição não está agendada"));

        assertThrows(BusinessRuleException.class, () -> candidatoService.criar(requestCriar(13)));
        verifyNoInteractions(eleitorRepository, partidoRepository);
        verify(candidatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: eleição inexistente dá NotFoundException (404)")
    void criar_comEleicaoInexistente_lancaNaoEncontrado() {
        when(eleicaoApi.exigirAgendada(ELEICAO_ID)).thenThrow(NotFoundException.de("Eleição", ELEICAO_ID));

        assertThrows(NotFoundException.class, () -> candidatoService.criar(requestCriar(13)));

        verify(candidatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: eleitor inexistente dá NotFoundException (404)")
    void criar_comEleitorInexistente_lancaNaoEncontrado() {
        when(eleicaoApi.exigirAgendada(ELEICAO_ID)).thenReturn(novaEleicao());
        when(eleitorRepository.findById(ELEITOR_ID)).thenReturn(Optional.empty());

        NotFoundException erro = assertThrows(NotFoundException.class,
                () -> candidatoService.criar(requestCriar(13)));

        assertThat(erro.getMessage()).contains("Eleitor");
        verify(candidatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: partido inexistente dá NotFoundException (404)")
    void criar_comPartidoInexistente_lancaNaoEncontrado() {
        when(eleicaoApi.exigirAgendada(ELEICAO_ID)).thenReturn(novaEleicao());
        when(eleitorRepository.findById(ELEITOR_ID)).thenReturn(Optional.of(novoEleitor()));
        when(partidoRepository.findById(PARTIDO_ID)).thenReturn(Optional.empty());

        NotFoundException erro = assertThrows(NotFoundException.class,
                () -> candidatoService.criar(requestCriar(13)));

        assertThat(erro.getMessage()).contains("Partido");
        verify(candidatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: eleitor que já é candidato nesta eleição dá ConflictException (409)")
    void criar_comEleitorJaCandidato_lancaConflito() {
        prepararCriacaoValida();
        when(candidatoRepository.existsByEleitorIdAndEleicaoId(ELEITOR_ID, ELEICAO_ID)).thenReturn(true);

        ConflictException erro = assertThrows(ConflictException.class,
                () -> candidatoService.criar(requestCriar(13)));

        assertThat(erro.getMessage()).contains("já é candidato");
        verify(candidatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: número já usado por outro candidato no mesmo cargo dá ConflictException (409)")
    void criar_comNumeroEmUso_lancaConflito() {
        prepararCriacaoValida();
        Candidato outro = novoCandidato(99L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        when(candidatoRepository.findByEleicaoIdAndCargoAndNumero(ELEICAO_ID, Cargo.GOVERNADOR, 13))
                .thenReturn(Optional.of(outro));

        ConflictException erro = assertThrows(ConflictException.class,
                () -> candidatoService.criar(requestCriar(13)));

        assertThat(erro.getMessage()).contains("já está em uso");
        verify(candidatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("buscar: devolve o candidato quando existe")
    void buscar_quandoExiste_devolveCandidato() {
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));

        CandidatoResponse resposta = candidatoService.buscar(7L);

        assertThat(resposta.id()).isEqualTo(7L);
        assertThat(resposta.partidoSigla()).isEqualTo("PT");
    }

    @Test
    @DisplayName("buscar: id inexistente dá NotFoundException (404)")
    void buscar_quandoNaoExiste_lancaNaoEncontrado() {
        when(candidatoRepository.findDetalhadoById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> candidatoService.buscar(999L));
    }

    @Test
    @DisplayName("listar: com eleição e cargo usa a consulta filtrada pelos dois")
    void listar_comEleicaoECargo_usaFiltroDosDois() {
        when(candidatoRepository.findByEleicaoIdAndCargoOrderByNumeroAsc(ELEICAO_ID, Cargo.GOVERNADOR))
                .thenReturn(List.of(candidatoPadrao()));

        List<CandidatoResponse> resposta = candidatoService.listar(ELEICAO_ID, Cargo.GOVERNADOR);

        assertThat(resposta).hasSize(1);
        verify(candidatoRepository).findByEleicaoIdAndCargoOrderByNumeroAsc(ELEICAO_ID, Cargo.GOVERNADOR);
        verifyNoMoreInteractions(candidatoRepository);
    }

    @Test
    @DisplayName("listar: só com eleição usa a consulta por eleição")
    void listar_soComEleicao_usaFiltroPorEleicao() {
        when(candidatoRepository.findByEleicaoIdOrderByCargoAscNumeroAsc(ELEICAO_ID))
                .thenReturn(List.of(candidatoPadrao()));

        List<CandidatoResponse> resposta = candidatoService.listar(ELEICAO_ID, null);

        assertThat(resposta).hasSize(1);
        verify(candidatoRepository).findByEleicaoIdOrderByCargoAscNumeroAsc(ELEICAO_ID);
        verifyNoMoreInteractions(candidatoRepository);
    }

    @Test
    @DisplayName("listar: só com cargo usa a consulta por cargo")
    void listar_soComCargo_usaFiltroPorCargo() {
        when(candidatoRepository.findByCargoOrderByEleicaoIdAscNumeroAsc(Cargo.GOVERNADOR))
                .thenReturn(List.of(candidatoPadrao()));

        List<CandidatoResponse> resposta = candidatoService.listar(null, Cargo.GOVERNADOR);

        assertThat(resposta).hasSize(1);
        verify(candidatoRepository).findByCargoOrderByEleicaoIdAscNumeroAsc(Cargo.GOVERNADOR);
        verifyNoMoreInteractions(candidatoRepository);
    }

    @Test
    @DisplayName("listar: sem filtros devolve todos")
    void listar_semFiltros_devolveTodos() {
        when(candidatoRepository.findAllByOrderByEleicaoIdAscCargoAscNumeroAsc())
                .thenReturn(List.of(candidatoPadrao(), candidatoPadrao()));

        List<CandidatoResponse> resposta = candidatoService.listar(null, null);

        assertThat(resposta).hasSize(2);
        verify(candidatoRepository).findAllByOrderByEleicaoIdAscCargoAscNumeroAsc();
        verifyNoMoreInteractions(candidatoRepository);
    }

    @Test
    @DisplayName("atualizar: troca partido, cargo e número quando tudo é válido")
    void atualizar_comDadosValidos_alteraCandidato() {
        // Candidato começa no REPUBLICANOS (10) com o número 10 e vai para o PT (13) com o número 131
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(),
                novoPartido(1L, 10, "REPUBLICANOS"), Cargo.GOVERNADOR, 10);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));
        when(partidoRepository.findById(PARTIDO_ID)).thenReturn(Optional.of(novoPartidoPt()));

        CandidatoResponse resposta = candidatoService.atualizar(7L,
                new CandidatoUpdateRequest(PARTIDO_ID, Cargo.GOVERNADOR, 131));

        assertThat(resposta.partidoSigla()).isEqualTo("PT");
        assertThat(resposta.numero()).isEqualTo(131);
        verify(eleicaoApi).exigirAgendada(ELEICAO_ID);
    }

    @Test
    @DisplayName("atualizar: manter o PRÓPRIO número não é conflito")
    void atualizar_mantendoOProprioNumero_naoLancaConflito() {
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));
        when(partidoRepository.findById(PARTIDO_ID)).thenReturn(Optional.of(novoPartidoPt()));
        when(candidatoRepository.findByEleicaoIdAndCargoAndNumero(ELEICAO_ID, Cargo.GOVERNADOR, 13))
                .thenReturn(Optional.of(candidato));

        CandidatoResponse resposta = candidatoService.atualizar(7L,
                new CandidatoUpdateRequest(PARTIDO_ID, Cargo.GOVERNADOR, 13));

        assertThat(resposta.numero()).isEqualTo(13);
    }

    @Test
    @DisplayName("atualizar: número de OUTRO candidato dá ConflictException (409)")
    void atualizar_comNumeroDeOutroCandidato_lancaConflito() {
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        Candidato outro = novoCandidato(8L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 131);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));
        when(partidoRepository.findById(PARTIDO_ID)).thenReturn(Optional.of(novoPartidoPt()));
        when(candidatoRepository.findByEleicaoIdAndCargoAndNumero(ELEICAO_ID, Cargo.GOVERNADOR, 131))
                .thenReturn(Optional.of(outro));

        assertThrows(ConflictException.class, () -> candidatoService.atualizar(7L,
                new CandidatoUpdateRequest(PARTIDO_ID, Cargo.GOVERNADOR, 131)));

        assertThat(candidato.getNumero()).isEqualTo(13);
    }

    @Test
    @DisplayName("atualizar: número fora do prefixo do partido dá BusinessRuleException (422)")
    void atualizar_comNumeroForaDoPrefixo_lancaRegraDeNegocio() {
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));
        when(partidoRepository.findById(PARTIDO_ID)).thenReturn(Optional.of(novoPartidoPt()));

        assertThrows(BusinessRuleException.class, () -> candidatoService.atualizar(7L,
                new CandidatoUpdateRequest(PARTIDO_ID, Cargo.GOVERNADOR, 99)));

        assertThat(candidato.getNumero()).isEqualTo(13);
    }

    @Test
    @DisplayName("atualizar: eleição que não está AGENDADA dá BusinessRuleException (422)")
    void atualizar_comEleicaoNaoAgendada_lancaRegraDeNegocio() {
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));
        when(eleicaoApi.exigirAgendada(ELEICAO_ID))
                .thenThrow(new BusinessRuleException("A eleição não está agendada"));

        assertThrows(BusinessRuleException.class, () -> candidatoService.atualizar(7L,
                new CandidatoUpdateRequest(PARTIDO_ID, Cargo.GOVERNADOR, 131)));

        verifyNoInteractions(partidoRepository);
        assertThat(candidato.getNumero()).isEqualTo(13);
    }

    @Test
    @DisplayName("atualizar: candidato inexistente dá NotFoundException (404)")
    void atualizar_quandoNaoExiste_lancaNaoEncontrado() {
        when(candidatoRepository.findDetalhadoById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> candidatoService.atualizar(999L,
                new CandidatoUpdateRequest(PARTIDO_ID, Cargo.GOVERNADOR, 13)));

        verifyNoInteractions(eleicaoApi);
    }

    @Test
    @DisplayName("excluir: com eleição AGENDADA remove o candidato")
    void excluir_comEleicaoAgendada_removeCandidato() {
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));

        candidatoService.excluir(7L);

        verify(eleicaoApi).exigirAgendada(ELEICAO_ID);
        verify(candidatoRepository).delete(candidato);
    }

    @Test
    @DisplayName("excluir: eleição que não está AGENDADA dá BusinessRuleException (422) e não apaga")
    void excluir_comEleicaoNaoAgendada_lancaRegraDeNegocio() {
        Candidato candidato = novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
        when(candidatoRepository.findDetalhadoById(7L)).thenReturn(Optional.of(candidato));
        when(eleicaoApi.exigirAgendada(ELEICAO_ID))
                .thenThrow(new BusinessRuleException("A eleição não está agendada"));

        assertThrows(BusinessRuleException.class, () -> candidatoService.excluir(7L));

        verify(candidatoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("excluir: candidato inexistente dá NotFoundException (404)")
    void excluir_quandoNaoExiste_lancaNaoEncontrado() {
        when(candidatoRepository.findDetalhadoById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> candidatoService.excluir(999L));

        verify(candidatoRepository, never()).delete(any());
    }

    private void prepararCriacaoValida() {
        when(eleicaoApi.exigirAgendada(ELEICAO_ID)).thenReturn(novaEleicao());
        when(eleitorRepository.findById(ELEITOR_ID)).thenReturn(Optional.of(novoEleitor()));
        when(partidoRepository.findById(PARTIDO_ID)).thenReturn(Optional.of(novoPartidoPt()));
    }

    private CandidatoRequest requestCriar(int numero) {
        return new CandidatoRequest(ELEITOR_ID, ELEICAO_ID, PARTIDO_ID, Cargo.GOVERNADOR, numero);
    }

    private Candidato candidatoPadrao() {
        return novoCandidato(7L, novoEleitor(), novaEleicao(), novoPartidoPt(), Cargo.GOVERNADOR, 13);
    }

    private Eleicao novaEleicao() {
        return Eleicao.builder()
                .id(ELEICAO_ID)
                .data(LocalDate.of(2026, 12, 1))
                .turno(1)
                .horarioAbertura(LocalTime.of(8, 0))
                .horarioEncerramento(LocalTime.of(17, 0))
                .build();
    }

    private Eleitor novoEleitor() {
        Usuario usuario = Usuario.builder()
                .nome("Maria Silva")
                .login("maria")
                .senhaHash("hash")
                .perfil(Perfil.ELEITOR)
                .build();
        usuario.setId(10L);
        return Eleitor.builder()
                .id(ELEITOR_ID)
                .usuario(usuario)
                .tituloEleitor("123456789012")
                .cpf("12345678901")
                .build();
    }

    private Partido novoPartidoPt() {
        return novoPartido(PARTIDO_ID, 13, "PT");
    }

    private Partido novoPartido(Long id, int numero, String sigla) {
        return Partido.builder().id(id).numero(numero).sigla(sigla).nome("Partido " + sigla).build();
    }

    private Candidato novoCandidato(Long id, Eleitor eleitor, Eleicao eleicao, Partido partido,
                                    Cargo cargo, int numero) {
        return Candidato.builder()
                .id(id)
                .eleitor(eleitor)
                .eleicao(eleicao)
                .partido(partido)
                .cargo(cargo)
                .numero(numero)
                .build();
    }
}
