-- =====================================================================
-- V2 - Dados iniciais (resolve o "ovo e a galinha": alguém precisa
-- existir para cadastrar os outros usuários).
--
-- Chefe da Seção padrão para desenvolvimento/demonstração:
--   login: chefe     senha: admin123
-- A coluna guarda o hash BCrypt (custo 10), nunca a senha pura.
-- Em produção real, troque a senha logo após o primeiro acesso.
-- =====================================================================
INSERT INTO usuario (nome, login, senha_hash, perfil)
VALUES ('Chefe da Seção', 'chefe',
        '$2a$10$TYnDZVB1O4xfWLtb//.qeOxDjM3mOhKFCx9.pGxwrKmtV6L4.XFAC',
        'CHEFE_SECAO');
