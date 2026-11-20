package com.eleicao.eleicao.domain;

import com.eleicao.shared.domain.StatusEleicao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "eleicao")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Eleicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private Integer turno;

    @Column(name = "horario_abertura", nullable = false)
    private LocalTime horarioAbertura;

    @Column(name = "horario_encerramento", nullable = false)
    private LocalTime horarioEncerramento;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusEleicao status = StatusEleicao.AGENDADA;

    @Column(name = "aberta_em")
    private LocalDateTime abertaEm;

    @Column(name = "encerrada_em")
    private LocalDateTime encerradaEm;
}
