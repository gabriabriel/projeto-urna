-- =====================================================================
-- V1 - Schema inicial do Sistema de Simulação de Eleição Eletrônica
-- =====================================================================

-- ---------------------------------------------------------------------
-- USUARIO:
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    nome        VARCHAR(120) NOT NULL,
    login       VARCHAR(60)  NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,            -- BCrypt, nunca senha pura
    perfil      VARCHAR(20)  NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_login UNIQUE (login),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('CHEFE_SECAO', 'ELEITOR'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- PARTIDO
-- ---------------------------------------------------------------------
CREATE TABLE partido (
    id     BIGINT       NOT NULL AUTO_INCREMENT,
    numero INT          NOT NULL,
    sigla  VARCHAR(20)  NOT NULL,
    nome   VARCHAR(100) NOT NULL,
    CONSTRAINT pk_partido PRIMARY KEY (id),
    CONSTRAINT uk_partido_numero UNIQUE (numero),
    CONSTRAINT uk_partido_sigla UNIQUE (sigla),
    CONSTRAINT uk_partido_nome UNIQUE (nome)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- ELEITOR:
-- ---------------------------------------------------------------------
CREATE TABLE eleitor (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    usuario_id      BIGINT   NOT NULL,
    titulo_eleitor  CHAR(12) NOT NULL,
    cpf             CHAR(11) NOT NULL,
    CONSTRAINT pk_eleitor PRIMARY KEY (id),
    CONSTRAINT uk_eleitor_usuario UNIQUE (usuario_id),
    CONSTRAINT uk_eleitor_titulo UNIQUE (titulo_eleitor),
    CONSTRAINT uk_eleitor_cpf UNIQUE (cpf),
    CONSTRAINT fk_eleitor_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- ELEICAO:
-- ---------------------------------------------------------------------
CREATE TABLE eleicao (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    data                  DATE        NOT NULL,
    turno                 TINYINT     NOT NULL,
    horario_abertura      TIME        NOT NULL,
    horario_encerramento  TIME        NOT NULL,
    status                VARCHAR(10) NOT NULL DEFAULT 'AGENDADA',
    aberta_em             DATETIME    NULL,      -- quando o Chefe de fato abriu
    encerrada_em          DATETIME    NULL,      -- quando o Chefe de fato encerrou
    CONSTRAINT pk_eleicao PRIMARY KEY (id),
    CONSTRAINT uk_eleicao_data_turno UNIQUE (data, turno),
    CONSTRAINT ck_eleicao_turno CHECK (turno IN (1, 2)),
    CONSTRAINT ck_eleicao_status CHECK (status IN ('AGENDADA', 'ABERTA', 'ENCERRADA')),
    CONSTRAINT ck_eleicao_horarios CHECK (horario_abertura < horario_encerramento)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- CANDIDATO:
-- ---------------------------------------------------------------------
CREATE TABLE candidato (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    eleitor_id  BIGINT      NOT NULL,
    eleicao_id  BIGINT      NOT NULL,
    partido_id  BIGINT      NOT NULL,
    cargo       VARCHAR(30) NOT NULL,
    numero      INT         NOT NULL,             -- número de identificação eleitoral
    CONSTRAINT pk_candidato PRIMARY KEY (id),
    -- a mesma pessoa concorre a um único cargo por eleição
    CONSTRAINT uk_candidato_eleitor_eleicao UNIQUE (eleitor_id, eleicao_id),
    CONSTRAINT uk_candidato_numero UNIQUE (eleicao_id, cargo, numero),
    CONSTRAINT fk_candidato_eleitor FOREIGN KEY (eleitor_id) REFERENCES eleitor (id),
    CONSTRAINT fk_candidato_eleicao FOREIGN KEY (eleicao_id) REFERENCES eleicao (id),
    CONSTRAINT fk_candidato_partido FOREIGN KEY (partido_id) REFERENCES partido (id),
    CONSTRAINT ck_candidato_cargo CHECK (cargo IN
        ('DEPUTADO_ESTADUAL', 'DEPUTADO_FEDERAL', 'SENADOR', 'GOVERNADOR', 'PRESIDENTE')),
    CONSTRAINT ck_candidato_numero CHECK (numero BETWEEN 1 AND 99999)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- PARTICIPACAO:
-- ---------------------------------------------------------------------
CREATE TABLE participacao (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    eleicao_id    BIGINT      NOT NULL,
    eleitor_id    BIGINT      NOT NULL,
    cargo         VARCHAR(30) NOT NULL,
    confirmado_em DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_participacao PRIMARY KEY (id),
    -- A trava definitiva contra voto duplicado fica no banco, não só no código:
    CONSTRAINT uk_participacao_eleicao_eleitor_cargo UNIQUE (eleicao_id, eleitor_id, cargo),
    CONSTRAINT fk_participacao_eleicao FOREIGN KEY (eleicao_id) REFERENCES eleicao (id),
    CONSTRAINT fk_participacao_eleitor FOREIGN KEY (eleitor_id) REFERENCES eleitor (id),
    CONSTRAINT ck_participacao_cargo CHECK (cargo IN
        ('DEPUTADO_ESTADUAL', 'DEPUTADO_FEDERAL', 'SENADOR', 'GOVERNADOR', 'PRESIDENTE'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- VOTO:
-- ---------------------------------------------------------------------
CREATE TABLE voto (
    id            CHAR(36) NOT NULL,
    candidato_id  BIGINT   NOT NULL,
    CONSTRAINT pk_voto PRIMARY KEY (id),
    CONSTRAINT fk_voto_candidato FOREIGN KEY (candidato_id) REFERENCES candidato (id),
    INDEX idx_voto_candidato (candidato_id)
) ENGINE = InnoDB;