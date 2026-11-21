package com.eleicao.auth.service;

import com.eleicao.auth.domain.Usuario;
import com.eleicao.auth.dto.LoginRequest;
import com.eleicao.auth.dto.TokenResponse;
import com.eleicao.auth.exception.CredenciaisInvalidasException;
import com.eleicao.auth.repository.UsuarioRepository;
import com.eleicao.auth.security.JwtService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caso de uso do login: confere login e senha e, se estiverem certos, emite o token JWT. */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Hash de uma senha qualquer, usado quando o login não existe. Assim a resposta demora o mesmo
     * tanto que a de um login existente com senha errada, e ninguém descobre quais logins existem
     * medindo o tempo de resposta.
     */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-igualar-o-tempo");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<Usuario> encontrado = usuarioRepository.findByLogin(request.login());

        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(request.senha(), hash);

        Usuario usuario = encontrado
                .filter(u -> senhaConfere)
                .filter(Usuario::isAtivo)
                .orElseThrow(CredenciaisInvalidasException::new);

        String token = jwtService.gerarToken(usuario.getId(), usuario.getPerfil());
        return new TokenResponse(
                token,
                "Bearer",
                jwtService.getExpiracao().toSeconds(),
                usuario.getId(),
                usuario.getNome(),
                usuario.getPerfil());
    }
}
