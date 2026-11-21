package com.eleicao.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eleicao.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integração de ponta a ponta com MySQL real (Testcontainers): login do Chefe que vem da
 * migration V2, cadastro de um eleitor pelo módulo cadastro (que chama a UsuarioApi real),
 * login desse eleitor e as permissões de cada perfil.
 *
 * @Transactional: tudo o que o teste grava é desfeito no final, para não afetar os outros ITs.
 */
@AutoConfigureMockMvc
@Transactional
class AuthIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Chefe loga, cadastra eleitor; eleitor loga e não acessa rota do Chefe")
    void fluxoCompletoDeAutenticacao() throws Exception {
        String tokenChefe = login("chefe", "admin123");

        mvc.perform(post("/eleitores")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenChefe)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Eleitor Auth IT", "login": "eleitor.auth.it", "senha": "senha123",
                                 "tituloEleitor": "987654321098", "cpf": "98765432109"}
                                """))
                .andExpect(status().isCreated());

        String hash = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE login = 'eleitor.auth.it'", String.class);
        assertThat(hash).isNotEqualTo("senha123").startsWith("$2a$");

        String tokenEleitor = login("eleitor.auth.it", "senha123");

        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEleitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ELEITOR"));

        mvc.perform(get("/eleitores").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEleitor))
                .andExpect(status().isForbidden());

        mvc.perform(get("/candidatos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEleitor))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha errada do Chefe responde 401")
    void senhaErrada_401() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"chefe\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rota protegida sem token responde 401")
    void semToken_401() throws Exception {
        mvc.perform(get("/eleitores")).andExpect(status().isUnauthorized());
    }

    private String login(String login, String senha) throws Exception {
        String corpo = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credenciais(login, senha))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(corpo);
        return json.get("token").asText();
    }

    private record Credenciais(String login, String senha) {
    }
}
