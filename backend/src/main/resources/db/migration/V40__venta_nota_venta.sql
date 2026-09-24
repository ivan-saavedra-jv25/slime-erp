-- Vínculo hacia arriba de la cadena: la venta pasa a conocer la nota de venta
-- que le dio origen (si existe), de modo que la trazabilidad pueda recorrer
-- Cotización -> Nota de Venta -> Venta -> Nota de Crédito -> Nota de Débito.
-- La dirección contraria ya existe: nota_venta_documento enlaza la NV con los
-- documentos posteriores (Guía/Factura/Pago, y ahora también Venta).
ALTER TABLE venta ADD COLUMN nota_venta_id BIGINT REFERENCES nota_venta(id);
CREATE INDEX idx_venta_nota_venta ON venta(tenant_id, nota_venta_id);