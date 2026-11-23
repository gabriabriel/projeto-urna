package com.eleicao.apuracao.domain;

/** Como ficou a disputa de um cargo na apuração. */
public enum SituacaoCargo {
    /** Um único candidato teve mais votos que todos os outros. */
    VENCEDOR,
    /** Dois ou mais candidatos empataram com o maior número de votos. */
    EMPATE,
    /** Há candidatos, mas ninguém votou para o cargo ainda. */
    SEM_VOTOS,
    /** Não há candidatos para o cargo nesta eleição. */
    SEM_CANDIDATOS
}
