package cl.slimerp.admin.empresas;

public enum EstadoEmpresa {
    TRIAL, ACTIVE, SUSPENDED, EXPIRED, BLOCKED, CANCELLED;

    public boolean esActivo() {
        return this == TRIAL || this == ACTIVE;
    }
}