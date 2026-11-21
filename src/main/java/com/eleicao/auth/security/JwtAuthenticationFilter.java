package com.eleicao.auth.security;

import com.eleicao.auth.api.UsuarioLogado;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.auth.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Roda uma vez em cada requisição, antes dos controllers. Se vier "Authorization: Bearer <token>"
 * e o token for válido, coloca o usuário no SecurityContext. Se não vier token ou ele for
 * inválido, a requisição segue SEM usuário, e as regras do SecurityConfig decidem: rota
 * pública passa, rota protegida recebe 401.
 *
 * Não é @Component de propósito: se fosse, o Spring Boot o registraria também como filtro
 * comum do servlet, fora da cadeia do Spring Security. Ele é criado no SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null && cabecalho.startsWith(PREFIXO)) {
            String token = cabecalho.substring(PREFIXO.length()).trim();
            jwtService.validar(token)
                    .flatMap(this::confirmarNoBanco)
                    .ifPresent(usuario -> autenticar(usuario, request));
        }
        chain.doFilter(request, response);
    }

    /**
     * O token pode ser válido e o usuário não existir mais (eleitor excluído depois do login)
     * ou estar inativo. Uma consulta por chave primária resolve isso, e o perfil usado passa
     * a ser o do banco, não o que veio no token.
     */
    private Optional<UsuarioLogado> confirmarNoBanco(UsuarioLogado doToken) {
        return usuarioRepository.findById(doToken.usuarioId())
                .filter(Usuario::isAtivo)
                .map(u -> new UsuarioLogado(u.getId(), u.getPerfil()));
    }

    private void autenticar(UsuarioLogado usuario, HttpServletRequest request) {
        // "ROLE_" é o prefixo que o hasRole("CHEFE_SECAO") do Spring Security espera.
        var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.perfil().name()));
        var autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, autoridades);
        autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
    }
}
