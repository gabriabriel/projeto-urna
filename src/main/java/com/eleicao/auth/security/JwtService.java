package com.eleicao.auth.security;

import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.shared.domain.Perfil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Gera e valida os tokens JWT (assinatura HMAC-SHA256 com o segredo de app.jwt.secret).
 *
 * O token carrega só o necessário: "sub" = id do usuário e "perfil". Ele é assinado, NÃO
 * criptografado: qualquer um consegue ler o conteúdo, mas ninguém consegue alterá-lo sem
 * o segredo (a assinatura deixaria de bater). Por isso nada sigiloso vai dentro dele.
 */
@Service
public class JwtService {

    static final String CLAIM_PERFIL = "perfil";

    private final SecretKey chave;
    private final Duration expiracao;
    private final Clock clock;
    private final JwtParser parser;

    @Autowired
    public JwtService(JwtProperties props) {
        this(props, Clock.systemUTC());
    }

    /** Construtor com relógio controlável, usado nos testes de expiração. */
    JwtService(JwtProperties props, Clock clock) {
        if (props.secret() == null || props.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret precisa ter pelo menos 32 bytes (256 bits) para o HS256");
        }
        if (props.expiracao() == null || props.expiracao().isNegative() || props.expiracao().isZero()) {
            throw new IllegalStateException("app.jwt.expiracao precisa ser positiva (ex.: 2h)");
        }
        this.chave = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expiracao = props.expiracao();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(chave)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String gerarToken(Long usuarioId, Perfil perfil) {
        Instant agora = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim(CLAIM_PERFIL, perfil.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracao)))
                .signWith(chave)
                .compact();
    }

    /**
     * Devolve o usuário do token, ou vazio se o token for inválido por QUALQUER motivo:
     * assinatura errada (adulterado ou assinado com outro segredo), expirado, malformado,
     * sem assinatura ou com conteúdo que não reconhecemos.
     */
    public Optional<UsuarioLogado> validar(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Long usuarioId = Long.valueOf(claims.getSubject());
            Perfil perfil = Perfil.valueOf(claims.get(CLAIM_PERFIL, String.class));
            return Optional.of(new UsuarioLogado(usuarioId, perfil));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // IllegalArgumentException cobre token vazio, "sub" não numérico e perfil desconhecido;
            // NullPointerException cobre token sem "sub" ou sem "perfil".
            return Optional.empty();
        }
    }

    public Duration getExpiracao() {
        return expiracao;
    }
}
