package com.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.eleicao.auth.domain.Usuario;
import com.eleicao.cadastro.api.CandidatoApi;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.domain.Partido;
import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.votacao.api.ContagemCandidato;
import com.eleicao.votacao.repository.VotoRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContagemVotosServiceTest {

    @Mock
    private VotoRepository votoRepository;
    @Mock
    private CandidatoApi candidatoApi;

    @InjectMocks
    private ContagemVotosService contagemVotosService;

    @Test
    @DisplayName("contarPorCargo: inclui candidatos sem votos, com zero")
    void contarPorCargo_incluiCandidatoSemVotos() {
        when(votoRepository.contarPorCandidato(1L, Cargo.GOVERNADOR))
                .thenReturn(List.<Object[]>of(new Object[] {5L, 3L}));
        when(candidatoApi.listarPorEleicaoECargo(1L, Cargo.GOVERNADOR))
                .thenReturn(List.of(candidato(5L), candidato(6L)));

        List<ContagemCandidato> contagem = contagemVotosService.contarPorCargo(1L, Cargo.GOVERNADOR);

        assertThat(contagem).containsExactly(
                new ContagemCandidato(5L, 3L),
                new ContagemCandidato(6L, 0L));
    }

    @Test
    @DisplayName("contarPorCargo: sem nenhum voto, todos os candidatos aparecem com zero")
    void contarPorCargo_semVotos_todosZerados() {
        when(votoRepository.contarPorCandidato(1L, Cargo.SENADOR)).thenReturn(List.of());
        when(candidatoApi.listarPorEleicaoECargo(1L, Cargo.SENADOR)).thenReturn(List.of(candidato(7L)));

        List<ContagemCandidato> contagem = contagemVotosService.contarPorCargo(1L, Cargo.SENADOR);

        assertThat(contagem).containsExactly(new ContagemCandidato(7L, 0L));
    }

    private Candidato candidato(Long id) {
        Usuario usuario = Usuario.builder().nome("Candidato " + id).login("c" + id).senhaHash("hash")
                .perfil(Perfil.ELEITOR).build();
        Eleitor eleitor = Eleitor.builder().id(id).usuario(usuario)
                .tituloEleitor("12345678901" + id % 10).cpf("1234567890" + id % 10).build();
        Eleicao eleicao = Eleicao.builder().id(1L).data(LocalDate.of(2026, 12, 1)).turno(1)
                .horarioAbertura(LocalTime.of(8, 0)).horarioEncerramento(LocalTime.of(17, 0)).build();
        Partido partido = Partido.builder().id(4L).numero(13).sigla("PT").nome("Partido PT").build();
        return Candidato.builder().id(id).eleitor(eleitor).eleicao(eleicao).partido(partido)
                .cargo(Cargo.GOVERNADOR).numero(13).build();
    }
}
