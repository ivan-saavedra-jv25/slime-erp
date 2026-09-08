package cl.slimerp.admin.usuarios;

public class UsuarioConflictException extends RuntimeException {
    public UsuarioConflictException(String message) {
        super(message);
    }
}