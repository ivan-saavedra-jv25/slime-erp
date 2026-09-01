package cl.slimerp.admin;

// Conflictos al administrar empresas (RUT o email de administrador ya usados) → HTTP 409
public class EmpresaConflictException extends RuntimeException {
    public EmpresaConflictException(String message) {
        super(message);
    }
}
