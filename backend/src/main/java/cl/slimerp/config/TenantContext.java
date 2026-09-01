package cl.slimerp.config;

/**
 * Contiene el tenant_id del request actual (extraído del JWT por {@link JwtAuthFilter}).
 * Se usa en toda la capa de servicio para filtrar siempre por tenant.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No hay tenant_id en el contexto actual. ¿Falta autenticación?");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
