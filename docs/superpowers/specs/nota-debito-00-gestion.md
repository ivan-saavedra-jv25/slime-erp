# ND-001 — Gestión del desarrollo

## Objetivo

Dejar el desarrollo del módulo dividido en tareas independientes, con `tasks.json` como
estado general y un archivo por tarea dentro de `specs/`.

## Estructura

```
nota-debito/
├── tasks.json
└── specs/
    ├── 00-gestion.md               ND-001
    ├── 01-modelo-datos.md          ND-002
    ├── 02-permisos.md              ND-003
    ├── 03-documento-asociado.md    ND-004
    ├── 04-tipo-reversion.md        ND-005
    ├── 05-estados.md               ND-006
    ├── 06-inventario-trazabilidad.md ND-007
    ├── 07-acciones-y-listado.md    ND-008
    ├── 08-pdf.md                   ND-009
    ├── 09-frontend-base.md         ND-010
    ├── 10-frontend-listado.md      ND-011
    ├── 11-frontend-formulario.md   ND-012
    └── 12-frontend-detalle.md      ND-013
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
ND-001
  └── ND-002 (modelo de datos)
        ├── ND-003 (permisos)
        └── ND-004 → ND-005 → ND-006 → ND-007 → ND-008 → ND-009
                                                                └── ND-010 → ND-011
                                                                          ├── ND-012
                                                                          └── ND-013
```

ND-003 puede hacerse en paralelo con ND-004; el resto del backend es secuencial porque
cada tarea añade métodos al mismo `NotaDebitoService`.

## Alcance del módulo

Incluye: anulación o reversión de una Nota de Crédito emitida, reversión de sus efectos
(incluida la recuperación de inventario), estados, trazabilidad completa
(Venta → NC → ND), listado con filtros, PDF y dashboard.

**No incluye**: XML, DTE, CAF ni envío al SII; efectos en cuentas por cobrar o tesorería;
Notas de Débito sobre Cotizaciones o Notas de Venta; notas de crédito; Libro de Notas de
Débito en `reporteria`.

## Reglas de negocio del módulo

* Toda Nota de Débito debe estar asociada a una Nota de Crédito `EMITIDA`.
* No permitir asociar una NC inexistente, borrador o anulada.
* No permitir revertir más monto del disponible de la NC.
* No permitir revertir dos veces el mismo efecto (anti doble reversión).
* Una ND emitida no puede modificarse.
* La anulación de una ND debe revertir sus propios efectos.
* Mantener trazabilidad completa entre los documentos relacionados.

## Validación

- `nota-debito/tasks.json` es JSON válido y todas las rutas de `spec` existen.
- Los estados usados son únicamente `pending`, `working` y `completed`.