-- BioSolar Citrus - cadastro gerenciavel pela interface (CRUD de talhoes, reservatorio e usina),
-- visoes de consulta e documentacao das tabelas (exibida no pgAdmin).
-- Nao remove dados. SQL compativel com PostgreSQL e H2 (modo PostgreSQL).

-- ---------------------------------------------------------------------------------------------
-- Talhoes: arquivamento (exclusao que preserva o historico) e umidade do cenario de demonstracao
-- ---------------------------------------------------------------------------------------------
ALTER TABLE talhao ADD COLUMN ativo BOOLEAN DEFAULT TRUE NOT NULL;

ALTER TABLE talhao ADD COLUMN umidade_inicial DOUBLE PRECISION;
-- Talhoes do cenario original: mesma umidade inicial usada ate aqui pelo "Restaurar cenario"
UPDATE talhao SET umidade_inicial = CASE id
    WHEN 'A' THEN 62 WHEN 'B' THEN 41 WHEN 'C' THEN 31 WHEN 'D' THEN 57 ELSE umidade END;
ALTER TABLE talhao ALTER COLUMN umidade_inicial SET NOT NULL;
ALTER TABLE talhao ADD CONSTRAINT ck_talhao_umidade_inicial CHECK (umidade_inicial BETWEEN 0 AND 100);

-- Valores fisicos validos (a API valida as mesmas faixas; aqui protege tambem as edicoes feitas pelo pgAdmin)
ALTER TABLE talhao ADD CONSTRAINT ck_talhao_valores CHECK (
    area_ha > 0 AND plantas >= 0 AND prioridade BETWEEN 1 AND 3 AND taxa_evapotranspiracao >= 0
    AND ganho_irrigacao > 0 AND vazao_bomba_m3h > 0 AND potencia_bomba_kw > 0 AND umidade_alvo < 85);

-- ---------------------------------------------------------------------------------------------
-- Reservatorio e usina
-- ---------------------------------------------------------------------------------------------
ALTER TABLE reservatorio ADD COLUMN nivel_inicial DOUBLE PRECISION;
UPDATE reservatorio SET nivel_inicial = 67;
ALTER TABLE reservatorio ALTER COLUMN nivel_inicial SET NOT NULL;
ALTER TABLE reservatorio ADD CONSTRAINT ck_reservatorio_nivel_inicial CHECK (nivel_inicial BETWEEN 0 AND 100);
ALTER TABLE reservatorio ADD CONSTRAINT ck_reservatorio_recarga CHECK (vazao_recarga_m3h >= 0);

ALTER TABLE estado_simulacao ADD CONSTRAINT ck_estado_simulacao_potencia CHECK (potencia_solar_pico_kw >= 0);

-- ---------------------------------------------------------------------------------------------
-- Visoes de consulta (nao guardam dados: leem as tabelas acima)
-- ---------------------------------------------------------------------------------------------
-- Cada talhao ativo tem um conjunto motobomba + aspersor (MB-x) e um sensor de umidade (SU-x)
CREATE VIEW vw_aspersores AS
SELECT t.id AS talhao_id, t.nome AS talhao, 'MB-' || t.id AS bomba_id, 'SU-' || t.id AS sensor_umidade_id,
       t.aspersor_ligado, t.modo_acionamento, t.irrigacao_bloqueada, t.umidade, t.status,
       t.vazao_bomba_m3h, t.potencia_bomba_kw, t.ganho_irrigacao, t.ultimo_acionamento
FROM talhao t
WHERE t.ativo;

-- Leituras do historico por talhao, com horario e nivel do reservatorio da mesma amostra
CREATE VIEW vw_leituras_talhao AS
SELECT l.id AS leitura_id, l.instante, l.hora_simulada, lt.talhao_id, lt.umidade, lt.aspersor_ligado,
       l.nivel_reservatorio, l.indice, l.bloqueio_emergencia
FROM leitura_talhao lt
JOIN leitura_telemetria l ON l.id = lt.leitura_id;

-- ---------------------------------------------------------------------------------------------
-- Documentacao (aparece em Propriedades/Comentario no pgAdmin)
-- ---------------------------------------------------------------------------------------------
COMMENT ON TABLE talhao IS 'Talhoes do pomar. Cadastro (nome, cultura, limites, bomba) editavel pela aba Gestao ou aqui; estado operacional (umidade, aspersor, status) gravado pela automacao a cada segundo.';
COMMENT ON COLUMN talhao.ativo IS 'false = arquivado: fora da automacao, historico preservado';
COMMENT ON COLUMN talhao.umidade IS 'Umidade atual do solo (%), gravada pela automacao';
COMMENT ON COLUMN talhao.umidade_inicial IS 'Umidade usada ao restaurar o cenario de demonstracao (%)';
COMMENT ON COLUMN talhao.limite_critico IS 'Abaixo deste valor a irrigacao critica (regra P2) liga o aspersor (%)';
COMMENT ON COLUMN talhao.limite_atencao IS 'Abaixo deste valor o talhao entra em atencao (%)';
COMMENT ON COLUMN talhao.umidade_alvo IS 'A irrigacao automatica termina ao atingir este valor (%)';
COMMENT ON COLUMN talhao.taxa_evapotranspiracao IS 'Perda de umidade no pico de sol (% por hora)';
COMMENT ON COLUMN talhao.ganho_irrigacao IS 'Ganho de umidade com o aspersor ligado (% por hora)';
COMMENT ON COLUMN talhao.aspersor_ligado IS 'Motobomba + aspersor do talhao ligados';
COMMENT ON COLUMN talhao.modo_acionamento IS 'AUTOMATICO (servidor), MANUAL (operador) ou DESLIGADO';
COMMENT ON TABLE reservatorio IS 'Reservatorio central (linha unica, id = 1). Nivel gravado pela automacao; nome, capacidade, recarga e nivel inicial editaveis.';
COMMENT ON COLUMN reservatorio.nivel IS 'Nivel atual (% da capacidade)';
COMMENT ON COLUMN reservatorio.limite_critico IS 'Abaixo deste nivel todas as bombas sao bloqueadas (regra P1, %)';
COMMENT ON COLUMN reservatorio.limite_rearme IS 'O bloqueio so e liberado ao atingir este nivel (%)';
COMMENT ON COLUMN reservatorio.nivel_inicial IS 'Nivel usado ao restaurar o cenario de demonstracao (%)';
COMMENT ON TABLE estado_simulacao IS 'Relogio da fazenda, velocidade, acumuladores de agua e energia e potencia da usina solar (linha unica, id = 1)';
COMMENT ON TABLE evento IS 'Historico: irrigacoes automaticas, comandos, bloqueios, alertas, cadastro e simulacao';
COMMENT ON TABLE leitura_telemetria IS 'Amostras periodicas da fazenda (base dos graficos e do relatorio)';
COMMENT ON TABLE leitura_talhao IS 'Umidade e aspersor de cada talhao em uma amostra de leitura_telemetria';
COMMENT ON VIEW vw_aspersores IS 'Motobombas, aspersores e sensores de umidade dos talhoes ativos';
COMMENT ON VIEW vw_leituras_talhao IS 'Historico de umidade por talhao com horario e nivel do reservatorio';
