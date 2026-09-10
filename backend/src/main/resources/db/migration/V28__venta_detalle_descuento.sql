-- Descuento por línea de detalle, además del descuento global ya existente
-- en la cabecera de la venta.
ALTER TABLE venta_detalle ADD COLUMN descuento NUMERIC(14,2) NOT NULL DEFAULT 0;
