package cl.slimerp.inventario;

import java.math.BigDecimal;

// Fila de la tabla de consulta de Inventario: un producto con su stock ya
// resuelto (cantidad en la bodega filtrada, o sumado entre todas las
// bodegas activas si no se filtró por una en particular).
public record InventarioConsultaItem(Long productoId, String nombre, String sku, String codigoBarra, BigDecimal stock) {
}
