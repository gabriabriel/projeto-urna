package com.eleicao.apuracao.domain;

import com.eleicao.eleicao.domain.Eleicao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Resultado final de uma eleição, gravado uma única vez no encerramento.
 * Os votos de cada candidato ficam em ResultadoCandidato. Depois de gravado, não muda (sem setters).
 */
@Entity
@Table(name = "apuracao")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Apuracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleicao_id", nullable = false, unique = true, updatable = false)
    private Eleicao eleicao;

    @Builder.Default
    @Column(name = "apurada_em", nullable = false, updatable = false)
    private LocalDateTime apuradaEm = LocalDateTime.now();

    /** Total de votos registrados na eleição, somando todos os cargos. */
    @Column(name = "total_votos", nullable = false, updatable = false)
    private Long totalVotos;
}
