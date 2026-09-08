package cl.slimerp.admin.empresas;

import java.time.LocalDateTime;

public record EmpresaDetalleResponse(
        Long id,
        String nombre,
        String rut,
        String businessName,
        String plan,
        String status,
        boolean activo,
        LocalDateTime fechaAlta,
        LocalDateTime lastAccessAt,
        long usuariosTotales,
        long usuariosActivos,
        java.math.BigDecimal saldoPendiente,
        String suscripcionEstado,
        long alertasAbiertas) {
}