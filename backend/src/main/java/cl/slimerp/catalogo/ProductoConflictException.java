package cl.slimerp.catalogo;

// Conflictos al crear/editar productos (código de barra ya usado por otro producto del tenant) → HTTP 409
public class ProductoConflictException extends RuntimeException {
    public ProductoConflictException(String message) {
        super(message);
    }
}
