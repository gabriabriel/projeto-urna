package com.eleicao.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> naoEncontrado(NotFoundException ex, HttpServletRequest req) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflito(ConflictException ex, HttpServletRequest req) {
        return resposta(HttpStatus.CONFLICT, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> proibido(ForbiddenException ex, HttpServletRequest req) {
        return resposta(HttpStatus.FORBIDDEN, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> regraDeNegocio(BusinessRuleException ex, HttpServletRequest req) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return resposta(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos", req, detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> corpoIlegivel(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST, "Corpo da requisição ausente ou malformado", req, List.of());
    }

    /** Valor inválido na URL (ex.: cargo ou status inexistente em /...?cargo=PREFEITO, ou id não numérico). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parametroInvalido(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + ex.getName() + "'",
                req, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "A operação viola uma restrição de integridade dos dados", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado em {}", req.getRequestURI(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", req, List.of());
    }

    private ResponseEntity<ErrorResponse> resposta(HttpStatus status, String mensagem,
                                                   HttpServletRequest req, List<String> detalhes) {
        ErrorResponse corpo = new ErrorResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                mensagem, req.getRequestURI(), detalhes);
        return ResponseEntity.status(status).body(corpo);
    }
}
