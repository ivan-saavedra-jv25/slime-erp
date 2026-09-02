package cl.slimerp.common;

import org.springframework.data.domain.Page;

import java.util.List;

// Respuesta de listados paginados desde el backend: solo el contenido de la
// página actual y el total de registros que coinciden con la búsqueda (para
// que el frontend arme la paginación sin cargar todo el listado).
public record PaginaResponse<T>(List<T> contenido, long total) {
    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getTotalElements());
    }
}
