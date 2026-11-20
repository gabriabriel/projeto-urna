package com.eleicao;

import static org.assertj.core.api.Assertions.assertThat;

import com.eleicao.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Teste de fumaça: a aplicação sobe e o Flyway aplicou V1 e V2. */
class EleicaoApplicationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextoSobeEMigrationsForamAplicadas() {
        Integer chefes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE perfil = 'CHEFE_SECAO'", Integer.class);

        assertThat(chefes).isEqualTo(1);
    }
}
