package com.eleicao.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base de TODOS os testes de integração (classes *IT).
 * Sobe UM MySQL real em container (precisa do Docker rodando), compartilhado por todas as
 * classes de teste, e aponta o Spring para ele. O Flyway roda as migrations nesse banco,
 * então os testes validam o schema de verdade, e não um banco "de mentirinha".
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        // Padrão "singleton container": inicia uma vez para toda a suíte.
        MYSQL.start();
    }

    @DynamicPropertySource
    static void propriedadesDoBanco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
