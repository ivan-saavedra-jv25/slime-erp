## Diseño y estilizado de formularios

Al crear, modificar o refactorizar formularios, prioriza siempre una interfaz **moderna, limpia, ordenada y consistente**.

### Principios generales

* Los formularios deben tener una **jerarquía visual clara**.
* Los campos deben estar organizados en un **orden lógico y natural para el usuario**.
* No colocar todos los campos simplemente uno debajo de otro sin analizar su relación.
* Agrupar campos relacionados en **secciones lógicas**.
* Utilizar títulos, subtítulos o separadores para diferenciar las distintas secciones.
* Mantener un espaciado uniforme entre campos, grupos y secciones.
* Evitar formularios visualmente saturados.
* Priorizar la legibilidad y facilidad de uso antes que agregar elementos decorativos.

### Distribución de campos

* Utilizar **1 o 2 columnas** dependiendo del contexto.
* Los campos relacionados pueden compartir una misma fila.
* Los campos largos, como observaciones, descripciones o contenido extenso, deben ocupar mayor ancho.
* No utilizar múltiples columnas cuando esto dificulte seguir el flujo del formulario.
* El orden debe permitir completar el formulario naturalmente **de arriba hacia abajo**.
* Los campos obligatorios deben estar claramente identificados.

### Estructura recomendada

Cuando la cantidad de información lo permita, estructurar los formularios de la siguiente manera:

1. **Título del formulario**

   * Indicar claramente qué acción está realizando el usuario.
   * Agregar una breve descripción cuando sea necesario.

2. **Información principal**

   * Datos esenciales del registro.
   * Identificación y campos principales.

3. **Información complementaria**

   * Datos secundarios relacionados.

4. **Configuración u opciones**

   * Parámetros, estados, configuraciones o preferencias.

5. **Acciones**

   * Acción principal claramente diferenciada.
   * Acciones secundarias separadas visualmente.

### Componentes

Mantener consistencia visual entre:

* Inputs
* Selects
* Textareas
* Checkboxes
* Radio buttons
* Date pickers
* Uploaders
* Botones
* Mensajes de validación

Todos deben respetar el mismo sistema visual de la aplicación.

### Estados visuales

Los componentes deben contemplar correctamente:

* Estado normal
* Hover
* Focus
* Disabled
* Error
* Validación correcta
* Loading

Los errores deben mostrarse **cerca del campo correspondiente**, evitando mensajes genéricos difíciles de asociar.

### Botones

* La acción principal debe ser visualmente predominante.
* Las acciones secundarias deben tener menor jerarquía visual.
* Evitar múltiples botones principales compitiendo entre sí.
* Mantener una posición consistente de las acciones en todos los formularios.

### Responsive

Todos los formularios deben funcionar correctamente en:

* Desktop
* Tablet
* Mobile

La distribución de columnas debe adaptarse automáticamente a pantallas pequeñas.

### Reglas importantes

* **No modificar la funcionalidad existente únicamente por mejorar el diseño.**
* **No eliminar campos existentes sin una solicitud explícita.**
* Antes de modificar un formulario, analizar la relación entre los campos y reorganizarlos cuando sea necesario.
* Mantener consistencia con los demás formularios de la aplicación.
* Reutilizar componentes existentes antes de crear componentes nuevos.
* No agregar elementos visuales innecesarios.
* Evitar exceso de colores, sombras, bordes o animaciones.
* El resultado debe parecer una **aplicación profesional y moderna**, no un formulario básico.

### Regla de prioridad

Ante cualquier decisión de diseño, priorizar en este orden:

1. Usabilidad
2. Orden y jerarquía de información
3. Consistencia visual
4. Responsive
5. Estética

# Reglas de Base de Datos

## Identificadores

* Todas las tablas deben utilizar un campo `id` como identificador primario.
* El campo `id` debe utilizar **auto-incremento**.
* El `id` debe ser generado automáticamente por la base de datos y **no debe ser enviado ni definido manualmente desde el frontend**.
* Al crear nuevos registros, la aplicación debe permitir que la base de datos genere automáticamente el siguiente `id`.
* Las relaciones entre tablas deben utilizar el `id` correspondiente como clave foránea cuando corresponda.
* No crear identificadores manuales o secuenciales desde la lógica de negocio de la aplicación.
* Al modificar o refactorizar tablas existentes, mantener el comportamiento de auto-incremento del `id`, salvo que exista una solicitud explícita para cambiarlo.
* Las migraciones deben definir correctamente el `id` como clave primaria y auto-incremental según el motor de base de datos utilizado.

## Regla de prioridad

Ante cualquier decisión relacionada con identificadores de base de datos:

1. El `id` debe ser la clave primaria.
2. El `id` debe ser auto-incremental.
3. La generación del `id` debe quedar a cargo de la base de datos.
4. El frontend nunca debe generar ni enviar manualmente el `id` al crear registros.
5. La lógica de negocio no debe implementar mecanismos propios para generar IDs.