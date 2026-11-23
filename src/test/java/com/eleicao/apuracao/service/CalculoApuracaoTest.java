package com.eleicao.apuracao.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.eleicao.apuracao.domain.SituacaoCargo;
import com.eleicao.apuracao.dto.ResultadoCandidatoResponse;
import com.eleicao.apuracao.dto.ResultadoCargoResponse;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.domain.Partido;
import com.eleicao.shared.domain.Cargo;
import com.eleicao.shared.domain.Perfil;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cálculo puro: percentuais, mais votado, empate e cargo sem votos. */
class CalculoApuracaoTest {

    private static final Candidato ANA = candidato(1L, "Ana", 13);
    private static final Candidato BETO = candidato(2L, "Beto", 22);
    private static final Candidato CAIO = candidato(3L, "Caio", 15);

    @Test
    @DisplayName("vencedor único: percentuais com 2 casas e só ele marcado como mais votado")
    void vencedorUnico() {
        ResultadoCargoResponse r = ResultadoCargoResponse.de(Cargo.GOVERNADOR,
                CalculoApuracao.calcular(List.of(ANA, BETO, CAIO), Map.of(1L, 2L, 2L, 1L)));

        assertThat(r.situacao()).isEqualTo(SituacaoCargo.VENCEDOR);
        assertThat(r.totalVotos()).isEqualTo(3);
        assertThat(r.vencedor().nome()).isEqualTo("Ana");
        assertThat(r.empatados()).isEmpty();
        // ordem: mais votado primeiro; quem não aparece no mapa tem 0 votos
        assertThat(r.candidatos()).extracting(ResultadoCandidatoResponse::nome).containsExactly("Ana", "Beto", "Caio");
        assertThat(r.candidatos()).extracting(ResultadoCandidatoResponse::percentual)
                .containsExactly(new BigDecimal("66.67"), new BigDecimal("33.33"), new BigDecimal("0.00"));
        assertThat(r.candidatos()).extracting(ResultadoCandidatoResponse::maisVotado).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("empate: os dois mais votados aparecem em empatados e não há vencedor")
    void empate() {
        ResultadoCargoResponse r = ResultadoCargoResponse.de(Cargo.SENADOR,
                CalculoApuracao.calcular(List.of(ANA, BETO, CAIO), Map.of(1L, 3L, 2L, 3L, 3L, 1L)));

        assertThat(r.situacao()).isEqualTo(SituacaoCargo.EMPATE);
        assertThat(r.vencedor()).isNull();
        assertThat(r.empatados()).extracting(ResultadoCandidatoResponse::nome).containsExactly("Ana", "Beto");
        assertThat(r.candidatos().get(0).percentual()).isEqualByComparingTo("42.86");
    }

    @Test
    @DisplayName("sem votos: todos com 0.00%, ninguém mais votado")
    void semVotos() {
        ResultadoCargoResponse r = ResultadoCargoResponse.de(Cargo.PRESIDENTE,
                CalculoApuracao.calcular(List.of(ANA, BETO), Map.of()));

        assertThat(r.situacao()).isEqualTo(SituacaoCargo.SEM_VOTOS);
        assertThat(r.vencedor()).isNull();
        assertThat(r.candidatos()).allSatisfy(c -> {
            assertThat(c.maisVotado()).isFalse();
            assertThat(c.percentual()).isEqualByComparingTo("0");
        });
    }

    @Test
    @DisplayName("sem candidatos: situação SEM_CANDIDATOS")
    void semCandidatos() {
        ResultadoCargoResponse r = ResultadoCargoResponse.de(Cargo.DEPUTADO_FEDERAL,
                CalculoApuracao.calcular(List.of(), Map.of()));

        assertThat(r.situacao()).isEqualTo(SituacaoCargo.SEM_CANDIDATOS);
        assertThat(r.candidatos()).isEmpty();
        assertThat(r.totalVotos()).isZero();
    }

    private static Candidato candidato(Long id, String nome, int numero) {
        Usuario usuario = Usuario.builder().nome(nome).login(nome.toLowerCase()).senhaHash("x")
                .perfil(Perfil.ELEITOR).build();
        Eleitor eleitor = Eleitor.builder().id(id).usuario(usuario).tituloEleitor("t" + id).cpf("c" + id).build();
        Partido partido = Partido.builder().id(id).numero(numero).sigla("P" + numero).nome("Partido " + numero).build();
        return Candidato.builder().id(id).eleitor(eleitor).partido(partido).cargo(Cargo.GOVERNADOR).numero(numero).build();
    }
}
