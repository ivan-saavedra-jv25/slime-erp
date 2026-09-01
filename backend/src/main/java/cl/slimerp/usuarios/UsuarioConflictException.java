package cl.slimerp.usuarios;

// Conflictos al administrar usuarios del tenant (email ya usado) → HTTP 409
public class UsuarioConflictException extends RuntimeException {
    public UsuarioConflictException(String message) {
        super(message);
    }
}
