# Módulo de Notas de Venta — ERP

Construir un módulo completo de **Notas de Venta** para el ERP, orientado a PyMEs.

La Nota de Venta representa una operación comercial confirmada y debe permitir gestionar el proceso entre la aceptación de una venta y su posterior despacho y/o facturación.

El módulo debe estar preparado para relacionarse posteriormente con:

```text
Cotización
    ↓
Nota de Venta
    ↓
Guía de Despacho
    ↓
Documento Tributario
    ↓
Pago
```

No implementar todavía la emisión de DTE ni la creación de Guías de Despacho desde este módulo.

---

# 1. Objetivo

Permitir:

* Crear notas de venta.
* Crear una nota de venta a partir de una cotización aceptada.
* Crear una nota de venta manualmente.
* Editar notas de venta mientras su estado lo permita.
* Confirmar ventas.
* Gestionar su estado.
* Consultar el detalle de cada venta.
* Mantener trazabilidad con la cotización de origen.
* Consultar documentos relacionados.
* Mantener un historial de cambios.
* Mostrar indicadores básicos de ventas.

---

# 2. Datos de la Nota de Venta

## Información general

Cada nota de venta debe contener:

* Número de nota de venta.
* Fecha de emisión.
* Fecha estimada de entrega.
* Cliente.
* Vendedor/responsable.
* Estado.
* Moneda.
* Forma de pago.
* Condiciones de venta.
* Dirección de entrega.
* Observaciones.

El número debe ser único y no reutilizarse.

---

# 3. Cliente

Permitir seleccionar un cliente existente.

Mostrar:

* Razón social / nombre.
* RUT.
* Dirección.
* Correo.
* Teléfono.

La nota de venta debe guardar la referencia al cliente y no duplicar innecesariamente su información.

---

# 4. Detalle de productos

Permitir agregar múltiples productos o servicios.

Cada línea debe contener:

* Producto/servicio.
* Código.
* Descripción.
* Cantidad.
* Precio unitario.
* Descuento.
* Subtotal.
* IVA.
* Total.

Calcular automáticamente:

```text
Subtotal
Descuentos
Neto
IVA
Total
```

Validar que:

* La cantidad sea mayor que 0.
* El precio no sea negativo.
* Exista al menos un producto o servicio.
* Los cálculos sean consistentes.

---

# 5. Estados

Implementar inicialmente:

```text
BORRADOR
CONFIRMADA
EN PREPARACIÓN
PARCIALMENTE ENTREGADA
ENTREGADA
FACTURADA
CANCELADA
```

## BORRADOR

Permite:

* Editar.
* Guardar.
* Confirmar.
* Cancelar.
* Duplicar.

## CONFIRMADA

Representa una venta confirmada.

Permite:

* Ver.
* Preparar.
* Duplicar.
* Cancelar según las reglas del sistema.

## EN PREPARACIÓN

La venta está siendo preparada para entrega.

Permite:

* Ver.
* Registrar avance.
* Generar posteriormente documentos relacionados.

## PARCIALMENTE ENTREGADA

Parte de los productos ya fueron entregados.

Debe mostrar:

```text
Cantidad solicitada
Cantidad entregada
Cantidad pendiente
```

## ENTREGADA

Todos los productos fueron entregados.

## FACTURADA

La operación ya fue asociada a un documento tributario.

## CANCELADA

La operación queda cerrada y debe conservarse para historial y auditoría.

---

# 6. Crear desde una cotización

Agregar la opción:

**Crear Nota de Venta desde Cotización**

Esta opción debe estar disponible desde el módulo de cotizaciones cuando la cotización esté en estado:

```text
ACEPTADA
```

Al crear la nota de venta, copiar:

* Cliente.
* Vendedor.
* Productos.
* Cantidades.
* Precios.
* Descuentos.
* Condiciones.
* Observaciones.

Mantener una relación:

```text
Cotización COT-000123
        ↓
Nota de Venta NV-000045
```

La nota de venta debe mostrar:

**Origen: Cotización COT-000123**

Y permitir navegar hacia la cotización original.

No modificar la cotización original al crear la nota de venta.

---

# 7. Crear manualmente

También debe existir:

**Nueva Nota de Venta**

Esta opción permite crear una venta sin cotización previa.

En este caso:

```text
Origen:
Venta directa
```

La nota de venta debe funcionar normalmente aunque no exista una cotización relacionada.

---

# 8. Listado de Notas de Venta

Crear una pantalla principal con tabla.

Columnas:

* Número.
* Fecha.
* Cliente.
* Vendedor.
* Total.
* Estado.
* Fecha de entrega.
* Origen.
* Acciones.

Agregar búsqueda por:

* Número.
* Cliente.
* RUT.

Filtros:

* Estado.
* Cliente.
* Vendedor.
* Fecha desde.
* Fecha hasta.
* Origen.

Origen:

```text
Cotización
Venta directa
```

---

# 9. Acciones

Implementar:

