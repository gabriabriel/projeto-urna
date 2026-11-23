package com.eleicao.apuracao.domain;

import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.shared.domain.Cargo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Linha do resultado final: quantos votos um candidato teve e se foi o mais votado do cargo.
 * Se mais de um candidato do mesmo cargo tem maisVotado = true, houve empate.
 */
@Entity
@Table(name = "resultado_candidato")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResultadoCandidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apuracao_id", nullable = false, updatable = false)
    private Apuracao apuracao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidato_id", nullable = false, updatable = false)
    private Candidato candidato;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Cargo cargo;

    @Column(nullable = false, updatable = false)
    private Long votos;

    @Column(nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal percentual;

    @Column(name = "mais_votado", nullable = false, updatable = false)
    private boolean maisVotado;
}
