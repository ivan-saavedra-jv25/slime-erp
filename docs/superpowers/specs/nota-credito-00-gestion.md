# NC-001 — Gestión del desarrollo

## Objetivo

Dejar el desarrollo del módulo dividido en tareas independientes, con `tasks.json` como
estado general y un archivo por tarea dentro de `specs/`.

## Estructura

```
nota-credito/
├── tasks.json
└── specs/
    ├── 00-gestion.md               NC-001
    ├── 01-modelo-datos.md          NC-002
    ├── 02-permisos.md              NC-003
    ├── 03-documento-asociado.md    NC-004
    ├── 04-tipo-correccion.md       NC-005
    ├── 05-estados.md               NC-006
    ├── 06-productos-inventario.md  NC-007
    ├── 07-acciones-y-listado.md    NC-008
    ├── 08-pdf.md                   NC-009
    ├── 09-frontend-base.md         NC-010
    ├── 10-frontend-listado.md      NC-011
    ├── 11-frontend-formulario.md   NC-012
    └── 12-frontend-detalle.md      NC-013
```

## Regla de implementación

1. Revisar `tasks.json`.
2. Seleccionar una tarea `pending` respetando el orden de dependencias.
3. Cambiar su estado a `working`.
4. Leer el spec correspondiente.
5. Implementar.
6. Validar (compilar backend / `ng test` frontend / prueba manual cuando aplique).
7. Cambiar el estado a `completed`.
8. Continuar con la siguiente.

Una tarea **no** se marca `completed` si su funcionalidad no está implementada y validada.

## Orden de dependencias

```
NC-001
  └── NC-002 (modelo de datos)
        ├── NC-003 (permisos)
        └── NC-004 → NC-005 → NC-006 → NC-007 → NC-008 → NC-009
                                                            └── NC-010 → NC-011
                                                                      ├── NC-012
                                                                      └── NC-013
```

NC-003 puede hacerse en paralelo con NC-004; el resto del backend es secuencial porque
cada tarea añade métodos al mismo `NotaCreditoService`.

## Alcance del módulo

Incluye: emisión, corrección de documentos de venta, recuperación de inventario,
estados, trazabilidad, listado con filtros, PDF y dashboard.

**No incluye**: XML, DTE, CAF ni envío al SII; efectos en cuentas por cobrar o tesorería;
Notas de Crédito sobre Cotizaciones o Notas de Venta; notas de débito; Libro de Notas de
Crédito en `reporteria`.

## Validación

- `nota-credito/tasks.json` es JSON válido y todas las rutas de `spec` existen.
- Los estados usados son únicamente `pending`, `working` y `completed`.