* Nueva Nota de Venta.
* Ver.
* Editar.
* Guardar.
* Confirmar.
* Duplicar.
* Cancelar.
* Descargar PDF.
* Imprimir.

Las acciones deben depender del estado.

No mostrar acciones que ya no sean válidas para el estado actual.

---

# 10. Vista detalle

La pantalla de detalle debe mostrar:

## Encabezado

```text
NOTA DE VENTA
NV-000045

Cliente:
Empresa ABC SpA

Fecha:
22/09/2026

Estado:
CONFIRMADA
```

## Origen

Si fue creada desde una cotización:

```text
Origen:
Cotización COT-000123
```

Si fue creada manualmente:

```text
Origen:
Venta directa
```

## Productos

Mostrar todos los productos y cantidades.

## Totales

```text
Subtotal
Descuentos
Neto
IVA
Total
```

## Información de entrega

Mostrar:

* Fecha estimada.
* Dirección.
* Estado de entrega.
* Cantidad entregada.
* Cantidad pendiente.

---

# 11. Trazabilidad

Agregar una sección:

## Documentos relacionados

Inicialmente soportar:

```text
Cotización
    ↓
Nota de Venta
```

Posteriormente deberá poder ampliarse a:

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

No implementar todavía estos módulos futuros.

La relación debe permitir consultar:

* Documento origen.
* Documentos derivados.
* Estado de cada documento.

---

# 12. Historial

Registrar los principales eventos:

```text
22/09/2026
Nota de venta creada

22/09/2026
Nota de venta confirmada

23/09/2026
Venta enviada a preparación

24/09/2026
Entrega parcial registrada
```

Cada evento debe registrar:

* Fecha.
* Usuario.
* Acción.
* Estado anterior.
* Estado nuevo.

---

# 13. Control de cantidades

Preparar la estructura para controlar entregas parciales.

Ejemplo:

```text
Producto       Solicitado    Entregado    Pendiente

Producto A        10            10            0
Producto B         5             3            2
```

El estado debe poder determinarse según las cantidades:

```text
0 entregado
      ↓
CONFIRMADA

Parte entregada
      ↓
PARCIALMENTE ENTREGADA

Todo entregado
      ↓
ENTREGADA
```

No implementar todavía el módulo completo de despacho; solamente dejar preparada esta estructura.

---

# 14. Dashboard

Agregar un dashboard pequeño sobre el listado.

Mostrar:

```text
Notas de venta este mes
Ventas confirmadas
Ventas en preparación
Ventas pendientes de entrega
Ventas entregadas
Monto total vendido
```

Agregar gráfico:

**Ventas por estado**

Y un segundo indicador:

**Ventas por período**

Permitir seleccionar:

```text
Este mes
Mes anterior
Últimos 3 meses
Este año
Personalizado
```

---

# 15. Libro de Notas de Venta

Crear una sección:

## Libro de Notas de Venta

Debe funcionar como registro histórico de todas las notas de venta.

Mostrar:

* Número.
* Fecha.
* Cliente.
* Estado.
* Neto.
* IVA.
* Total.
* Vendedor.
* Origen.
* Documentos relacionados.

Las notas canceladas deben permanecer en el libro.

No eliminar registros históricos.

---

# 16. Arquitectura de trazabilidad

Preparar las relaciones para:

```text
quotation
      ↓
sales_note
      ↓
delivery_note
      ↓
tax_document
      ↓
payment
```

No asumir que todos los documentos existirán.

Una nota de venta puede existir:

```text
sin cotización
```

o:

```text
con cotización
```

y posteriormente puede tener:

```text
0 o múltiples documentos relacionados
```

Evitar implementar la trazabilidad únicamente mediante campos como:

```text
invoice_id
delivery_note_id
```

Preferir una estructura de relaciones que permita futuras extensiones.

---

# 17. Reglas importantes

* No eliminar notas de venta confirmadas.
* Las notas canceladas deben conservarse.
* No reutilizar números.
* Registrar cambios importantes en historial.
* Validar cliente y productos antes de confirmar.
* Mantener los valores originales de la venta.
* No modificar una nota confirmada de forma que altere su historial comercial.
* Mantener siempre la referencia a la cotización de origen cuando exista.
* Separar claramente la Nota de Venta de los documentos tributarios.
* La Nota de Venta no reemplaza una factura, boleta u otro DTE.

---

# 18. Resultado esperado

El módulo debe permitir este flujo:

```text
Cotización aceptada
        ↓
Crear Nota de Venta
        ↓
NV-000045
        ↓
Confirmar
        ↓
En preparación
        ↓
Entrega parcial / total
        ↓
Entregada
        ↓
Posteriormente:
Guía de Despacho / DTE
```

También debe permitir:

```text
Venta directa
      ↓
Nota de Venta
      ↓
Confirmada
```

El objetivo es construir un módulo de Nota de Venta sólido para el MVP, con una estructura preparada para convertirse posteriormente en el centro de la trazabilidad de las operaciones comerciales del ERP.
