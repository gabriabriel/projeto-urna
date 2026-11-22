package com.eleicao.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eleicao.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class CadastroIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("Chefe cadastra eleitor: 201, Location, sem senha na resposta e hash BCrypt no banco")
    void chefeCadastraEleitor() throws Exception {
        String token = loginChefe();

        mvc.perform(post("/eleitores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleitor("Maria Silva", "maria.it", "123456789012", "12345678901")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.cpf").value("12345678901"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        String hash = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE login = 'maria.it'", String.class);
        assertThat(hash).isNotEqualTo("senha123").startsWith("$2");
    }

    @Test
    @DisplayName("Eleitor com CPF ou título repetido: 409")
    void eleitorDuplicado_409() throws Exception {
        String token = loginChefe();
        criarEleitor(token, "Ana", "ana.it", "111111111111", "11111111111");

        mvc.perform(post("/eleitores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleitor("Outra", "outra.it", "222222222222", "11111111111")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mvc.perform(post("/eleitores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleitor("Outra", "outra2.it", "111111111111", "33333333333")))
                .andExpect(status().isConflict());

        Integer logins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE login IN ('outra.it', 'outra2.it')", Integer.class);
        assertThat(logins).isZero();
    }

    @Test
    @DisplayName("Dados inválidos no cadastro de eleitor: 400")
    void eleitorComDadosInvalidos_400() throws Exception {
        mvc.perform(post("/eleitores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginChefe()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleitor("Teste", "teste.it", "123456789012", "123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Consulta de eleitor inexistente: 404")
    void eleitorInexistente_404() throws Exception {
        mvc.perform(get("/eleitores/999999").header(HttpHeaders.AUTHORIZATION, bearer(loginChefe())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Perfis: sem token 401; eleitor comum 403 nas rotas do Chefe")
    void perfis() throws Exception {
        mvc.perform(get("/eleitores")).andExpect(status().isUnauthorized());

        String tokenChefe = loginChefe();
        criarEleitor(tokenChefe, "Bruno", "bruno.it", "444444444444", "44444444444");
        String tokenEleitor = login("bruno.it", "senha123");

        mvc.perform(get("/eleitores").header(HttpHeaders.AUTHORIZATION, bearer(tokenEleitor)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/candidatos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenEleitor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/candidatos").header(HttpHeaders.AUTHORIZATION, bearer(tokenEleitor)))
                .andExpect(status().isOk());
        mvc.perform(get("/partidos").header(HttpHeaders.AUTHORIZATION, bearer(tokenEleitor)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Partidos: 30 partidos do TSE em ordem de número; id inexistente dá 404")
    void partidos() throws Exception {
        String token = loginChefe();

        mvc.perform(get("/partidos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[0].numero").value(10))
                .andExpect(jsonPath("$[0].sigla").value("REPUBLICANOS"));

        mvc.perform(get("/partidos/" + partidoId(13)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sigla").value("PT"));

        mvc.perform(get("/partidos/999999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Chefe cadastra candidato (eleitor + partido + cargo + número) e ele aparece na listagem")
    void chefeCadastraCandidato() throws Exception {
        String token = loginChefe();
        long eleicaoId = criarEleicaoAgendada();
        long eleitorId = criarEleitor(token, "Carla", "carla.it", "555555555555", "55555555555");

        mvc.perform(post("/candidatos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCandidato(eleitorId, eleicaoId, partidoId(13), "GOVERNADOR", 13)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nome").value("Carla"))
                .andExpect(jsonPath("$.partidoSigla").value("PT"))
                .andExpect(jsonPath("$.partidoNumero").value(13))
                .andExpect(jsonPath("$.numero").value(13));

        mvc.perform(get("/candidatos")
                        .param("eleicaoId", String.valueOf(eleicaoId))
                        .param("cargo", "GOVERNADOR")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eleitorId").value(eleitorId));
    }

    @Test
    @DisplayName("Regras do candidato: número fora do prefixo do partido 422; número repetido 409; 2ª candidatura do mesmo eleitor 409")
    void regrasDoCandidato() throws Exception {
        String token = loginChefe();
        long eleicaoId = criarEleicaoAgendada();
        long partidoPt = partidoId(13);
        long carla = criarEleitor(token, "Carla", "carla.it", "555555555555", "55555555555");
        long diego = criarEleitor(token, "Diego", "diego.it", "666666666666", "66666666666");

        postCandidato(token, carla, eleicaoId, partidoPt, "GOVERNADOR", 14).andExpect(status().isUnprocessableEntity());

        postCandidato(token, carla, eleicaoId, partidoPt, "GOVERNADOR", 13).andExpect(status().isCreated());

        postCandidato(token, diego, eleicaoId, partidoPt, "GOVERNADOR", 13).andExpect(status().isConflict());

        postCandidato(token, diego, eleicaoId, partidoPt, "SENADOR", 13).andExpect(status().isCreated());

        postCandidato(token, carla, eleicaoId, partidoPt, "SENADOR", 131).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Referências inexistentes: eleitor, partido e eleição dão 404; cargo inválido dá 400")
    void referenciasInexistentes() throws Exception {
        String token = loginChefe();
        long eleicaoId = criarEleicaoAgendada();
        long eleitorId = criarEleitor(token, "Eva", "eva.it", "777777777777", "77777777777");

        postCandidato(token, 999999, eleicaoId, partidoId(13), "GOVERNADOR", 13).andExpect(status().isNotFound());
        postCandidato(token, eleitorId, eleicaoId, 999999, "GOVERNADOR", 13).andExpect(status().isNotFound());
        postCandidato(token, eleitorId, 999999, partidoId(13), "GOVERNADOR", 13).andExpect(status().isNotFound());
        postCandidato(token, eleitorId, eleicaoId, partidoId(13), "PREFEITO", 13).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Eleição ABERTA: não se cadastra nem se exclui candidato (422)")
    void eleicaoAberta_422() throws Exception {
        String token = loginChefe();
        long eleicaoId = criarEleicaoAgendada();
        long eleitorId = criarEleitor(token, "Fábio", "fabio.it", "888888888888", "88888888888");

        String resposta = postCandidato(token, eleitorId, eleicaoId, partidoId(13), "GOVERNADOR", 13)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long candidatoId = objectMapper.readTree(resposta).get("id").asLong();

        jdbc.update("UPDATE eleicao SET status = 'ABERTA' WHERE id = ?", eleicaoId);
        em.clear();
        long outro = criarEleitor(token, "Gil", "gil.it", "999999999999", "99999999999");
        postCandidato(token, outro, eleicaoId, partidoId(13), "SENADOR", 13).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/candidatos/" + candidatoId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Excluir eleitor: candidato 409; depois de excluir a candidatura 204")
    void excluirEleitorCandidato() throws Exception {
        String token = loginChefe();
        long eleicaoId = criarEleicaoAgendada();
        long eleitorId = criarEleitor(token, "Helena", "helena.it", "101010101010", "10101010101");
        String resposta = postCandidato(token, eleitorId, eleicaoId, partidoId(13), "GOVERNADOR", 13)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long candidatoId = objectMapper.readTree(resposta).get("id").asLong();

        mvc.perform(delete("/eleitores/" + eleitorId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        mvc.perform(delete("/candidatos/" + candidatoId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/eleitores/" + eleitorId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        em.flush();
        assertThat(contar("SELECT COUNT(*) FROM eleitor WHERE id = ?", eleitorId)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuario WHERE login = 'helena.it'")).isZero();
    }

    @Test
    @DisplayName("Excluir eleitor que já votou: a FK do banco barra e a API responde 409")
    void excluirEleitorQueJaVotou_409() throws Exception {
        String token = loginChefe();
        long eleicaoId = criarEleicaoAgendada();
        long eleitorId = criarEleitor(token, "Igor", "igor.it", "121212121212", "12121212121");

        jdbc.update("INSERT INTO participacao (eleicao_id, eleitor_id, cargo) VALUES (?, ?, 'GOVERNADOR')",
                eleicaoId, eleitorId);

        mvc.perform(delete("/eleitores/" + eleitorId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        assertThat(contar("SELECT COUNT(*) FROM eleitor WHERE id = ?", eleitorId)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM usuario WHERE login = 'igor.it'")).isEqualTo(1);
    }


    private String loginChefe() throws Exception {
        return login("chefe", "admin123");
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long criarEleitor(String token, String nome, String login, String titulo, String cpf) throws Exception {
        String resposta = mvc.perform(post("/eleitores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleitor(nome, login, titulo, cpf)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions postCandidato(
            String token, long eleitorId, long eleicaoId, long partidoId, String cargo, int numero) throws Exception {
        return mvc.perform(post("/candidatos")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonCandidato(eleitorId, eleicaoId, partidoId, cargo, numero)));
    }

    private long criarEleicaoAgendada() {
        jdbc.update("INSERT INTO eleicao (data, turno, horario_abertura, horario_encerramento) "
                + "VALUES ('2031-11-02', 1, '08:00:00', '17:00:00')");
        return jdbc.queryForObject(
                "SELECT id FROM eleicao WHERE data = '2031-11-02' AND turno = 1", Long.class);
    }

    private long partidoId(int numero) {
        return jdbc.queryForObject("SELECT id FROM partido WHERE numero = ?", Long.class, numero);
    }

    private Integer contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String jsonEleitor(String nome, String login, String titulo, String cpf) {
        return """
                {"nome": "%s", "login": "%s", "senha": "senha123", "tituloEleitor": "%s", "cpf": "%s"}
                """.formatted(nome, login, titulo, cpf);
    }

    private String jsonCandidato(long eleitorId, long eleicaoId, long partidoId, String cargo, int numero) {
        return """
                {"eleitorId": %d, "eleicaoId": %d, "partidoId": %d, "cargo": "%s", "numero": %d}
                """.formatted(eleitorId, eleicaoId, partidoId, cargo, numero);
    }

    private record Credenciais(String login, String senha) {
    }
}
