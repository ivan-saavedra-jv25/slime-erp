ALTER TABLE producto ADD COLUMN codigo_barra VARCHAR(64);

-- Único por tenant solo cuando está presente: un índice único parcial deja
-- que muchos productos sigan sin código de barra sin chocar entre sí.
CREATE UNIQUE INDEX uq_producto_tenant_codigo_barra
    ON producto (tenant_id, codigo_barra)
    WHERE codigo_barra IS NOT NULL;
