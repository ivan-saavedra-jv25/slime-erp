SELECT setval(pg_get_serial_sequence('tenant', 'id'), (SELECT COALESCE(MAX(id), 1) FROM tenant));
