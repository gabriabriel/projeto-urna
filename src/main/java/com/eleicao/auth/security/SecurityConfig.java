package com.eleicao.auth.security;

import com.eleicao.auth.repository.UsuarioRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração do Spring Security.
 *
 * - Stateless: o servidor não guarda sessão; cada requisição se identifica pelo token JWT.
 * - CSRF desligado: CSRF explora cookies de sessão enviados automaticamente pelo navegador.
 *   Aqui não há cookie de sessão (o token vai num cabeçalho que o cliente coloca à mão).
 * - @EnableMethodSecurity: permite que outros módulos usem @PreAuthorize("hasRole('ELEITOR')")
 *   em métodos, se quiserem uma segunda barreira além das regras por rota.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** BCrypt com custo 10 (padrão), o mesmo usado no hash do Chefe em V2__dados_iniciais.sql. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UsuarioRepository usuarioRepository,
                                                   RespostasDeErroDeSeguranca respostasDeErro) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostasDeErro)   // 401
                        .accessDeniedHandler(respostasDeErro))       // 403
                .authorizeHttpRequests(RegrasDeAcesso::aplicar)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
