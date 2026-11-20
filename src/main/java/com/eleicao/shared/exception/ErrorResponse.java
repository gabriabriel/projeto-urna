package com.eleicao.shared.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<String> detalhes) {
}
