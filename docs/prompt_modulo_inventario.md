# Prompt — Módulo de Inventario con Filtros

## Objetivo

Desarrollar un **módulo de Inventario** para un sistema ERP/administrativo que permita consultar, filtrar, buscar y visualizar el stock de productos.

Usar la imagen de referencia como guía visual para estructura, jerarquía, densidad de información, proporciones, colores y disposición.

### Exclusiones

No implementar:

- Menú lateral.
- Barra superior del sistema.
- Búsqueda por lote.
- Sección "BUSCAR POR LOTE".
- Columna "Lotes".
- Cualquier funcionalidad relacionada con lotes.

El módulo debe comenzar directamente con **FILTROS STOCK**.

---

# 1. FILTROS STOCK

Crear un panel titulado:

`FILTROS STOCK`

Dentro incluir 4 tarjetas de resumen:

### Stock Crítico
- Color rojo.
- Icono de alerta.
- Texto `STOCK CRÍTICO`.
- Cantidad dinámica.

### Stock Reposición
- Color verde claro.
- Icono de alerta.
- Texto `STOCK REPOSICIÓN`.
- Cantidad dinámica.

### Stock Normal
- Color verde.
- Icono de check.
- Texto `STOCK NORMAL`.
- Cantidad dinámica.

### Stock Exceso
- Color naranja.
- Icono de flecha hacia arriba.
- Texto `STOCK EXCESO`.
- Cantidad dinámica.

Las cantidades deben calcularse a partir de los datos reales del inventario.

---

# 2. Filtros rápidos por estado

Crear botones:

```text
[ Todos ] [ Stock Crítico ] [ Stock Reposición ] [ Stock Normal ] [ Stock Exceso ] [ Limpiar Filtros ]
```

Comportamiento:

- **Todos:** muestra todos los productos.
- **Stock Crítico:** filtra productos en estado crítico.
- **Stock Reposición:** filtra productos que necesitan reposición.
- **Stock Normal:** muestra productos dentro del rango normal.
- **Stock Exceso:** muestra productos sobre el máximo.
- **Limpiar Filtros:** restaura todos los filtros, búsqueda y paginación.

---

# 3. Filtro de inventario específico

Crear un panel titulado:

`FILTRO DE INVENTARIO ESPECÍFICO`

Controles requeridos:

### Bodega

Select dinámico:

```text
Bodega [ Todos / Restaurante 2 / ... ]
```

### Familia

Select:

```text
Familia [ Todos ]
```

### Sub Familia

Select:

```text
Sub Familia [ Todos ]
```

Puede depender de la familia seleccionada.

### Tipo

Select:

```text
Tipo [ Todos ]
```

### Categoría

Select:

```text
Categoría [ Todos ]
```

### Ordenar por Stock

Checkbox:

```text
☐ Ordenar por Stock
```

Permitir orden ascendente o descendente.

### Ver Deshabilitados

Checkbox:

```text
☐ Ver Deshabilitados
```

Por defecto desactivado. Al activarlo, incluir productos deshabilitados.

### Exportación CSV

Botón:

```text
[ Descargar .CSV ]
```

Exportar los resultados respetando todos los filtros activos.

### Exportación XLSX

Botón:

```text
[ Descargar XLSX ]
```

Generar Excel con los resultados filtrados.

---

# 4. Filtro por tipo de búsqueda

Crear un panel titulado:

`FILTRO POR TIPO DE BÚSQUEDA`

### Tipo de búsqueda

Select con opciones:

```text
Código Barra
Código Producto
Nombre Producto
SKU
```

### Buscador

Input:

```text
Buscador de Productos
```

### Buscar

Botón azul:

```text
[ Buscar ]
```

La búsqueda debe utilizar el tipo seleccionado y combinarse con los demás filtros.

---

# 5. Importador de mínimos, reposición y máximos

Crear un panel titulado:

`IMPORTADOR Minimo Reposicion Maximo`

### Descargar ejemplo

Botón verde:

```text
[ Descargar Ejemplo ]
```

Descargar plantilla Excel.

### Importar Excel

Botón azul:

```text
[ Importar Excel ]
```

Permitir archivos `.xlsx`.

Columnas esperadas:

```text
Producto
Mínimo
Reposición
Máximo
```

Validar:

- Extensión.
- Columnas obligatorias.
- Datos numéricos.
- Valores no negativos.
- Existencia del producto.
- Duplicados.
- Errores por fila.

Mostrar resumen:

```text
Importados: 95
Actualizados: 90
Con errores: 5
```

No realizar actualizaciones parciales de forma silenciosa.

---

# 6. Tabla INVENTARIO

Crear un panel titulado:

`INVENTARIO`

