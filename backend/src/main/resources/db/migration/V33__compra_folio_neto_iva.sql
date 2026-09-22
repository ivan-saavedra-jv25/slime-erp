ALTER TABLE compra ADD COLUMN numero_documento VARCHAR(50);
ALTER TABLE compra ADD COLUMN monto_neto NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE compra ADD COLUMN monto_iva NUMERIC(14,2) NOT NULL DEFAULT 0;

-- Backfill de compras existentes: los precios ingresados en el formulario de
-- Compra son netos (sin IVA), por lo que "total" ya es el monto neto y el IVA
-- se calcula por encima de él (19%).
UPDATE compra
SET monto_neto = total,
    monto_iva  = ROUND(total * 0.19, 2)
WHERE total > 0;
