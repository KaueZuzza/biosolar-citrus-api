-- BioSolar Citrus - Mapa da Fazenda: area (poligono) de cada talhao desenhada sobre a imagem de satelite.
-- Tabela nova e independente: nao altera nenhuma tabela existente. SQL compativel com PostgreSQL e H2.
-- Talhao sem linha aqui aparece no mapa em posicao ILUSTRATIVA (calculada pela API, nunca gravada).

CREATE TABLE talhao_area (
    talhao_id      VARCHAR(10)      PRIMARY KEY REFERENCES talhao (id) ON DELETE CASCADE,
    coordenadas    VARCHAR(12000)   NOT NULL,
    vertices       INTEGER          NOT NULL,
    area_ha        DOUBLE PRECISION NOT NULL,
    centro_lat     DOUBLE PRECISION NOT NULL,
    centro_lng     DOUBLE PRECISION NOT NULL,
    atualizado_em  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_talhao_area_vertices CHECK (vertices BETWEEN 3 AND 200),
    CONSTRAINT ck_talhao_area_area CHECK (area_ha > 0),
    CONSTRAINT ck_talhao_area_centro CHECK (centro_lat BETWEEN -90 AND 90 AND centro_lng BETWEEN -180 AND 180)
);

COMMENT ON TABLE talhao_area IS 'Area de cada talhao no Mapa da Fazenda (poligono desenhado sobre o satelite). Sem linha: posicao ilustrativa.';
COMMENT ON COLUMN talhao_area.coordenadas IS 'Anel externo do poligono em GeoJSON: [[longitude, latitude], ...] (WGS 84, sem repetir o 1o ponto)';
COMMENT ON COLUMN talhao_area.area_ha IS 'Area geodesica do poligono calculada pela API (hectares)';
COMMENT ON COLUMN talhao_area.centro_lat IS 'Centro do poligono (latitude), usado para centralizar o mapa';
COMMENT ON COLUMN talhao_area.atualizado_em IS 'Quando a area foi desenhada ou alterada';
