package cl.slimerp.admin.common;

import java.util.List;

/** Forma estándar de listas paginadas de la consola (shape compatible con Spring Data Page). */
public record Paginated<T>(List<T> content,
                           long totalElements,
                           int totalPages,
                           int number,
                           int size) {

    public static <T> Paginated<T> desde(org.springframework.data.domain.Page<T> page) {
        return new Paginated<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }
}