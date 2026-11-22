package com.eleicao.cadastro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.eleicao.cadastro.domain.Partido;
import com.eleicao.cadastro.dto.PartidoResponse;
import com.eleicao.cadastro.repository.PartidoRepository;
import com.eleicao.shared.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Teste UNITÁRIO do PartidoService (repositório mockado). */
@ExtendWith(MockitoExtension.class)
class PartidoServiceTest {

    @Mock
    private PartidoRepository partidoRepository;

    @InjectMocks
    private PartidoService partidoService;

    @Test
    @DisplayName("listar: devolve os partidos na ordem do repositório (por número) já convertidos")
    void listar_devolvePartidosEmOrdemDeNumero() {
        when(partidoRepository.findAllByOrderByNumeroAsc()).thenReturn(List.of(
                novoPartido(1L, 10, "REPUBLICANOS"),
                novoPartido(4L, 13, "PT")));

        List<PartidoResponse> resposta = partidoService.listar();

        assertThat(resposta).extracting(PartidoResponse::numero).containsExactly(10, 13);
        assertThat(resposta).extracting(PartidoResponse::sigla).containsExactly("REPUBLICANOS", "PT");
    }

    @Test
    @DisplayName("buscar: devolve o partido quando existe")
    void buscar_quandoExiste_devolvePartido() {
        when(partidoRepository.findById(4L)).thenReturn(Optional.of(novoPartido(4L, 13, "PT")));

        PartidoResponse resposta = partidoService.buscar(4L);

        assertThat(resposta.id()).isEqualTo(4L);
        assertThat(resposta.numero()).isEqualTo(13);
        assertThat(resposta.nome()).isEqualTo("Partido PT");
    }

    @Test
    @DisplayName("buscar: id inexistente dá NotFoundException (404)")
    void buscar_quandoNaoExiste_lancaNaoEncontrado() {
        when(partidoRepository.findById(999L)).thenReturn(Optional.empty());

        NotFoundException erro = assertThrows(NotFoundException.class, () -> partidoService.buscar(999L));

        assertThat(erro.getMessage()).contains("Partido");
    }

    private Partido novoPartido(Long id, int numero, String sigla) {
        return Partido.builder().id(id).numero(numero).sigla(sigla).nome("Partido " + sigla).build();
    }
}
