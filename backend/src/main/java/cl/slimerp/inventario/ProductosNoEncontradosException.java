package cl.slimerp.inventario;

import java.util.List;
import java.util.stream.Collectors;

// Se lanza al registrar un movimiento cuando hay productos del detalle que no
// existen (o están deshabilitados) en el catálogo del tenant. Lleva las
// posiciones 1-based dentro de la lista para que el frontend las resalte.
public class ProductosNoEncontradosException extends IllegalArgumentException {

    private final List<Integer> posicionesInvalidas;

    public ProductosNoEncontradosException(List<Integer> posicionesInvalidas) {
        super(mensaje(posicionesInvalidas));
        this.posicionesInvalidas = List.copyOf(posicionesInvalidas);
    }

    public List<Integer> getPosicionesInvalidas() {
        return posicionesInvalidas;
    }

    private static String mensaje(List<Integer> posiciones) {
        String listado = posiciones.stream().map(String::valueOf).collect(Collectors.joining(", "));
        if (posiciones.size() == 1) {
            return "Un producto de la lista no fue encontrado en el catálogo (posición " + listado
                    + "). Está resaltado en rojo; revísalo e intenta nuevamente.";
        }
        return posiciones.size() + " productos de la lista no fueron encontrados en el catálogo (posiciones "
                + listado + "). Están resaltados en rojo; revísalos e intenta nuevamente.";
    }
}