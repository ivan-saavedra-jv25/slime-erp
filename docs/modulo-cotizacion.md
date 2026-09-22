# Módulo de Cotizaciones — ERP

Construye un módulo completo de **Cotizaciones** para un ERP orientado a PyMEs.

El módulo debe ser sencillo, funcional y preparado para posteriormente relacionar las cotizaciones con notas de venta, órdenes de compra, guías de despacho y documentos tributarios.

## 1. Objetivo

Permitir crear, editar, consultar, enviar, duplicar y gestionar el estado de las cotizaciones realizadas a clientes.

Cada cotización debe tener un identificador único y mantener trazabilidad sobre sus cambios y documentos relacionados.

---

## 2. Datos de la cotización

### Información general

* Número de cotización
* Fecha de emisión
* Fecha de vencimiento
* Cliente
* Vendedor/responsable
* Estado
* Moneda
* Forma de pago
* Condiciones comerciales
* Observaciones

### Cliente

Permitir seleccionar un cliente existente y mostrar:

* Razón social / nombre
* RUT
* Dirección
* Correo
* Teléfono

No crear nuevamente los datos del cliente dentro de la cotización; utilizar la referencia al cliente existente.

### Detalle

Permitir agregar múltiples líneas:

* Producto o servicio
* Código
* Descripción
* Cantidad
* Precio unitario
* Descuento
* Subtotal
* IVA
* Total

Calcular automáticamente:

```text
Subtotal
Descuentos
Neto
IVA
Total
```

Los cálculos deben realizarse automáticamente al modificar cantidad, precio o descuento.

---

# 3. Estados

Implementar inicialmente los siguientes estados:

```text
BORRADOR
ENVIADA
ACEPTADA
RECHAZADA
VENCIDA
CANCELADA
```

### Reglas básicas

**BORRADOR**

* Puede editarse.
* Puede eliminarse.
* Puede enviarse.

**ENVIADA**

* No debería eliminarse directamente.
* Puede marcarse como aceptada o rechazada.
* Puede duplicarse.

**ACEPTADA**

* No debería modificarse libremente.
* Debe permitir crear una Nota de Venta.
* Mantener la cotización original como referencia.

**RECHAZADA**

* No puede convertirse en una venta.
* Puede duplicarse para generar una nueva cotización.

**VENCIDA**

* Se determina cuando supera la fecha de vencimiento.
* Puede duplicarse para generar una nueva cotización.

**CANCELADA**

* Estado final.
* Mantener el registro para auditoría.

Mostrar siempre el estado mediante un badge visual.

---

# 4. Acciones

En el listado y detalle implementar:

* Nueva cotización
* Ver
* Editar
* Guardar
* Enviar
* Aceptar
* Rechazar
* Duplicar
* Descargar PDF
* Imprimir
* Cancelar

---

# 5. Listado de cotizaciones

Crear una pantalla principal con una tabla/listado.

Columnas:

* Número
* Fecha
* Cliente
* Vendedor
* Total
* Vencimiento
* Estado
* Acciones

Agregar filtros por:

* Estado
* Cliente
* Fecha desde
* Fecha hasta
* Vendedor

Agregar búsqueda por:

* Número de cotización
* Cliente
* RUT

Agregar ordenamiento por fecha, número y total.

---

# 6. Vista detalle

Al abrir una cotización mostrar:

### Encabezado

```text
COT-000123
Empresa ABC SpA
15/09/2026
Vence: 30/09/2026
ESTADO: ENVIADA
```

### Información del cliente

Mostrar todos los datos principales del cliente.

### Productos

Mostrar el detalle completo de productos/servicios.

### Totales

```text
Subtotal       $XXX
Descuento      $XXX
Neto           $XXX
IVA            $XXX
TOTAL          $XXX
```

### Acciones

Mostrar las acciones disponibles según el estado.

---

# 7. Trazabilidad

Agregar una sección llamada:

## Documentos relacionados

Inicialmente debe mostrar:

```text
Cotización
    ↓
Nota de Venta
```

Si todavía no existe una Nota de Venta:

```text
No existen documentos relacionados.
```

Diseñar esta sección para que posteriormente pueda soportar:

```text
Cotización
   ↓
Nota de Venta
   ↓
Guía de Despacho
   ↓
Factura
   ↓
Pago
```

No implementar todavía los módulos futuros, solamente dejar preparada la estructura.

---

# 8. Historial

Agregar una sección de historial de la cotización.

Registrar eventos como:

```text
15/09/2026 - Cotización creada
15/09/2026 - Cotización enviada
16/09/2026 - Cotización aceptada
16/09/2026 - Nota de Venta creada
```

Cada evento debe registrar:

* Fecha
* Usuario
* Acción
* Estado anterior
* Estado nuevo cuando corresponda

---

# 9. Dashboard de cotizaciones

Crear un pequeño dashboard encima del listado.

Mostrar tarjetas con:

```text
Cotizaciones este mes
Cotizaciones pendientes
Cotizaciones aceptadas
Cotizaciones rechazadas
Monto cotizado
Monto aceptado
```

Agregar un gráfico sencillo:

### Cotizaciones por estado

Mostrar visualmente:

* Borradores
* Enviadas
* Aceptadas
* Rechazadas
* Vencidas

Agregar otro indicador:

### Tasa de conversión

```text
Cotizaciones aceptadas
---------------------- × 100
Cotizaciones enviadas
```

El dashboard debe permitir seleccionar un período:

```text
Este mes
Mes anterior
Últimos 3 meses
Este año
Personalizado
```

---

# 10. Libro / historial de cotizaciones

Crear una sección llamada **Libro de Cotizaciones**.

Debe funcionar como un registro histórico de todas las cotizaciones.

Mostrar:

* Número
* Fecha
* Cliente
* Estado
* Neto
* IVA
* Total
* Usuario
* Documentos relacionados

Permitir:

* Buscar
* Filtrar
* Ordenar
* Exportar posteriormente

El libro debe conservar también cotizaciones rechazadas, vencidas y canceladas.

No eliminar registros históricos.

---

# 11. Diseño

Utilizar el diseño visual existente del ERP.

El módulo debe sentirse empresarial, limpio y sencillo.

Priorizar:

* Tabla clara
* Estados mediante badges
* Acciones contextuales
* Formularios organizados por secciones
* Dashboard compacto
* Buena visualización de totales
* Responsive

No sobrecargar la interfaz.

---

# 12. Consideraciones técnicas

Mantener separación entre:

```text
Cotización
Cliente
Producto
Usuario
Documentos relacionados
Historial
```

No duplicar información innecesariamente.

Preparar la estructura para futuras relaciones:

```text
quotation
    ↓
sales_order
    ↓
delivery_note
    ↓
invoice
    ↓
payment
```

La cotización debe conservar su identificador único y nunca reutilizar un número.

Implementar validaciones para evitar:

* Cotizaciones sin cliente
* Cotizaciones sin productos
* Cantidades inválidas
* Precios inválidos
* Fechas inconsistentes
* Estados incompatibles con determinadas acciones.

El objetivo es tener un **módulo de cotizaciones funcional para el MVP**, pero con una arquitectura preparada para convertirse posteriormente en el origen de la trazabilidad comercial completa del ERP.
