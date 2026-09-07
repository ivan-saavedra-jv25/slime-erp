-- Categorías (3 columnas: tenant_id, nombre, activo)
INSERT INTO categoria (tenant_id, nombre, activo)
SELECT t.id, d.nombre, TRUE
FROM tenant t
CROSS JOIN (VALUES
    ('Electrónica'),
    ('Computación'),
    ('Oficina'),
    ('Cocina'),
    ('Climatización'),
    ('Herramientas'),
    ('Audio y Video'),
    ('Salud y Cuidado Personal'),
    ('Deportes y Fitness'),
    ('Auto y Moto')
) AS d(nombre);

-- Subcategorías (4 columnas: tenant_id, categoria_id, nombre, activo)
-- Cada fila VALUES mapea a d(nombre, categoria_nombre); se une contra
-- categoria por nombre para obtener el id real (evita depender de que los
-- ids se hayan asignado en el mismo orden de este INSERT).
INSERT INTO subcategoria (tenant_id, categoria_id, nombre, activo)
SELECT t.id, c.id, d.nombre, TRUE
FROM tenant t
JOIN categoria c ON c.tenant_id = t.id
JOIN (VALUES
    ('Laptops y Computadores', 'Electrónica'),
    ('Accesorios PC', 'Electrónica'),
    ('Periféricos', 'Electrónica'),
    ('Tablets y E-readers', 'Computación'),
    ('Accesorios Tablet', 'Computación'),
    ('Impresoras y Suministros', 'Electrónica'),
    ('Oficina y Papelería', 'Oficina'),
    ('Cocina y Ahorro', 'Cocina'),
    ('Climatización y Aire', 'Climatización'),
    ('Herramientas Manuales', 'Herramientas'),
    ('Iluminación', 'Herramientas'),
    ('Audio Portátil', 'Audio y Video'),
    ('Audio Home Theater', 'Audio y Video'),
    ('Termal y Cuidado', 'Salud y Cuidado Personal'),
    ('Monitoreo Salud', 'Salud y Cuidado Personal'),
    ('Equipo Cardio', 'Deportes y Fitness'),
    ('Accesorios Deporte', 'Deportes y Fitness'),
    ('Repuestos Auto', 'Auto y Moto'),
    ('Limpieza Auto', 'Auto y Moto')
) AS d(nombre, categoria_nombre) ON d.categoria_nombre = c.nombre;

