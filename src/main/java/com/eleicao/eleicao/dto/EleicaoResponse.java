package com.eleicao.eleicao.dto;

import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.StatusEleicao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Saída da eleição: agendamento, status e quando o Chefe de fato abriu/encerrou. */
public record EleicaoResponse(
        Long id,
        LocalDate data,
        Integer turno,
        LocalTime horarioAbertura,
        LocalTime horarioEncerramento,
        StatusEleicao status,
        LocalDateTime abertaEm,
        LocalDateTime encerradaEm) {

    public static EleicaoResponse from(Eleicao eleicao) {
        return new EleicaoResponse(
                eleicao.getId(),
                eleicao.getData(),
                eleicao.getTurno(),
                eleicao.getHorarioAbertura(),
                eleicao.getHorarioEncerramento(),
                eleicao.getStatus(),
                eleicao.getAbertaEm(),
                eleicao.getEncerradaEm());
    }
}
