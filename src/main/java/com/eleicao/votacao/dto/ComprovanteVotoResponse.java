package com.eleicao.votacao.dto;

import com.eleicao.shared.domain.Cargo;
import java.time.LocalDateTime;

/**
 * Resposta da confirmação. Não diz em quem o eleitor votou, para preservar o sigilo do voto.
 */
public record ComprovanteVotoResponse(
        Long eleicaoId,
        Cargo cargo,
        LocalDateTime confirmadoEm) {
}