Antes de la tabla incluir:

```text
[ 10 ▼ ] Registros
```

Opciones:

```text
10
25
50
100
```

---

# 7. Columnas

La tabla debe contener exactamente:

| # | Producto | Código | Código Barra | Tipo | Mínimo | Reposición | Máximo | Stock | Histórico |
|---|---|---|---|---|---:|---:|---:|---:|---|

### #

Identificador interno del registro/producto.

### Producto

Nombre del producto.

### Código

Código interno del producto.

### Código Barra

Código de barras asociado.

### Tipo

Tipo de producto, por ejemplo `producto`.

### Mínimo

Stock mínimo configurado.

### Reposición

Nivel/cantidad configurado para reposición.

### Máximo

Stock máximo configurado.

### Stock

Stock actual mediante badge visual.

Estados:

- Crítico → rojo.
- Reposición → amarillo/naranja.
- Normal → verde.
- Exceso → naranja.

La clasificación debe calcularse usando:

```text
stock
mínimo
reposición
máximo
```

No depender únicamente de un color almacenado en la base de datos.

### Histórico

Botón por registro:

```text
[ Ver Histórico ]
```

Abrir modal o vista secundaria con el historial de movimientos.

Mostrar como mínimo:

```text
Fecha
Producto
Tipo movimiento
Cantidad
Stock anterior
Stock posterior
Usuario
Documento / referencia
Observación
```

Tipos de movimiento posibles:

```text
Entrada
Salida
Ajuste
Traspaso
Venta
Devolución
```

---

# 8. Ordenamiento

Permitir ordenar columnas principales:

- Producto.
- Código.
- Código Barra.
- Mínimo.
- Reposición.
- Máximo.
- Stock.

Soportar:

- Ascendente.
- Descendente.
- Sin orden.

Mostrar indicador visual de ordenamiento en los encabezados.

---

# 9. Paginación

Mostrar información como:

```text
Registros del 1 al 5 de un total de 5 Registros
```

Controles:

```text
[ Anterior ] [ 1 ] [ Siguiente ]
```

La paginación debe actualizar página, total de registros, rango mostrado y cantidad por página.

Deshabilitar `Anterior` en la primera página y `Siguiente` en la última.

---

# 10. Estados de la tabla

### Loading

Mostrar indicador de carga durante la consulta.

### Sin resultados

Mostrar:

```text
No se encontraron productos para los filtros seleccionados.
```

### Error

Mostrar:

```text
No fue posible cargar el inventario.
Intente nuevamente.
```

Nunca mostrar stack traces, SQL, nombres de clases internas ni información sensible del backend.

---

# 11. Combinación de filtros

Todos los filtros deben funcionar simultáneamente.

Ejemplo:

```text
Bodega = Restaurante 2
Familia = Bebidas
Categoría = Café
Stock = Reposición
Tipo búsqueda = Código Barra
```

El resultado debe aplicar todos los filtros activos.

Evitar múltiples llamadas innecesarias al backend.

Ejemplo conceptual:

```text
GET /inventory
    ?warehouseId=2
    &familyId=3
    &categoryId=5
    &stockStatus=REPLENISHMENT
    &searchType=BARCODE
    &search=7801234567890
```

---

# 12. Modelo conceptual

Considerar como mínimo:

```text
Product
Warehouse
Inventory
InventoryMovement
Category
Family
SubFamily
```

Relación conceptual:

```text
Producto
   │
   ├── Categoría
   ├── Familia
   ├── Subfamilia
   │
   └── Inventario
          │
          ├── Bodega
          ├── Stock
          ├── Mínimo
          ├── Reposición
          └── Máximo
```

No incorporar entidades ni funcionalidades relacionadas con lotes.

---

# 13. Diseño visual

Tomar la imagen de referencia como guía.

Características:

- Estilo ERP administrativo.
- Alta densidad de información.
- Fondo gris muy claro.
- Paneles blancos/gris claro.
- Encabezados de sección gris oscuro.
- Bordes sutiles.
- Botones compactos.
- Tipografía pequeña y funcional.
- Iconografía mediante Font Awesome o equivalente.
- Badges para estados.
- Acciones claramente diferenciadas.
- Espaciado compacto.
- Controles correctamente alineados.

### Colores funcionales

```text
Rojo      → Stock Crítico
Amarillo  → Stock Reposición
Verde     → Stock Normal
Naranja   → Stock Exceso
Azul      → Acciones, búsqueda e histórico
Gris      → Información secundaria
```

No convertirlo en un dashboard moderno con tarjetas gigantes o exceso de espacios. Debe parecer un **módulo administrativo/ERP profesional y funcional**.

---

# 14. Responsive

