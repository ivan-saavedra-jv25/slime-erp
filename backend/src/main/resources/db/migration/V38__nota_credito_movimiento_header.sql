-- Los movimientos de inventario que genera una Nota de Crédito nacían sin
-- cabecera (header_id NULL), igual que los de Venta y Compra. Eso los dejaba
-- fuera del historial del módulo de Inventario y sin acceso a su pantalla de
-- detalle ni a sus exportaciones a PDF y Excel, que se sirven por cabecera.
--
-- Desde V38 la aplicación crea la cabecera al emitir y al anular. Este script
-- hace lo mismo hacia atrás, con las notas ya emitidas: una cabecera por nota y
-- por sentido (ENTRADA para la recuperación, SALIDA para la reversa de la
-- anulación), y engancha en ella los movimientos existentes.
--
-- Se recorre en un bloque PL/pgSQL porque hay que conocer el id de cada cabecera
-- recién insertada para actualizar sus movimientos, y un INSERT ... RETURNING no
-- permite mapearlo de vuelta a la nota que lo originó.

DO $$
DECLARE
    grupo   RECORD;
    nuevo_id BIGINT;
BEGIN
    FOR grupo IN
        SELECT nc.id            AS nota_credito_id,
               nc.tenant_id     AS tenant_id,
               nc.usuario_id    AS usuario_id,
               nc.bodega_id     AS bodega_id,
               nc.folio         AS folio,
               ncm.tipo         AS tipo_vinculo
        FROM nota_credito nc
        JOIN nota_credito_movimiento ncm ON ncm.nota_credito_id = nc.id
        JOIN movimiento_inventario mi    ON mi.id = ncm.movimiento_inventario_id
        WHERE mi.header_id IS NULL
        GROUP BY nc.id, nc.tenant_id, nc.usuario_id, nc.bodega_id, nc.folio, ncm.tipo
    LOOP
        INSERT INTO movimiento_inventario_header
            (tenant_id, tipo, bodega_origen_id, bodega_destino_id, usuario_id, observacion)
        VALUES (
            grupo.tenant_id,
            CASE WHEN grupo.tipo_vinculo = 'RECUPERACION' THEN 'ENTRADA' ELSE 'SALIDA' END,
            CASE WHEN grupo.tipo_vinculo = 'RECUPERACION' THEN NULL ELSE grupo.bodega_id END,
            CASE WHEN grupo.tipo_vinculo = 'RECUPERACION' THEN grupo.bodega_id ELSE NULL END,
            grupo.usuario_id,
            CASE WHEN grupo.tipo_vinculo = 'RECUPERACION'
                 THEN 'Recuperación de inventario por NC-' || LPAD(grupo.folio::TEXT, 6, '0')
                 ELSE 'Anulación de NC-' || LPAD(grupo.folio::TEXT, 6, '0')
            END
        )
        RETURNING id INTO nuevo_id;

        UPDATE movimiento_inventario mi
        SET header_id = nuevo_id
        FROM nota_credito_movimiento ncm
        WHERE ncm.movimiento_inventario_id = mi.id
          AND ncm.nota_credito_id = grupo.nota_credito_id
          AND ncm.tipo = grupo.tipo_vinculo
          AND mi.header_id IS NULL;
    END LOOP;
END $$;
