package com.eleicao.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Faz o Swagger UI mostrar o botão "Authorize": cola-se o token do /auth/login uma vez
 * e todas as chamadas seguintes já vão com "Authorization: Bearer <token>".
 */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Simulação de Eleição Eletrônica - API")
                        .version("v1")
                        .description("Faça login em POST /auth/login, copie o token e clique em Authorize. "
                                + "Chefe padrão: login chefe / senha admin123."))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
