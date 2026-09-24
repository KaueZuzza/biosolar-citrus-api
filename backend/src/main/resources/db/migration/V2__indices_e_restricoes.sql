-- BioSolar Citrus - indices para as consultas de indicadores/relatorio e restricoes de integridade.
-- Nao altera nem remove dados. SQL compativel com PostgreSQL e H2 (modo PostgreSQL).

-- Contagem de acionamentos por talhao e tipo (GET /indicadores a cada 5 s e GET /relatorio)
CREATE INDEX idx_evento_talhao_tipo ON evento (talhao_id, tipo);

-- Resumo de umidade por talhao no relatorio (tambem acelera a verificacao da FK ao remover talhoes)
CREATE INDEX idx_leitura_talhao_talhao ON leitura_talhao (talhao_id);

-- Tabelas de linha unica: o codigo sempre usa id = 1
ALTER TABLE reservatorio ADD CONSTRAINT ck_reservatorio_linha_unica CHECK (id = 1);
ALTER TABLE estado_simulacao ADD CONSTRAINT ck_estado_simulacao_linha_unica CHECK (id = 1);

-- Protecao hidrica: o rearme precisa ficar acima do limite critico (histerese) e abaixo do de atencao
ALTER TABLE reservatorio ADD CONSTRAINT ck_reservatorio_limites
    CHECK (capacidade_m3 > 0 AND limite_critico < limite_rearme AND limite_rearme <= limite_atencao);

-- Faixas de umidade coerentes: critico < atencao <= alvo da irrigacao
ALTER TABLE talhao ADD CONSTRAINT ck_talhao_limites
    CHECK (limite_critico < limite_atencao AND limite_atencao <= umidade_alvo);

-- Aspersor ligado sempre tem um modo (AUTOMATICO/MANUAL); desligado sempre tem modo DESLIGADO
ALTER TABLE talhao ADD CONSTRAINT ck_talhao_aspersor_modo
    CHECK ((aspersor_ligado AND modo_acionamento <> 'DESLIGADO')
        OR (NOT aspersor_ligado AND modo_acionamento = 'DESLIGADO'));

-- Mesma faixa validada pela API (POST /simulacao/velocidade)
ALTER TABLE estado_simulacao ADD CONSTRAINT ck_estado_simulacao_fator CHECK (fator_velocidade BETWEEN 1 AND 60);
