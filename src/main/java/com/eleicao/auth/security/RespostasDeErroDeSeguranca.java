package com.eleicao.auth.security;

import com.eleicao.shared.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Respostas de erro geradas pelo próprio Spring Security, antes de chegar no controller
 * (por isso o GlobalExceptionHandler não as vê). Usa o mesmo formato ErrorResponse do resto da API.
 *
 * 401 Unauthorized = não sei quem você é (sem token, token inválido/expirado).
 * 403 Forbidden    = sei quem você é, mas seu perfil não pode fazer isso.
 */
@Component
@RequiredArgsConstructor
public class RespostasDeErroDeSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        escrever(response, request, HttpStatus.UNAUTHORIZED,
                "Autenticação necessária: faça login e envie o token no cabeçalho Authorization: Bearer <token>");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escrever(response, request, HttpStatus.FORBIDDEN, "Seu perfil não tem permissão para esta operação");
    }

    private void escrever(HttpServletResponse response, HttpServletRequest request,
                          HttpStatus status, String mensagem) throws IOException {
        ErrorResponse corpo = new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                mensagem, request.getRequestURI(), List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), corpo);
    }
}
