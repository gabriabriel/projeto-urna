package com.eleicao.eleicao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eleicao.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integração dos módulos eleicao + apuracao com auth, cadastro e votação de verdade, sobre o MySQL
 * do Testcontainers: HTTP -> segurança (JWT) -> services -> banco. Cada teste faz rollback no final.
 */
@AutoConfigureMockMvc
@Transactional
class EleicaoApuracaoIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Fluxo completo: cadastrar, abrir, votar, acompanhar, encerrar e consultar o resultado final")
    void fluxoCompleto() throws Exception {
        String chefe = loginChefe();
        long eleicaoId = criarEleicao(chefe, "2032-10-03", 1);

        long ana = criarEleitor(chefe, "Ana", "ana.b.it", "200000000001", "20000000001");
        long beto = criarEleitor(chefe, "Beto", "beto.b.it", "200000000002", "20000000002");
        criarEleitor(chefe, "Eva", "eva.b.it", "200000000003", "20000000003");
        long candidataAna = criarCandidato(chefe, ana, eleicaoId, 13, "GOVERNADOR", 13);
        long candidatoBeto = criarCandidato(chefe, beto, eleicaoId, 22, "GOVERNADOR", 22);

        // situação antes de abrir
        mvc.perform(get("/eleicoes/" + eleicaoId + "/situacao").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.totalCandidatos").value(2))
                .andExpect(jsonPath("$.candidatosPorCargo.GOVERNADOR").value(2));

        // abrir
        mvc.perform(post("/eleicoes/" + eleicaoId + "/abertura").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.abertaEm").isNotEmpty());

        // votar: Eva e Ana em Beto; Beto em Ana
        String tokenAna = login("ana.b.it");
        String tokenBeto = login("beto.b.it");
        String tokenEva = login("eva.b.it");
        votar(tokenEva, eleicaoId, candidatoBeto).andExpect(status().isCreated());
        votar(tokenAna, eleicaoId, candidatoBeto).andExpect(status().isCreated());
        votar(tokenBeto, eleicaoId, candidataAna).andExpect(status().isCreated());

        // acompanhamento (parcial) pelo eleitor comum
        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/parcial").header(HttpHeaders.AUTHORIZATION, bearer(tokenEva)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PARCIAL"))
                .andExpect(jsonPath("$.totalVotos").value(3))
                .andExpect(jsonPath("$.cargos.length()").value(1))
                .andExpect(jsonPath("$.cargos[0].cargo").value("GOVERNADOR"))
                .andExpect(jsonPath("$.cargos[0].situacao").value("VENCEDOR"))
                .andExpect(jsonPath("$.cargos[0].candidatos[0].nome").value("Beto"))
                .andExpect(jsonPath("$.cargos[0].candidatos[0].votos").value(2))
                .andExpect(jsonPath("$.cargos[0].candidatos[0].percentual").value(66.67));

        // a parcial não grava nada
        assertThat(contar("SELECT COUNT(*) FROM apuracao WHERE eleicao_id = ?", eleicaoId)).isZero();

        // encerrar
        mvc.perform(post("/eleicoes/" + eleicaoId + "/encerramento").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADA"))
                .andExpect(jsonPath("$.encerradaEm").isNotEmpty());

        // o resultado final foi gravado no encerramento
        assertThat(contar("SELECT COUNT(*) FROM apuracao WHERE eleicao_id = ?", eleicaoId)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM resultado_candidato r JOIN apuracao a ON a.id = r.apuracao_id "
                + "WHERE a.eleicao_id = ?", eleicaoId)).isEqualTo(2);

        // depois de encerrada: não se vota, não há parcial, não se encerra de novo
        String tokenNovo = criarELogarEleitor(chefe, "Gil", "gil.b.it", "200000000004", "20000000004");
        votar(tokenNovo, eleicaoId, candidatoBeto).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/parcial").header(HttpHeaders.AUTHORIZATION, bearer(tokenEva)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/eleicoes/" + eleicaoId + "/encerramento").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isUnprocessableEntity());

        // resultado final
        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/resultado").header(HttpHeaders.AUTHORIZATION, bearer(tokenEva)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("FINAL"))
                .andExpect(jsonPath("$.statusEleicao").value("ENCERRADA"))
                .andExpect(jsonPath("$.totalVotos").value(3))
                .andExpect(jsonPath("$.cargos[0].cargo").value("GOVERNADOR"))
                .andExpect(jsonPath("$.cargos[0].situacao").value("VENCEDOR"))
                .andExpect(jsonPath("$.cargos[0].vencedor.nome").value("Beto"))
                .andExpect(jsonPath("$.cargos[0].vencedor.votos").value(2))
                .andExpect(jsonPath("$.cargos[0].candidatos[1].nome").value("Ana"))
                .andExpect(jsonPath("$.cargos[0].candidatos[1].percentual").value(33.33));
    }

    @Test
    @DisplayName("Candidato acompanha só o próprio cargo: outro cargo dá 403")
    void candidatoSoAcompanhaOProprioCargo() throws Exception {
        String chefe = loginChefe();
        long eleicaoId = criarEleicao(chefe, "2032-10-03", 1);
        long ana = criarEleitor(chefe, "Ana", "ana.b.it", "200000000001", "20000000001");
        long beto = criarEleitor(chefe, "Beto", "beto.b.it", "200000000002", "20000000002");
        long caio = criarEleitor(chefe, "Caio", "caio.b.it", "200000000005", "20000000005");
        criarCandidato(chefe, ana, eleicaoId, 13, "GOVERNADOR", 13);
        criarCandidato(chefe, beto, eleicaoId, 22, "GOVERNADOR", 22);
        criarCandidato(chefe, caio, eleicaoId, 15, "SENADOR", 151);
        abrir(chefe, eleicaoId).andExpect(status().isOk());

        String tokenAna = login("ana.b.it");

        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/parcial").header(HttpHeaders.AUTHORIZATION, bearer(tokenAna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargos.length()").value(1))
                .andExpect(jsonPath("$.cargos[0].cargo").value("GOVERNADOR"));
        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/parcial").param("cargo", "SENADOR")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAna)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // o Chefe vê os dois cargos
        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/parcial").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargos.length()").value(2));
    }

    @Test
    @DisplayName("Casos de borda: abrir sem candidatos 422, editar/excluir eleição aberta 422, resultado antes do encerramento 422")
    void casosDeBorda() throws Exception {
        String chefe = loginChefe();
        long eleicaoId = criarEleicao(chefe, "2032-10-03", 1);

        abrir(chefe, eleicaoId).andExpect(status().isUnprocessableEntity());

        long ana = criarEleitor(chefe, "Ana", "ana.b.it", "200000000001", "20000000001");
        criarCandidato(chefe, ana, eleicaoId, 13, "GOVERNADOR", 13);
        abrir(chefe, eleicaoId).andExpect(status().isUnprocessableEntity()); // só 1 candidato

        long beto = criarEleitor(chefe, "Beto", "beto.b.it", "200000000002", "20000000002");
        criarCandidato(chefe, beto, eleicaoId, 22, "GOVERNADOR", 22);

        // enquanto AGENDADA, pode alterar
        mvc.perform(put("/eleicoes/" + eleicaoId).header(HttpHeaders.AUTHORIZATION, bearer(chefe))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleicao("2032-10-10", 1, "07:00:00", "18:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2032-10-10"));

        // com candidatos, não se exclui
        mvc.perform(delete("/eleicoes/" + eleicaoId).header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isConflict());

        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/resultado").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isUnprocessableEntity());

        abrir(chefe, eleicaoId).andExpect(status().isOk());
        abrir(chefe, eleicaoId).andExpect(status().isUnprocessableEntity()); // já está aberta

        mvc.perform(put("/eleicoes/" + eleicaoId).header(HttpHeaders.AUTHORIZATION, bearer(chefe))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleicao("2032-10-11", 1, "08:00:00", "17:00:00")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/eleicoes/" + eleicaoId).header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/apuracao/eleicoes/" + eleicaoId + "/resultado").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("CRUD e validações: 201 com Location, 400, 404, 409, 422, exclusão 204 e perfis 401/403")
    void crudEValidacoes() throws Exception {
        String chefe = loginChefe();

        mvc.perform(post("/eleicoes").header(HttpHeaders.AUTHORIZATION, bearer(chefe))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEleicao("2032-10-03", 1, "08:00:00", "17:00:00")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("AGENDADA"));

        // mesma data e turno: 409
        postEleicao(chefe, jsonEleicao("2032-10-03", 1, "09:00:00", "18:00:00")).andExpect(status().isConflict());
        // encerramento antes da abertura: 422
        postEleicao(chefe, jsonEleicao("2032-10-04", 1, "17:00:00", "08:00:00")).andExpect(status().isUnprocessableEntity());
        // turno inválido e data passada: 400
        postEleicao(chefe, jsonEleicao("2032-10-04", 3, "08:00:00", "17:00:00")).andExpect(status().isBadRequest());
        postEleicao(chefe, jsonEleicao("2000-01-01", 1, "08:00:00", "17:00:00")).andExpect(status().isBadRequest());
        // status inexistente no filtro: 400
        mvc.perform(get("/eleicoes").param("status", "PAUSADA").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/eleicoes/999999").header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isNotFound());

        // eleição sem candidatos e AGENDADA pode ser excluída
        long semCandidatos = criarEleicao(chefe, "2032-10-05", 2);
        mvc.perform(delete("/eleicoes/" + semCandidatos).header(HttpHeaders.AUTHORIZATION, bearer(chefe)))
                .andExpect(status().isNoContent());
        assertThat(contar("SELECT COUNT(*) FROM eleicao WHERE id = ?", semCandidatos)).isZero();

        // perfis
        mvc.perform(get("/eleicoes")).andExpect(status().isUnauthorized());
        String eleitor = criarELogarEleitor(chefe, "Eva", "eva.b.it", "200000000003", "20000000003");
        mvc.perform(get("/eleicoes").header(HttpHeaders.AUTHORIZATION, bearer(eleitor)))
                .andExpect(status().isOk());
        postEleicao(eleitor, jsonEleicao("2032-10-06", 1, "08:00:00", "17:00:00")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private String loginChefe() throws Exception {
        return login("chefe", "admin123");
    }

    private String login(String login) throws Exception {
        return login(login, "senha123");
    }

    private String login(String login, String senha) throws Exception {
        String corpo = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login": "%s", "senha": "%s"}
                                """.formatted(login, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions postEleicao(String token, String json) throws Exception {
        return mvc.perform(post("/eleicoes").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long criarEleicao(String token, String data, int turno) throws Exception {
        String corpo = postEleicao(token, jsonEleicao(data, turno, "08:00:00", "17:00:00"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private ResultActions abrir(String token, long eleicaoId) throws Exception {
        return mvc.perform(post("/eleicoes/" + eleicaoId + "/abertura").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long criarEleitor(String token, String nome, String login, String titulo, String cpf) throws Exception {
        String corpo = mvc.perform(post("/eleitores").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "login": "%s", "senha": "senha123", "tituloEleitor": "%s", "cpf": "%s"}
                                """.formatted(nome, login, titulo, cpf)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private String criarELogarEleitor(String tokenChefe, String nome, String login, String titulo, String cpf)
            throws Exception {
        criarEleitor(tokenChefe, nome, login, titulo, cpf);
        return login(login);
    }

    private long criarCandidato(String token, long eleitorId, long eleicaoId, int numeroPartido, String cargo,
                                int numero) throws Exception {
        long partidoId = jdbc.queryForObject("SELECT id FROM partido WHERE numero = ?", Long.class, numeroPartido);
        String corpo = mvc.perform(post("/candidatos").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eleitorId": %d, "eleicaoId": %d, "partidoId": %d, "cargo": "%s", "numero": %d}
                                """.formatted(eleitorId, eleicaoId, partidoId, cargo, numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private ResultActions votar(String token, long eleicaoId, long candidatoId) throws Exception {
        return mvc.perform(post("/votacao/eleicoes/" + eleicaoId + "/voto/confirmacao")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cargo": "GOVERNADOR", "candidatoId": %d}
                        """.formatted(candidatoId)));
    }

    private String jsonEleicao(String data, int turno, String abertura, String encerramento) {
        return """
                {"data": "%s", "turno": %d, "horarioAbertura": "%s", "horarioEncerramento": "%s"}
                """.formatted(data, turno, abertura, encerramento);
    }

    private Integer contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
