-- Umbral de stock mínimo por producto, usado por la alerta de "stock bajo"
-- del Dashboard. 0 = sin umbral configurado (no genera alerta).
ALTER TABLE producto ADD COLUMN stock_minimo NUMERIC(14,2) NOT NULL DEFAULT 0;
