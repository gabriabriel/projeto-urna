-- =====================================================================
-- V4 - Apuração (módulo B): resultado final gravado no encerramento
--
-- O resultado PARCIAL não é gravado: é calculado na hora, a partir da
-- tabela voto. O resultado FINAL é gravado uma única vez, quando o Chefe
-- da Seção encerra a votação, e passa a ser a fonte da consulta final.
-- =====================================================================

-- ---------------------------------------------------------------------
-- APURACAO: um registro por eleição encerrada
-- ---------------------------------------------------------------------
CREATE TABLE apuracao (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    eleicao_id   BIGINT   NOT NULL,
    apurada_em   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_votos  BIGINT   NOT NULL,
    CONSTRAINT pk_apuracao PRIMARY KEY (id),
    -- uma eleição tem no máximo um resultado final
    CONSTRAINT uk_apuracao_eleicao UNIQUE (eleicao_id),
    CONSTRAINT fk_apuracao_eleicao FOREIGN KEY (eleicao_id) REFERENCES eleicao (id),
    CONSTRAINT ck_apuracao_total_votos CHECK (total_votos >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- RESULTADO_CANDIDATO: votos de cada candidato no resultado final
-- ---------------------------------------------------------------------
CREATE TABLE resultado_candidato (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    apuracao_id   BIGINT       NOT NULL,
    candidato_id  BIGINT       NOT NULL,
    cargo         VARCHAR(30)  NOT NULL,
    votos         BIGINT       NOT NULL,
    percentual    DECIMAL(5,2) NOT NULL,          -- % dos votos do cargo (0.00 a 100.00)
    mais_votado   BOOLEAN      NOT NULL DEFAULT FALSE, -- mais de um TRUE no mesmo cargo = empate
    CONSTRAINT pk_resultado_candidato PRIMARY KEY (id),
    CONSTRAINT uk_resultado_apuracao_candidato UNIQUE (apuracao_id, candidato_id),
    CONSTRAINT fk_resultado_apuracao FOREIGN KEY (apuracao_id) REFERENCES apuracao (id),
    CONSTRAINT fk_resultado_candidato FOREIGN KEY (candidato_id) REFERENCES candidato (id),
    CONSTRAINT ck_resultado_cargo CHECK (cargo IN
        ('DEPUTADO_ESTADUAL', 'DEPUTADO_FEDERAL', 'SENADOR', 'GOVERNADOR', 'PRESIDENTE')),
    CONSTRAINT ck_resultado_votos CHECK (votos >= 0),
    CONSTRAINT ck_resultado_percentual CHECK (percentual BETWEEN 0 AND 100)
) ENGINE = InnoDB;
