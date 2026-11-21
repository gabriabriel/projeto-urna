package com.eleicao.auth.exception;

import com.eleicao.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros de autenticação/autorização que acontecem DENTRO dos controllers/services
 * (login errado, @PreAuthorize negado, UsuarioLogadoProvider sem usuário).
 *
 * Precisa rodar antes do GlobalExceptionHandler (que tem um handler genérico para Exception
 * e devolveria 500), por isso a ordem de maior precedência. Só trata os tipos abaixo;
 * todo o resto continua indo para o GlobalExceptionHandler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErrorResponse> credenciaisInvalidas(CredenciaisInvalidasException ex,
                                                              HttpServletRequest req) {
        return resposta(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> naoAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return resposta(HttpStatus.UNAUTHORIZED, "Autenticação necessária", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> acessoNegado(AccessDeniedException ex, HttpServletRequest req) {
        return resposta(HttpStatus.FORBIDDEN, "Seu perfil não tem permissão para esta operação", req);
    }

    private ResponseEntity<ErrorResponse> resposta(HttpStatus status, String mensagem, HttpServletRequest req) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                mensagem, req.getRequestURI(), List.of()));
    }
}
