package com.eleicao.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eleicao.auth.controller.AuthController;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.auth.dto.TokenResponse;
import com.eleicao.auth.exception.CredenciaisInvalidasException;
import com.eleicao.auth.repository.UsuarioRepository;
import com.eleicao.auth.service.AuthService;
import com.eleicao.auth.service.UsuarioService;
import com.eleicao.shared.domain.Perfil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Testa a camada de segurança sozinha, sem banco: sobe só o MVC + Spring Security, com o
 * filtro JWT e as regras de acesso de verdade, e com o repositório/serviços simulados (mocks).
 *
 * As rotas dos outros módulos não existem neste teste (só o AuthController é carregado),
 * então uma requisição que PASSA pela segurança termina em erro de rota inexistente.
 * O que interessa aqui é só: barrou com 401/403 ou deixou passar?
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtService.class, RespostasDeErroDeSeguranca.class,
        SecurityContextUsuarioLogadoProvider.class})
class RegrasDeAcessoTest {

    private static final ResultMatcher PASSOU_PELA_SEGURANCA =
            resultado -> assertThat(resultado.getResponse().getStatus()).isNotIn(401, 403);

    private static final Usuario CHEFE = Usuario.builder()
            .id(1L).nome("Chefe").login("chefe").senhaHash("x").perfil(Perfil.CHEFE_SECAO).build();
    private static final Usuario ELEITOR = Usuario.builder()
            .id(2L).nome("Maria").login("maria").senhaHash("x").perfil(Perfil.ELEITOR).build();
    private static final Usuario INATIVO = Usuario.builder()
            .id(3L).nome("Inativo").login("inativo").senhaHash("x").perfil(Perfil.ELEITOR).ativo(false).build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;

    @MockBean
    private UsuarioRepository usuarioRepository;
    @MockBean
    private AuthService authService;
    @MockBean
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(CHEFE));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(ELEITOR));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(INATIVO));
    }

    // ------------------------------------------------------------ 401: não autenticado

    @Test
    @DisplayName("sem token, rota protegida responde 401 em JSON")
    void semToken_401() throws Exception {
        mvc.perform(get("/eleitores"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.caminho").value("/eleitores"));
    }

    @Test
    @DisplayName("token inválido responde 401")
    void tokenInvalido_401() throws Exception {
        mvc.perform(get("/eleitores").header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token válido de usuário que foi excluído responde 401")
    void tokenDeUsuarioExcluido_401() throws Exception {
        String token = jwtService.gerarToken(99L, Perfil.CHEFE_SECAO); // id 99 não existe no "banco"

        mvc.perform(get("/eleitores").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token válido de usuário inativo responde 401")
    void tokenDeUsuarioInativo_401() throws Exception {
        mvc.perform(get("/candidatos").header(HttpHeaders.AUTHORIZATION, bearer(INATIVO)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ cadastro

    @Test
    @DisplayName("eleitores: Chefe passa, Eleitor recebe 403")
    void eleitores_soChefe() throws Exception {
        mvc.perform(get("/eleitores").header(HttpHeaders.AUTHORIZATION, bearer(CHEFE)))
                .andExpect(PASSOU_PELA_SEGURANCA);
        mvc.perform(get("/eleitores").header(HttpHeaders.AUTHORIZATION, bearer(ELEITOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("candidatos: Eleitor consulta, mas só o Chefe cadastra")
    void candidatos_eleitorSoConsulta() throws Exception {
        mvc.perform(get("/candidatos").header(HttpHeaders.AUTHORIZATION, bearer(ELEITOR)))
                .andExpect(PASSOU_PELA_SEGURANCA);
        mvc.perform(post("/candidatos").header(HttpHeaders.AUTHORIZATION, bearer(ELEITOR)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/candidatos").header(HttpHeaders.AUTHORIZATION, bearer(CHEFE)))
                .andExpect(PASSOU_PELA_SEGURANCA);
    }

    // ------------------------------------------------------------ eleição

    @Test
    @DisplayName("eleições: todos consultam, só o Chefe abre/encerra")
    void eleicoes_soChefeAltera() throws Exception {
        mvc.perform(get("/eleicoes/1").header(HttpHeaders.AUTHORIZATION, bearer(ELEITOR)))
                .andExpect(PASSOU_PELA_SEGURANCA);
        mvc.perform(post("/eleicoes/1/abrir").header(HttpHeaders.AUTHORIZATION, bearer(ELEITOR)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/eleicoes/1/abrir").header(HttpHeaders.AUTHORIZATION, bearer(CHEFE)))
                .andExpect(PASSOU_PELA_SEGURANCA);
    }

    // ------------------------------------------------------------ votação

    @Test
    @DisplayName("votação: só Eleitor vota, Chefe recebe 403")
    void votacao_soEleitor() throws Exception {
        mvc.perform(post("/votacao/votos").header(HttpHeaders.AUTHORIZATION, bearer(ELEITOR)))
                .andExpect(PASSOU_PELA_SEGURANCA);
        mvc.perform(post("/votacao/votos").header(HttpHeaders.AUTHORIZATION, bearer(CHEFE)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ rotas públicas e /auth

    @Test
    @DisplayName("documentação do Swagger é pública")
    void swagger_publico() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(PASSOU_PELA_SEGURANCA);
    }

    @Test
    @DisplayName("login é público e devolve o token")
    void login_publico() throws Exception {
        when(authService.login(any())).thenReturn(
                new TokenResponse("abc.def.ghi", "Bearer", 7200, 1L, "Chefe", Perfil.CHEFE_SECAO));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"chefe\",\"senha\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("abc.def.ghi"))
                .andExpect(jsonPath("$.perfil").value("CHEFE_SECAO"));
    }

    @Test
    @DisplayName("login com credenciais erradas responde 401 com mensagem genérica")
    void login_credenciaisInvalidas_401() throws Exception {
        when(authService.login(any())).thenThrow(new CredenciaisInvalidasException());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"chefe\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Login ou senha inválidos"));
    }

    @Test
    @DisplayName("login sem senha responde 400 (validação)")
    void login_semSenha_400() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"chefe\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/auth/me devolve o usuário do token")
    void me_devolveUsuarioLogado() throws Exception {
        when(usuarioService.buscar(2L)).thenReturn(ELEITOR);

        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(ELEITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.perfil").value("ELEITOR"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario.getId(), usuario.getPerfil());
    }
}
