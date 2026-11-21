package com.eleicao.auth.controller;

import com.eleicao.auth.api.UsuarioLogadoProvider;
import com.eleicao.auth.dto.LoginRequest;
import com.eleicao.auth.dto.TokenResponse;
import com.eleicao.auth.dto.UsuarioResponse;
import com.eleicao.auth.service.AuthService;
import com.eleicao.auth.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login e dados do usuário logado")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;
    private final UsuarioLogadoProvider usuarioLogadoProvider;

    @PostMapping("/login")
    @SecurityRequirements // rota pública: sem cadeado no Swagger
    @Operation(summary = "Faz login (Chefe da Seção ou Eleitor) e devolve o token JWT",
            description = "Use o token no botão Authorize do Swagger ou no cabeçalho Authorization: Bearer <token>")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Mostra quem está logado (útil para conferir o token)")
    public UsuarioResponse me() {
        return UsuarioResponse.from(usuarioService.buscar(usuarioLogadoProvider.atual().usuarioId()));
    }
}
