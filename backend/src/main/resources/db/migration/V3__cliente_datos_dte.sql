-- Datos tributarios del receptor DTE (se completan al emitir facturación electrónica, no al crear el cliente)

ALTER TABLE cliente ADD COLUMN razon_social VARCHAR(200);
ALTER TABLE cliente ADD COLUMN giro         VARCHAR(100);
ALTER TABLE cliente ADD COLUMN comuna       VARCHAR(60);
ALTER TABLE cliente ADD COLUMN ciudad       VARCHAR(60);
