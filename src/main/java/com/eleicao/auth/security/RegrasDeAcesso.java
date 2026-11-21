package com.eleicao.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * TODAS as regras de "qual perfil acessa qual rota", num lugar só.
 *
 * As regras são avaliadas de cima para baixo e vale a PRIMEIRA que casar. Por isso as
 * específicas (ex.: GET /candidatos) vêm antes das gerais (/candidatos/**).
 *
 * Se algum módulo criar uma rota com outro prefixo, ela cai em anyRequest().authenticated()
 * (qualquer usuário logado). Para restringir por perfil, adicione a regra aqui.
 */
final class RegrasDeAcesso {

    static final String CHEFE = "CHEFE_SECAO";
    static final String ELEITOR = "ELEITOR";

    private RegrasDeAcesso() {
    }

    static void aplicar(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry regras) {
        regras
                // ---- públicas
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()

                // ---- cadastro (módulo C)
                .requestMatchers("/eleitores/**").hasRole(CHEFE)
                // o eleitor precisa consultar candidatos e partidos para votar
                .requestMatchers(HttpMethod.GET, "/candidatos/**", "/partidos/**").authenticated()
                .requestMatchers("/candidatos/**", "/partidos/**").hasRole(CHEFE)

                // ---- eleição e apuração (módulo B)
                // consultar eleição/situação/apuração: qualquer logado. A regra "candidato só vê o
                // próprio cargo" depende de dados (candidatura), então fica no serviço de apuração.
                .requestMatchers(HttpMethod.GET, "/eleicoes/**", "/apuracao/**").authenticated()
                // cadastrar, alterar, abrir e encerrar: só o Chefe da Seção
                .requestMatchers("/eleicoes/**", "/apuracao/**").hasRole(CHEFE)

                // ---- votação (módulo D)
                .requestMatchers("/votacao/**").hasRole(ELEITOR)

                // ---- qualquer outra rota: precisa estar logado
                .anyRequest().authenticated();
    }
}
