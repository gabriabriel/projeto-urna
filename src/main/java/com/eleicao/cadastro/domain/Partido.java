package com.eleicao.cadastro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "partido")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número da legenda no TSE (2 dígitos). É o prefixo do número dos candidatos do partido. */
    @Column(nullable = false, unique = true)
    private Integer numero;

    @Column(nullable = false, length = 20, unique = true)
    private String sigla;

    @Column(nullable = false, length = 100, unique = true)
    private String nome;
}
