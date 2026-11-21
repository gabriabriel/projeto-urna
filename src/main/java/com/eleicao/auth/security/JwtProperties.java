package com.eleicao.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração do JWT, lida de app.jwt.* no application.yml. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiracao) {
}
