package com.eleicao.eleicao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.eleicao.repository.EleicaoRepository;
import com.eleicao.shared.domain.StatusEleicao;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.NotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Contrato que os módulos cadastro, votação e apuração usam para checar o status da eleição. */
@ExtendWith(MockitoExtension.class)
class EleicaoApiImplTest {

    @Mock
    private EleicaoRepository eleicaoRepository;

    @InjectMocks
    private EleicaoApiImpl eleicaoApi;

    @Test
    @DisplayName("buscar: inexistente dá NotFoundException (404)")
    void buscar_inexistente_404() {
        when(eleicaoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eleicaoApi.buscar(9L));
    }

    @Test
    @DisplayName("exigirAgendada: devolve a eleição AGENDADA; outro status dá 422")
    void exigirAgendada() {
        when(eleicaoRepository.findById(1L)).thenReturn(Optional.of(eleicao(1L, StatusEleicao.AGENDADA)));
        when(eleicaoRepository.findById(2L)).thenReturn(Optional.of(eleicao(2L, StatusEleicao.ABERTA)));

        assertThat(eleicaoApi.exigirAgendada(1L).getId()).isEqualTo(1L);
        assertThrows(BusinessRuleException.class, () -> eleicaoApi.exigirAgendada(2L));
    }

    @Test
    @DisplayName("exigirAberta: devolve a eleição ABERTA; AGENDADA ou ENCERRADA dá 422")
    void exigirAberta() {
        when(eleicaoRepository.findById(1L)).thenReturn(Optional.of(eleicao(1L, StatusEleicao.ABERTA)));
        when(eleicaoRepository.findById(2L)).thenReturn(Optional.of(eleicao(2L, StatusEleicao.AGENDADA)));
        when(eleicaoRepository.findById(3L)).thenReturn(Optional.of(eleicao(3L, StatusEleicao.ENCERRADA)));

        assertThat(eleicaoApi.exigirAberta(1L).getId()).isEqualTo(1L);
        assertThrows(BusinessRuleException.class, () -> eleicaoApi.exigirAberta(2L));
        assertThrows(BusinessRuleException.class, () -> eleicaoApi.exigirAberta(3L));
    }

    private Eleicao eleicao(Long id, StatusEleicao status) {
        return Eleicao.builder()
                .id(id)
                .data(LocalDate.of(2030, 10, 6))
                .turno(1)
                .horarioAbertura(LocalTime.of(8, 0))
                .horarioEncerramento(LocalTime.of(17, 0))
                .status(status)
                .build();
    }
}
