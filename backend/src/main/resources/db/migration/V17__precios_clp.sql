-- Convierte los precios de los productos del dataset de demostración (V13)
-- de una moneda genérica/USD a pesos chilenos (CLP).
-- Tasa de referencia: $900 CLP por 1 USD.
-- Solo afecta a los productos con SKU del dataset (SKU001...SKU050);
-- los productos creados manualmente que ya están en CLP quedan intactos.
UPDATE producto SET
    precio_venta = precio_venta * 900,
    precio_compra = precio_compra * 900
WHERE sku ~ '^SKU[0-9]{3}$';