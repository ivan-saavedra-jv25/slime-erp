# NC-003 — Permisos

## Objetivo

Que el módulo quede protegido con los mismos permisos que el resto del ERP, visible en el
menú solo para quien puede verlo y editable solo para quien puede editarlo.

## Backend

### `backend/src/main/java/cl/slimerp/permisos/Permiso.java`

Agregar, inmediatamente después de `NOTAS_VENTA_EDITAR` para mantener el orden de la
cadena comercial:

```java
NOTAS_CREDITO_VER,
NOTAS_CREDITO_EDITAR,
```

### `backend/src/main/java/cl/slimerp/permisos/RolPermisos.java`

Asignar con el mismo criterio que `NOTAS_VENTA_*`:

| Rol | `NOTAS_CREDITO_VER` | `NOTAS_CREDITO_EDITAR` |
|---|---|---|
| SUPER_ADMIN | sí | sí |
| ADMIN | sí | sí |
| VENDEDOR | sí | sí |
| COMPRADOR | no | no |
| VISUALIZADOR | sí | no |

Verificar contra el archivo real al implementar: el criterio es "los mismos roles que ya
tienen `NOTAS_VENTA_VER` / `NOTAS_VENTA_EDITAR`".

### Uso

`@PreAuthorize("hasAuthority('NOTAS_CREDITO_VER')")` en los métodos de lectura y
`@PreAuthorize("hasAuthority('NOTAS_CREDITO_EDITAR')")` en los de escritura, **uno por
método** del controller. Los overrides por usuario ya funcionan solos vía
`usuario_permiso` + `PermisoEfectivoService`; no hay que tocar nada ahí.

### Tests que enumeran permisos esperados

Estos tests fallan al agregar constantes nuevas y deben actualizarse en la misma tarea:

- `backend/src/test/java/cl/slimerp/permisos/RolPermisosTest.java`
- `backend/src/test/java/cl/slimerp/auth/AuthControllerTest.java`
- `backend/src/test/java/cl/slimerp/config/JwtAuthFilterTest.java`

## Frontend

### `frontend/src/app/core/models/models.ts`

Agregar al union type `Permiso` (líneas ~22-50), junto a los de notas de venta:

```ts
| 'NOTAS_CREDITO_VER'
| 'NOTAS_CREDITO_EDITAR'
```

### `frontend/src/app/features/usuarios/roles-permisos.component.ts`

Agregar las etiquetas legibles de ambos permisos para que aparezcan en la pantalla de
administración de roles.

## Validación

```bash
docker run --rm -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-21 \
  mvn -q -o test -Dtest='RolPermisosTest,AuthControllerTest,JwtAuthFilterTest'
```

Manual: con un usuario VISUALIZADOR, el módulo aparece en el menú pero el botón "Nueva"
no; con un usuario COMPRADOR, el ítem no aparece en el menú y la API responde 403.
