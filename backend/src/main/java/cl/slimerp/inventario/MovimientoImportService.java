package cl.slimerp.inventario;

import cl.slimerp.catalogo.Producto;
import cl.slimerp.catalogo.ProductoRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee un Excel de carga masiva con columnas Código (SKU o código de barra) y
 * Cantidad, y resuelve cada fila contra el catálogo de productos. No crea
 * movimientos: el tipo, la bodega y la observación los define el formulario
 * en pantalla — el resultado solo se agrega al detalle de esa operación.
 */
@Service
public class MovimientoImportService {

    private static final int COL_CODIGO = 0;
    private static final int COL_CANTIDAD = 1;
    private static final int NUM_COLUMNAS = 2;

    private final ProductoRepository productoRepository;
    private final DataFormatter dataFormatter = new DataFormatter();

    public MovimientoImportService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public record ImportResultado(int totalFilas, List<ItemResuelto> items, List<FilaError> errores) {}

    public record ItemResuelto(Long productoId, String productoSku, String productoNombre, BigDecimal cantidad) {}

    public record FilaError(int numeroFila, String mensaje) {}

    private record FilaResuelta(int numeroFila, Producto producto, BigDecimal cantidad) {}

    public ImportResultado importar(Long tenantId, InputStream xlsx) {
        List<String[]> filasCrudas = leerFilas(xlsx);
        List<FilaError> errores = new ArrayList<>();
        // LinkedHashMap: conserva el orden de primera aparición y suma filas que repiten el mismo producto.
        Map<Long, FilaResuelta> porProducto = new LinkedHashMap<>();

        for (int i = 0; i < filasCrudas.size(); i++) {
            int numeroFila = i + 2; // fila 1 es encabezado
            try {
                FilaResuelta fila = resolverFila(tenantId, numeroFila, filasCrudas.get(i));
                porProducto.merge(fila.producto().getId(), fila,
                        (existente, nueva) -> new FilaResuelta(
                                existente.numeroFila(), existente.producto(), existente.cantidad().add(nueva.cantidad())));
            } catch (IllegalArgumentException e) {
                errores.add(new FilaError(numeroFila, e.getMessage()));
            }
        }

        List<ItemResuelto> items = porProducto.values().stream()
                .map(f -> new ItemResuelto(f.producto().getId(), f.producto().getSku(), f.producto().getNombre(), f.cantidad()))
                .toList();

        errores.sort((a, b) -> Integer.compare(a.numeroFila(), b.numeroFila()));
        return new ImportResultado(filasCrudas.size(), items, errores);
    }

    private FilaResuelta resolverFila(Long tenantId, int numeroFila, String[] valores) {
        String codigo = valor(valores, COL_CODIGO);
        String cantidadTexto = valor(valores, COL_CANTIDAD);

        if (codigo == null) throw new IllegalArgumentException("Falta el código del producto");
        Producto producto = productoRepository.findFirstByTenantIdAndSku(tenantId, codigo)
                .or(() -> productoRepository.findFirstByTenantIdAndCodigoBarra(tenantId, codigo))
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado para el código \"" + codigo + "\""));

        BigDecimal cantidad = parseCantidad(cantidadTexto);

        return new FilaResuelta(numeroFila, producto, cantidad);
    }

    private BigDecimal parseCantidad(String texto) {
        if (texto == null) throw new IllegalArgumentException("Falta la cantidad");
        try {
            BigDecimal cantidad = new BigDecimal(texto.trim().replace(",", "."));
            if (cantidad.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
            return cantidad;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cantidad inválida: \"" + texto + "\"");
        }
    }

    private String valor(String[] valores, int indice) {
        if (indice >= valores.length) return null;
        String v = valores[indice];
        if (v == null) return null;
        String limpio = v.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private List<String[]> leerFilas(InputStream xlsx) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(xlsx)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String[]> filas = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || esFilaVacia(row)) continue;
                String[] valores = new String[NUM_COLUMNAS];
                for (int c = 0; c < NUM_COLUMNAS; c++) {
                    valores[c] = leerCelda(row.getCell(c));
                }
                filas.add(valores);
            }
            return filas;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo Excel", e);
        }
    }

    // Las celdas numéricas (p.ej. un código de barras tipeado como número) se formatean con
    // DataFormatter en notación científica para magnitudes grandes; se leen como BigDecimal
    // para conservar el valor completo en texto plano.
    private String leerCelda(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return dataFormatter.formatCellValue(cell);
    }

    private boolean esFilaVacia(Row row) {
        for (int c = 0; c < NUM_COLUMNAS; c++) {
            var cell = row.getCell(c);
            if (cell != null && !dataFormatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }
}