-- Productos (9 columnas: tenant_id, sku, nombre, descripcion, precio_venta, precio_compra, categoria_id, subcategoria_id, activo)
INSERT INTO producto (tenant_id, sku, nombre, descripcion, precio_venta, precio_compra, categoria_id, subcategoria_id, activo)
SELECT t.id, d.sku, d.nombre, d.descripcion, d.precio_venta, d.precio_compra, d.categoria_id, d.subcategoria_id, TRUE
FROM tenant t
CROSS JOIN (VALUES
    ('SKU001', 'Laptop Gaming HP',        'Laptop de alto rendimiento para juegos y renderizado',      1250.00,  850.00, 1, 1),
    ('SKU002', 'Mouse Inalámbrico',        'Mouse ergonómico con conexión USB inalámbrica',           25.00,    12.00,  1, 3),
    ('SKU003', 'Teclado Mecánico',         'Teclado gaming con retroiluminación RGB',                 80.00,    45.00,  1, 3),
    ('SKU004', 'Monitor 24 pulgadas',        'Monitor Full HD 1920x1080 con panel IPS',                180.00,   110.00, 1, 1),
    ('SKU005', 'Impresora Multifunción',     'Impresora láser con escáner y copiadora duplex',          220.00,   140.00, 1, 3),
    ('SKU006', 'Tablet 10 pulgadas',         'Tableta con sistema operativo Android 13',               350.00,   220.00,  2, 4),
    ('SKU007', 'Auriculares Bluetooth',      'Auriculares inalámbricos con cancelación de ruido',       65.00,    35.00,  7, 12),
    ('SKU008', 'Cargador Portátil 45W',      'Cargador rápido USB-C 45W para laptops',                  45.00,    25.00,  1, 2),
    ('SKU009', 'Batería Externa 10000mAh',   'Batería portátil con carga rápida USB-C',                35.00,    20.00,  7, 3),
    ('SKU010', 'Alfombrilla para Mouse',      'Alfombrilla grande de goma anti-deslizante',             15.00,    8.00,   1, 3),
    ('SKU011', 'Mochila para Laptop',        'Mochila acolchada con compartimento para tablet',         45.00,    28.00,  1, 3),
    ('SKU012', 'Pen Drive 64GB',             'Memoria USB 3.0 de alta velocidad 64GB',                12.00,    7.00,   1, 3),
    ('SKU013', 'Disco Duro Externo 1TB',     'Almacenamiento portátil 1TB USB 3.0',                   95.00,    65.00,  1, 3),
    ('SKU014', 'Router WiFi Dual Band',      'Router inalámbrico AC1900 con velocidades dual-band',     120.00,   75.00,  1, 3),
    ('SKU015', 'Cámara Web HD 1080p',        'Cámara para videoconferencias con enfoque automático',     45.00,    28.00,  1, 3),
    ('SKU016', 'Impresora de Etiquetas',     'Impresora térmica 203dpi para etiquetas de producto',    180.00,   120.00, 1, 3),
    ('SKU017', 'Scanner de Código de Barras', 'Scanner láser de alta velocidad para códigos de barras', 110.00,   70.00,  1, 3),
    ('SKU018', 'Webcam Profesional',         'Cámara Full HD 1080p para streaming y grabación',         200.00,   130.00, 7, 12),
    ('SKU019', 'Hub USB 4 Puertos',          'Centralizador USB con 4 puertos adicionales y carga',    35.00,    22.00,  1, 3),
    ('SKU020', 'Extensión HDMI 2m',        'Cable HDMI 4K de alta velocidad 2 metros',              25.00,    15.00,  1, 3),
    ('SKU021', 'Kit Limpieza Electrónica',  'Kit con aire comprimido y paños microfibra para equipos',  20.00,    12.00,  1, 3),
    ('SKU022', 'Fuente de Poder 600W',       'Fuente ATX 600W certificación 80+ para PC de escritorio',110.00,   70.00,  1, 1),
    ('SKU023', 'Memoria RAM 16GB DDR4',      'Módulo de memoria RAM 16GB DDR4 3200MHz',               85.00,    55.00,  1, 1),
    ('SKU024', 'SSD 512GB M.2',              'Disco sólido estado NVMe 512GB para laptops modernas',   130.00,   85.00,  1, 1),
    ('SKU025', 'Cooling Pad Laptop',         'Base refrigerante con 4 ventiladores y control de velocidad', 60.00,    38.00,  1, 1),
    ('SKU026', 'Teclado Mecánico Compacto',  'Teclado sin cable con switches rojos y iluminación RGB', 100.00,   65.00,  1, 3),
    ('SKU027', 'Mouse Pad RGB',              'Alfombrilla con iluminación LED de 7 colores personalizable', 30.00,    18.00,  1, 3),
    ('SKU028', 'Soporte para Monitor Articulado', 'Soporte VESA con ajuste de altura y ángulo',            45.00,    30.00,  1, 1),
    ('SKU029', 'Cámara de Seguridad Exterior', 'Cámara IP resistente IP66 con visión nocturna y audio',   250.00,   160.00, 8, 14),
    ('SKU030', 'Disco Externo 4TB',          'Almacenamiento portátil 4TB USB 3.0 con velocidad alta', 320.00,   210.00, 1, 3),
    ('SKU031', 'Tablet Teclado 10.1 pulgadas', 'Tablet con teclado físico desmontable y batería de larga duración', 420.00,   275.00, 2, 4),
    ('SKU032', 'Reloj Inteligente',          'Smartwatch con pantalla AMOLED, seguimiento de salud y notificaciones', 180.00,   115.00, 9, 15),
    ('SKU033', 'Brazalete de Actividad',     'Tracker de actividad física con monitoreo de pasos, sueño y frecuencia cardíaca', 85.00,    55.00,  9, 15),
    ('SKU034', 'Altavoz Bluetooth Portátil', 'Altavoz compacto con batería de 10h de reproducción',    120.00,   78.00,  7, 12),
    ('SKU035', 'Proyector Portátil LED',     'Proyector mini con corrección keystone y altavoz integrado', 350.00,   230.00, 7, 12),
    ('SKU036', 'Termómetro Digital Infrarrojo', 'Termómetro rápido para adultos y niños, memoria de última medición', 35.00,    22.00,  8, 14),
    ('SKU037', 'Esfigmomanómetro Digital',   'Monitor de presión arterial con memoria para 2 usuarios y indicador de arritmia', 65.00,    42.00,  8, 14),
    ('SKU038', 'Pulsoxímetro Digital',       'Pulsoxímetro para medir SpO2 y frecuencia cardíaca, display LED', 55.00,    35.00,  8, 14),
    ('SKU039', 'Batidor de Mano Eléctrico',  'Batidor potente 300W con 5 velocidades y accesorio para picar', 45.00,    30.00,  4, 4),
    ('SKU040', 'Licuadora de Mesa',          'Licuadora de alta potencia 1500W con vaso de vidrio',    110.00,   72.00,  4, 4),
    ('SKU041', 'Cafetera Express Automática', 'Cafetera con molinillo integrado, 15 presiones y panel táctil', 280.00,   180.00, 4, 4),
    ('SKU042', 'Tostadora 2 Rebanadas',      'Tostadora con bandeja para migas extraíble y 7 niveles de tostado', 70.00,    45.00,  4, 4),
    ('SKU043', 'Plancha de Vapor',           'Plancha con suela de cerámica, regulación de temperatura y función eco', 85.00,    55.00,  4, 4),
    ('SKU044', 'Ventilador de Torre',        'Ventilador con control remoto, modo nocturno y ionizador', 130.00,   85.00,  5, 4),
    ('SKU045', 'Deshumidificador 15L',       'Deshumidificador para ambientes grandes con bomba continua', 220.00,   145.00,  5, 4),
    ('SKU046', 'Aire Acondicionado 12000 BTU', 'Equipo de aire acondicionado para habitaciones de 20-30 m²', 580.00,   380.00,  5, 4),
    ('SKU047', 'Calentador de Agua Solar',   'Sistema doméstico de calentamiento solar para acumulador de 150L', 890.00,   590.00,  5, 4),
    ('SKU048', 'Cerradura Digital',          'Cerradura electrónica con huella digital, tarjeta y código de emergencia', 210.00,   135.00, 10, 10),
    ('SKU049', 'Sistema de Alarma Hogar',    'Sistema completo con panel de control, 3 sensores movimiento, 1 sensor puerta y 1 cámara', 450.00,   295.00, 10, 10),
    ('SKU050', 'Extintor de Incendios ABC 5kg', 'Extintor químico seco para clases A, B y C, presión garantizada 5 años', 95.00,    65.00,  10, 10)
) AS d(sku, nombre, descripcion, precio_venta, precio_compra, categoria_id, subcategoria_id);