Debe funcionar correctamente en:

- Desktop.
- Laptop.
- Tablet.

En resoluciones pequeñas:

- Reorganizar filtros en varias filas.
- Adaptar las tarjetas de stock.
- Permitir scroll horizontal en la tabla.
- Evitar superposición de botones.
- Mantener inputs y selects utilizables.

---

# 15. Componentización

Separar la pantalla en componentes reutilizables.

Estructura sugerida:

```text
InventoryModule
│
├── StockSummary
│   ├── StockCriticalCard
│   ├── StockReplenishmentCard
│   ├── StockNormalCard
│   └── StockExcessCard
│
├── StockQuickFilters
│
├── InventorySpecificFilters
│   ├── WarehouseSelect
│   ├── FamilySelect
│   ├── SubFamilySelect
│   ├── TypeSelect
│   ├── CategorySelect
│   └── InventoryExport
│
├── ProductSearchFilter
│
├── InventoryImport
│
├── InventoryTable
│   ├── InventoryRow
│   ├── StockBadge
│   └── HistoryButton
│
├── InventoryPagination
│
└── InventoryHistoryModal
```

---

# 16. Reglas de implementación

- No implementar menú lateral.
- No implementar barra superior.
- No implementar búsqueda por lote.
- No implementar columna de lotes.
- No implementar funcionalidades relacionadas con lotes.
- Todos los datos deben ser dinámicos.
- No hardcodear cantidades de stock.
- Los filtros deben afectar realmente los resultados.
- Los contadores superiores deben calcularse a partir de los datos.
- Las exportaciones deben utilizar los filtros activos.
- La búsqueda debe combinarse con los demás filtros.
- El histórico debe abrirse para el producto seleccionado.
- La importación Excel debe validar los datos antes de actualizar.
- Manejar loading, error y vacío.
- Evitar duplicación de lógica.
- Mantener componentes pequeños y reutilizables.
- Separar presentación, lógica y acceso a datos.
- No mostrar errores técnicos al usuario final.
- Mantener el diseño visual cercano a la referencia.
- No agregar funcionalidades fuera del alcance de este prompt.

---

# 17. Estructura visual esperada

```text
┌──────────────────────────────────────────────────────────────┐
│ FILTROS STOCK                                                │
│                                                              │
│ ┌──────────┐ ┌────────────┐ ┌──────────┐ ┌──────────┐       │
│ │ CRÍTICO  │ │ REPOSICIÓN │ │  NORMAL  │ │  EXCESO  │       │
│ │    0     │ │     4      │ │    0     │ │    1     │       │
│ └──────────┘ └────────────┘ └──────────┘ └──────────┘       │
│                                                              │
│ [Todos] [Crítico] [Reposición] [Normal] [Exceso] [Limpiar] │
├──────────────────────────────────────────────────────────────┤
│ FILTRO DE INVENTARIO ESPECÍFICO                              │
│                                                              │
│ Bodega       Familia       Sub Familia                       │
│ Tipo         Categoría     Ordenar Stock    Deshabilitados   │
│                                                              │
│ [Descargar CSV]                         [Descargar XLSX]      │
├──────────────────────────────────────────────────────────────┤
│ FILTRO POR TIPO DE BÚSQUEDA                                  │
│                                                              │
│ Tipo búsqueda       Buscador de Productos       [Buscar]     │
├──────────────────────────────────────────────────────────────┤
│ IMPORTADOR Minimo Reposicion Maximo                          │
│                                                              │
│ [Descargar Ejemplo]                    [Importar Excel]      │
├──────────────────────────────────────────────────────────────┤
│ INVENTARIO                                                    │
│                                                              │
│ [10 ▼] Registros                                             │
│                                                              │
│ # | Producto | Código | Barra | Tipo | Min | Rep | Max | ...│
│───┼──────────┼────────┼───────┼──────┼─────┼─────┼─────┼────│
│   │ Producto │        │       │      │     │     │     │    │
│   │ Producto │        │       │      │     │     │     │    │
│                                                              │
│ Registros del 1 al X de un total de X                        │
│                       [Anterior] [1] [Siguiente]              │
└──────────────────────────────────────────────────────────────┘
```

## Criterio final

El resultado debe sentirse como una **pantalla real de inventario de un ERP**, no como un mockup genérico.

Priorizar:

1. Fidelidad a la referencia.
2. Usabilidad.
3. Claridad de los filtros.
4. Densidad de información.
5. Componentización.
6. Reutilización.
7. Manejo correcto de estados.
8. Integración limpia con backend.
9. Diseño responsive.
10. Código mantenible.

**No agregar funcionalidades fuera del alcance definido en este prompt.**
