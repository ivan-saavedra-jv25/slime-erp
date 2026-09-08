-- Contador de folios por tenant y tipo de documento (Factura, Factura Exenta,
-- Boleta, Boleta Exenta, Voucher). Un folio no es correlativo global: cada
-- tipo lleva su propia numeración, empezando en 1, igual que en el SII.
CREATE TABLE folio_venta_contador (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    clave VARCHAR(20) NOT NULL,
    ultimo_folio INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_folio_venta_contador_tenant_clave UNIQUE (tenant_id, clave)
);

-- codigo_sii: código de documento tributario electrónico del SII (33/34/39/41).
-- Voucher no es un documento tributario real, así que queda NULL.
ALTER TABLE venta ADD COLUMN folio INTEGER;
ALTER TABLE venta ADD COLUMN codigo_sii INTEGER;

-- Backfill: numera las ventas ya existentes en orden de fecha, agrupadas por
-- tenant + tipo (misma clave que usará el contador de aquí en adelante).
-- Se numeran también las inactivas (anuladas): un folio ya emitido no se
-- reutiliza ni se salta, igual que un documento tributario real.
WITH clasificacion AS (
    SELECT id, tenant_id,
           CASE
               WHEN tipo_documento = 'FACTURA' AND exento THEN 'Factura Exenta'
               WHEN tipo_documento = 'FACTURA' THEN 'Factura'
               WHEN tipo_documento = 'BOLETA' AND exento THEN 'Boleta Exenta'
               WHEN tipo_documento = 'BOLETA' THEN 'Boleta'
               ELSE 'Voucher'
           END AS clave,
           CASE
               WHEN tipo_documento = 'FACTURA' AND exento THEN 34
               WHEN tipo_documento = 'FACTURA' THEN 33
               WHEN tipo_documento = 'BOLETA' AND exento THEN 41
               WHEN tipo_documento = 'BOLETA' THEN 39
               ELSE NULL
           END AS codigo_sii
    FROM venta
),
numeradas AS (
    SELECT id, clave, codigo_sii,
           ROW_NUMBER() OVER (PARTITION BY tenant_id, clave ORDER BY id) AS folio_calculado
    FROM clasificacion
)
UPDATE venta
SET folio = numeradas.folio_calculado,
    codigo_sii = numeradas.codigo_sii
FROM numeradas
WHERE venta.id = numeradas.id;

ALTER TABLE venta ALTER COLUMN folio SET NOT NULL;

-- Deja el contador de cada tenant+tipo apuntando al último folio ya asignado,
-- para que la próxima venta de ese tipo siga la numeración sin repetir.
INSERT INTO folio_venta_contador (tenant_id, clave, ultimo_folio)
SELECT tenant_id,
       CASE
           WHEN tipo_documento = 'FACTURA' AND exento THEN 'Factura Exenta'
           WHEN tipo_documento = 'FACTURA' THEN 'Factura'
           WHEN tipo_documento = 'BOLETA' AND exento THEN 'Boleta Exenta'
           WHEN tipo_documento = 'BOLETA' THEN 'Boleta'
           ELSE 'Voucher'
       END AS clave,
       MAX(folio) AS ultimo_folio
FROM venta
GROUP BY tenant_id, clave;
