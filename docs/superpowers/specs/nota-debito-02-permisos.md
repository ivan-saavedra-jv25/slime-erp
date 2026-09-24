# ND-003 — Permisos

## Objetivo

Que el módulo quede protegido con los mismos permisos que el resto del ERP, visible en el
menú solo para quien puede verlo y editable solo para quien puede editarlo.

## Backend

### `backend/src/main/java/cl/slimerp/permisos/Permiso.java`

Agregar, inmediatamente después de `NOTAS_CREDITO_EDITAR`:

```java
NOTAS_DEBITO_VER,
NOTAS_DEBITO_EDITAR,
```

### `backend/src/main/java/cl/slimerp/permisos/RolPermisos.java`

Asignar con el mismo criterio que `NOTAS_CREDITO_*`:

| Rol | `NOTAS_DEBITO_VER` | `NOTAS_DEBITO_EDITAR` |
|---|---|---|
| SUPER_ADMIN | sí | sí |
| ADMIN | sí | sí |
| VENDEDOR | sí | sí |
| COMPRADOR | no | no |
| VISUALIZADOR | sí | no |

Verificar contra el archivo real al implementar: el criterio es "los mismos roles que ya
tienen `NOTAS_CREDITO_VER` / `NOTAS_CREDITO_EDITAR`".

### Uso

`@PreAuthorize("hasAuthority('NOTAS_DEBITO_VER')")` en los métodos de lectura y
`@PreAuthorize("hasAuthority('NOTAS_DEBITO_EDITAR')")` en los de escritura, **uno por
método** del controller. Los overrides por usuario ya funcionan solos vía
`usuario_permiso` + `PermisoEfectivoService`; no hay que tocar nada ahí.

### Tests que enumeran permisos esperados

Estos tests fallan al agregar constantes nuevas y deben actualizarse en la misma tarea:

- `backend/src/test/java/cl/slimerp/permisos/RolPermisosTest.java` — agregar el par a cada
  rol esperado (ADMIN, VENDEDOR, VISUALIZADOR; no COMPRADOR).
- `backend/src/test/java/cl/slimerp/permisos/PermisoEfectivoServiceTest.java` — verificar
  que no falla; si enumera el set de permisos totales, actualizar.
- `backend/src/test/java/cl/slimerp/auth/AuthControllerTest.java` — set de permisos en el
  `LoginResponse` (agregar `NOTAS_DEBITO_VER`, `NOTAS_DEBITO_EDITAR`).
- `backend/src/test/java/cl/slimerp/config/JwtAuthFilterTest.java` — revisar el assert de
  tamaño de autoridades (`assertEquals(21, autoridades.size())` si hoy es 19).

## Frontend

### `frontend/src/app/core/models/models.ts`

Agregar al union type `Permiso` junto a los de notas de crédito:

```ts
| 'NOTAS_DEBITO_VER'
| 'NOTAS_DEBITO_EDITAR'
```

### `frontend/src/app/features/usuarios/roles-permisos.component.ts`

Agregar las etiquetas legibles de ambos permisos para que aparezcan en la pantalla de
administración de roles.

## Validación

- `mvn -q -o test -Dtest='RolPermisosTest,PermisoEfectivoServiceTest,AuthControllerTest,JwtAuthFilterTest'`
  pasa.
- Manual: con un usuario VISUALIZADOR el módulo aparece en el menú pero el botón "Nueva"
  no; con un COMPRADOR el ítem no aparece y la API responde 403.