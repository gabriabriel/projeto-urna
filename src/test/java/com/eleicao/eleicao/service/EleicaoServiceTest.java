package com.eleicao.eleicao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.eleicao.api.EleicaoEncerradaEvent;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.eleicao.dto.EleicaoRequest;
import com.eleicao.eleicao.dto.EleicaoResponse;
import com.eleicao.eleicao.dto.SituacaoEleicaoResponse;
import com.eleicao.eleicao.repository.EleicaoRepository;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.StatusEleicao;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class EleicaoServiceTest {

    private static final Long ID = 1L;
    private static final LocalDate DATA = LocalDate.now().plusDays(10);
    private static final LocalTime ABERTURA = LocalTime.of(8, 0);
    private static final LocalTime ENCERRAMENTO = LocalTime.of(17, 0);

    @Mock
    private EleicaoRepository eleicaoRepository;
    @Mock
    private CandidatoApi candidatoApi;
    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private EleicaoService eleicaoService;

    // ------------------------------------------------------------------ criar

    @Test
    @DisplayName("criar: eleição válida nasce AGENDADA")
    void criar_valida_nasceAgendada() {
        when(eleicaoRepository.existsByDataAndTurno(DATA, 1)).thenReturn(false);
        when(eleicaoRepository.save(any(Eleicao.class))).thenAnswer(inv -> inv.getArgument(0));

        EleicaoResponse resposta = eleicaoService.criar(pedido(ABERTURA, ENCERRAMENTO));

        assertThat(resposta.status()).isEqualTo(StatusEleicao.AGENDADA);
        assertThat(resposta.data()).isEqualTo(DATA);
        assertThat(resposta.turno()).isEqualTo(1);
        assertThat(resposta.abertaEm()).isNull();
        assertThat(resposta.encerradaEm()).isNull();
    }

    @Test
    @DisplayName("criar: encerramento antes (ou igual) da abertura dá BusinessRuleException (422) e não grava")
    void criar_horariosInvertidos_422() {
        assertThrows(BusinessRuleException.class, () -> eleicaoService.criar(pedido(ENCERRAMENTO, ABERTURA)));
        assertThrows(BusinessRuleException.class, () -> eleicaoService.criar(pedido(ABERTURA, ABERTURA)));

        verify(eleicaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: já existe eleição na mesma data e turno dá ConflictException (409)")
    void criar_dataETurnoRepetidos_409() {
        when(eleicaoRepository.existsByDataAndTurno(DATA, 1)).thenReturn(true);

        assertThrows(ConflictException.class, () -> eleicaoService.criar(pedido(ABERTURA, ENCERRAMENTO)));
        verify(eleicaoRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ consultar

    @Test
    @DisplayName("buscar: eleição inexistente dá NotFoundException (404)")
    void buscar_inexistente_404() {
        when(eleicaoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eleicaoService.buscar(99L));
    }

    @Test
    @DisplayName("listar: com status usa o filtro; sem status lista todas")
    void listar_comESemFiltro() {
        when(eleicaoRepository.findByStatusOrderByDataDescTurnoAsc(StatusEleicao.ABERTA))
                .thenReturn(List.of(eleicao(StatusEleicao.ABERTA)));
        when(eleicaoRepository.findAllByOrderByDataDescTurnoAsc())
                .thenReturn(List.of(eleicao(StatusEleicao.ABERTA), eleicao(StatusEleicao.AGENDADA)));

        assertThat(eleicaoService.listar(StatusEleicao.ABERTA)).hasSize(1);
        assertThat(eleicaoService.listar(null)).hasSize(2);
    }

    @Test
    @DisplayName("situacao: devolve status e candidatos por cargo")
    void situacao_contaCandidatosPorCargo() {
        when(eleicaoRepository.findById(ID)).thenReturn(Optional.of(eleicao(StatusEleicao.AGENDADA)));
        prepararCandidatos(2, 1);

        SituacaoEleicaoResponse situacao = eleicaoService.situacao(ID);

        assertThat(situacao.status()).isEqualTo(StatusEleicao.AGENDADA);
        assertThat(situacao.totalCandidatos()).isEqualTo(3);
        assertThat(situacao.candidatosPorCargo())
                .containsEntry(Cargo.GOVERNADOR, 2)
                .containsEntry(Cargo.SENADOR, 1)
                .containsEntry(Cargo.PRESIDENTE, 0);
    }

    // ------------------------------------------------------------------ atualizar / excluir

    @Test
    @DisplayName("atualizar: eleição AGENDADA tem data, turno e horários alterados")
    void atualizar_agendada_altera() {
        Eleicao eleicao = eleicao(StatusEleicao.AGENDADA);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));
        when(eleicaoRepository.existsByDataAndTurnoAndIdNot(DATA, 2, ID)).thenReturn(false);

        EleicaoResponse resposta = eleicaoService.atualizar(ID,
                new EleicaoRequest(DATA, 2, LocalTime.of(7, 0), LocalTime.of(18, 0)));

        assertThat(resposta.turno()).isEqualTo(2);
        assertThat(eleicao.getHorarioAbertura()).isEqualTo(LocalTime.of(7, 0));
        assertThat(eleicao.getHorarioEncerramento()).isEqualTo(LocalTime.of(18, 0));
    }

    @ParameterizedTest
    @EnumSource(value = StatusEleicao.class, names = {"ABERTA", "ENCERRADA"})
    @DisplayName("atualizar: eleição fora de AGENDADA dá BusinessRuleException (422)")
    void atualizar_foraDeAgendada_422(StatusEleicao status) {
        Eleicao eleicao = eleicao(status);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));

        assertThrows(BusinessRuleException.class,
                () -> eleicaoService.atualizar(ID, new EleicaoRequest(DATA, 2, ABERTURA, ENCERRAMENTO)));
        assertThat(eleicao.getTurno()).isEqualTo(1);
    }

    @Test
    @DisplayName("atualizar: data e turno de outra eleição dá ConflictException (409)")
    void atualizar_dataETurnoDeOutra_409() {
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao(StatusEleicao.AGENDADA)));
        when(eleicaoRepository.existsByDataAndTurnoAndIdNot(DATA, 2, ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> eleicaoService.atualizar(ID, new EleicaoRequest(DATA, 2, ABERTURA, ENCERRAMENTO)));
    }

    @Test
    @DisplayName("excluir: eleição AGENDADA e sem candidatos é excluída")
    void excluir_agendadaSemCandidatos_exclui() {
        Eleicao eleicao = eleicao(StatusEleicao.AGENDADA);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));
        prepararCandidatos(0, 0);

        eleicaoService.excluir(ID);

        verify(eleicaoRepository).delete(eleicao);
        verify(eleicaoRepository).flush();
    }

    @ParameterizedTest
    @EnumSource(value = StatusEleicao.class, names = {"ABERTA", "ENCERRADA"})
    @DisplayName("excluir: eleição fora de AGENDADA dá BusinessRuleException (422)")
    void excluir_foraDeAgendada_422(StatusEleicao status) {
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao(status)));

        assertThrows(BusinessRuleException.class, () -> eleicaoService.excluir(ID));
        verify(eleicaoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("excluir: eleição com candidatos dá ConflictException (409)")
    void excluir_comCandidatos_409() {
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao(StatusEleicao.AGENDADA)));
        prepararCandidatos(1, 0);

        assertThrows(ConflictException.class, () -> eleicaoService.excluir(ID));
        verify(eleicaoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("excluir: a FK do banco barra (registro vinculado) e vira ConflictException (409)")
    void excluir_fkDoBanco_409() {
        Eleicao eleicao = eleicao(StatusEleicao.AGENDADA);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));
        prepararCandidatos(0, 0);
        doThrow(new DataIntegrityViolationException("fk")).when(eleicaoRepository).flush();

        assertThrows(ConflictException.class, () -> eleicaoService.excluir(ID));
    }

    // ------------------------------------------------------------------ abrir

    @Test
    @DisplayName("abrir: AGENDADA com 2 candidatos vira ABERTA e registra abertaEm")
    void abrir_comDoisCandidatos_abre() {
        Eleicao eleicao = eleicao(StatusEleicao.AGENDADA);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));
        prepararCandidatos(2, 0);

        EleicaoResponse resposta = eleicaoService.abrir(ID);

        assertThat(resposta.status()).isEqualTo(StatusEleicao.ABERTA);
        assertThat(eleicao.getStatus()).isEqualTo(StatusEleicao.ABERTA);
        assertThat(eleicao.getAbertaEm()).isNotNull();
    }

    @Test
    @DisplayName("abrir: sem candidatos dá BusinessRuleException (422) e continua AGENDADA")
    void abrir_semCandidatos_422() {
        Eleicao eleicao = eleicao(StatusEleicao.AGENDADA);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));
        prepararCandidatos(0, 0);

        assertThrows(BusinessRuleException.class, () -> eleicaoService.abrir(ID));
        assertThat(eleicao.getStatus()).isEqualTo(StatusEleicao.AGENDADA);
        assertThat(eleicao.getAbertaEm()).isNull();
    }

    @Test
    @DisplayName("abrir: com 1 candidato só dá BusinessRuleException (422)")
    void abrir_comUmCandidato_422() {
        Eleicao eleicao = eleicao(StatusEleicao.AGENDADA);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));
        prepararCandidatos(1, 0);

        assertThrows(BusinessRuleException.class, () -> eleicaoService.abrir(ID));
        assertThat(eleicao.getStatus()).isEqualTo(StatusEleicao.AGENDADA);
    }

    @ParameterizedTest
    @EnumSource(value = StatusEleicao.class, names = {"ABERTA", "ENCERRADA"})
    @DisplayName("abrir: eleição que não está AGENDADA dá BusinessRuleException (422)")
    void abrir_foraDeAgendada_422(StatusEleicao status) {
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao(status)));

        assertThrows(BusinessRuleException.class, () -> eleicaoService.abrir(ID));
    }

    @Test
    @DisplayName("abrir: eleição inexistente dá NotFoundException (404)")
    void abrir_inexistente_404() {
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eleicaoService.abrir(ID));
    }

    // ------------------------------------------------------------------ encerrar

    @Test
    @DisplayName("encerrar: ABERTA vira ENCERRADA, registra encerradaEm e publica o evento para a apuração")
    void encerrar_aberta_encerraEPublicaEvento() {
        Eleicao eleicao = eleicao(StatusEleicao.ABERTA);
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao));

        EleicaoResponse resposta = eleicaoService.encerrar(ID);

        assertThat(resposta.status()).isEqualTo(StatusEleicao.ENCERRADA);
        assertThat(eleicao.getEncerradaEm()).isNotNull();
        ArgumentCaptor<EleicaoEncerradaEvent> evento = ArgumentCaptor.forClass(EleicaoEncerradaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().eleicaoId()).isEqualTo(ID);
    }

    @ParameterizedTest
    @EnumSource(value = StatusEleicao.class, names = {"AGENDADA", "ENCERRADA"})
    @DisplayName("encerrar: eleição que não está ABERTA dá BusinessRuleException (422) e não publica evento")
    void encerrar_foraDeAberta_422(StatusEleicao status) {
        when(eleicaoRepository.buscarParaAlterar(ID)).thenReturn(Optional.of(eleicao(status)));

        assertThrows(BusinessRuleException.class, () -> eleicaoService.encerrar(ID));
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------------ helpers

    private EleicaoRequest pedido(LocalTime abertura, LocalTime encerramento) {
        return new EleicaoRequest(DATA, 1, abertura, encerramento);
    }

    private Eleicao eleicao(StatusEleicao status) {
        return Eleicao.builder()
                .id(ID)
                .data(DATA)
                .turno(1)
                .horarioAbertura(ABERTURA)
                .horarioEncerramento(ENCERRAMENTO)
                .status(status)
                .build();
    }

    /** Governadores e senadores cadastrados na eleição; os demais cargos ficam vazios. */
    private void prepararCandidatos(int governadores, int senadores) {
        for (Cargo cargo : Cargo.values()) {
            int quantidade = cargo == Cargo.GOVERNADOR ? governadores : cargo == Cargo.SENADOR ? senadores : 0;
            List<Candidato> candidatos = java.util.stream.IntStream.range(0, quantidade)
                    .mapToObj(i -> Candidato.builder().id((long) (i + 1)).cargo(cargo).build())
                    .toList();
            when(candidatoApi.listarPorEleicaoECargo(eq(ID), eq(cargo))).thenReturn(candidatos);
        }
    }
}
