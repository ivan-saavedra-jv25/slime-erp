-- Tenant de plataforma y su usuario SUPER_ADMIN.
-- La consola administrativa (admin-backend) solo acepta login de rol SUPER_ADMIN
-- y no gestiona el tenant de plataforma desde las secciones de empresas.

INSERT INTO tenant (id, nombre, rut, plan, activo)
VALUES (2, 'Plataforma Slime ERP', '99.999.999-9', 'plataforma', TRUE);

INSERT INTO usuario (tenant_id, email, rut, password_hash, nombre, rol, activo)
VALUES (
    2,
    'super@slimerp.cl',
    '99.888.777-6',
    '$2b$10$qoxzrj3gH5e05XaFkk5ntuCzsk8tkj82nQEkmZkjs900M0PJ6rFZm', -- password: Super123!
    'Super Administrador',
    'SUPER_ADMIN',
    TRUE
);

SELECT setval(pg_get_serial_sequence('tenant', 'id'),
              (SELECT COALESCE(MAX(id), 1) FROM tenant));
SELECT setval(pg_get_serial_sequence('usuario', 'id'),
              (SELECT COALESCE(MAX(id), 1) FROM usuario));