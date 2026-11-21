package com.eleicao.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.shared.domain.Perfil;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-pelo-menos-32-bytes!!";
    private static final JwtProperties PROPS = new JwtProperties(SEGREDO, Duration.ofHours(2));

    private final JwtService jwtService = new JwtService(PROPS);

    @Test
    @DisplayName("token gerado é válido e devolve o mesmo usuário e perfil")
    void gerarEValidar_devolveUsuarioDoToken() {
        String token = jwtService.gerarToken(7L, Perfil.ELEITOR);

        Optional<UsuarioLogado> usuario = jwtService.validar(token);

        assertThat(usuario).contains(new UsuarioLogado(7L, Perfil.ELEITOR));
    }

    @Test
    @DisplayName("eleitor que edita o próprio token para virar CHEFE_SECAO é barrado pela assinatura")
    void tokenAdulterado_eInvalido() {
        String token = jwtService.gerarToken(7L, Perfil.ELEITOR);
        String[] partes = token.split("\\.");
        String conteudo = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        assertThat(conteudo).contains("\"perfil\":\"ELEITOR\"");

        String conteudoAlterado = conteudo.replace("\"ELEITOR\"", "\"CHEFE_SECAO\"");
        String forjado = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(conteudoAlterado.getBytes(StandardCharsets.UTF_8))
                + "." + partes[2];

        assertThat(jwtService.validar(forjado)).isEmpty();
    }

    @Test
    @DisplayName("token assinado com outro segredo é inválido")
    void tokenDeOutroSegredo_eInvalido() {
        JwtService outro = new JwtService(new JwtProperties("outro-segredo-qualquer-com-mais-de-32-bytes", Duration.ofHours(2)));
        String token = outro.gerarToken(1L, Perfil.CHEFE_SECAO);

        assertThat(jwtService.validar(token)).isEmpty();
    }

    @Test
    @DisplayName("token expirado é inválido")
    void tokenExpirado_eInvalido() {
        Clock tresHorasAtras = Clock.fixed(Instant.now().minus(3, ChronoUnit.HOURS), ZoneOffset.UTC);
        String token = new JwtService(PROPS, tresHorasAtras).gerarToken(1L, Perfil.CHEFE_SECAO);

        assertThat(jwtService.validar(token)).isEmpty();
    }

    @Test
    @DisplayName("token sem assinatura (alg=none) é inválido")
    void tokenSemAssinatura_eInvalido() {
        String semAssinatura = Jwts.builder()
                .subject("1")
                .claim("perfil", "CHEFE_SECAO")
                .compact();

        assertThat(jwtService.validar(semAssinatura)).isEmpty();
    }

    @Test
    @DisplayName("texto qualquer, vazio ou nulo é inválido (sem lançar exceção)")
    void tokenMalformado_eInvalido() {
        assertThat(jwtService.validar("isso.nao.e-um-token")).isEmpty();
        assertThat(jwtService.validar("")).isEmpty();
        assertThat(jwtService.validar(null)).isEmpty();
    }

    @Test
    @DisplayName("segredo com menos de 32 bytes impede a aplicação de subir")
    void segredoCurto_lancaExcecao() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("curto", Duration.ofHours(2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("expiração informada é a usada no token")
    void expiracao_vemDaConfiguracao() {
        assertThat(jwtService.getExpiracao()).isEqualTo(Duration.ofHours(2));
    }
}
