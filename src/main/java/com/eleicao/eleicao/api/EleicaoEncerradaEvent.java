package com.eleicao.eleicao.api;

/**
 * Evento publicado pelo módulo eleicao quando o Chefe da Seção encerra a votação.
 *
 * É assim que o módulo apuracao fica sabendo que deve gravar o resultado final, sem que o módulo
 * eleicao dependa dele (o eleicao só publica; quem quiser, escuta). O evento é síncrono e roda na
 * mesma transação do encerramento: se a apuração falhar, o encerramento também é desfeito.
 */
public record EleicaoEncerradaEvent(Long eleicaoId) {
}
