package com.eleicao.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eleicao.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integração do BANCO: provam que as regras críticas estão protegidas
 * por constraints, e não apenas por código Java. Cada teste faz rollback no final.
 */
@Transactional
class SchemaIntegridadeIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void votoNaoGuardaEleitorNemHorario_garanteSigilo() {
        List<String> colunas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'voto'",
                String.class);

        assertThat(colunas).containsExactlyInAnyOrder("id", "candidato_id");
    }

    @Test
    void bancoImpedeDoisVotosDoMesmoEleitorNoMesmoCargo() {
        long eleicaoId = criarEleicao("2030-10-05", 1, "08:00:00", "17:00:00");
        long eleitorId = criarEleitor();

        jdbc.update("INSERT INTO participacao (eleicao_id, eleitor_id, cargo) VALUES (?, ?, 'GOVERNADOR')",
                eleicaoId, eleitorId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO participacao (eleicao_id, eleitor_id, cargo) VALUES (?, ?, 'GOVERNADOR')",
                eleicaoId, eleitorId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void mesmoEleitorPodeVotarEmCargosDiferentes() {
        long eleicaoId = criarEleicao("2030-10-05", 1, "08:00:00", "17:00:00");
        long eleitorId = criarEleitor();

        jdbc.update("INSERT INTO participacao (eleicao_id, eleitor_id, cargo) VALUES (?, ?, 'GOVERNADOR')",
                eleicaoId, eleitorId);
        jdbc.update("INSERT INTO participacao (eleicao_id, eleitor_id, cargo) VALUES (?, ?, 'SENADOR')",
                eleicaoId, eleitorId);

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM participacao WHERE eleitor_id = ?", Integer.class, eleitorId);
        assertThat(total).isEqualTo(2);
    }

    @Test
    void bancoImpedeDuasEleicoesNaMesmaDataETurno() {
        criarEleicao("2030-10-05", 1, "08:00:00", "17:00:00");

        assertThatThrownBy(() -> criarEleicao("2030-10-05", 1, "09:00:00", "18:00:00"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void bancoImpedeEleicaoComEncerramentoAntesDaAbertura() {
        assertThatThrownBy(() -> criarEleicao("2030-10-06", 1, "17:00:00", "08:00:00"))
                .isInstanceOf(DataAccessException.class);
    }

    // ------------------------------------------------------------------ helpers

    private long criarEleicao(String data, int turno, String abertura, String encerramento) {
        jdbc.update("INSERT INTO eleicao (data, turno, horario_abertura, horario_encerramento) "
                + "VALUES (?, ?, ?, ?)", data, turno, abertura, encerramento);
        return jdbc.queryForObject("SELECT MAX(id) FROM eleicao", Long.class);
    }

    private long criarEleitor() {
        String sufixo = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil) VALUES (?, ?, 'hash', 'ELEITOR')",
                "Eleitor Teste", "eleitor_" + sufixo);
        long usuarioId = jdbc.queryForObject("SELECT MAX(id) FROM usuario", Long.class);

        // título (12) e CPF (11) únicos, derivados do sufixo aleatório
        String titulo = sufixo.substring(0, 12);
        String cpf = sufixo.substring(12, 23);
        jdbc.update("INSERT INTO eleitor (usuario_id, titulo_eleitor, cpf) VALUES (?, ?, ?)",
                usuarioId, titulo, cpf);
        return jdbc.queryForObject("SELECT MAX(id) FROM eleitor", Long.class);
    }
}
