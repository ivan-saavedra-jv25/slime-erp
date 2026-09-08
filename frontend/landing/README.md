# Landing Page — SAAVIA NEXO

Landing comercial de **NEXO**, la plataforma de gestión empresarial de **SAAVIA**.

HTML + CSS + JavaScript puro. Sin build, sin dependencias, sin `node_modules`.
Se abre `index.html` en el navegador y funciona, o se sube por FTP tal cual.

---

## Estructura

```
page/
├── index.html            todo el marcado y el copy
├── css/
│   ├── tokens.css        colores, tipografía, espaciado, sombras (modo claro y oscuro)
│   ├── base.css          reset, tipografía base, utilidades, contenedor
│   ├── components.css    navbar, botones, cards, badges, footer
│   └── sections.css      estilos de cada sección de la página
├── js/
│   ├── config.js         número de WhatsApp y mensajes  ← ARCHIVO A EDITAR
│   ├── theme.js          tema claro/oscuro (se carga en el <head>, sin destello)
│   └── main.js           navbar, menú móvil, enlaces de WhatsApp, animaciones
├── data/
│   └── faq.json          las preguntas frecuentes  ← ARCHIVO A EDITAR
├── tools/
│   └── sync-faq.js       copia el JSON al respaldo de index.html
├── assets/img/           destino de las imágenes de marca
└── favicon.svg           provisional
```

---

## 1. Configurar WhatsApp

Editar **`js/config.js`**. Es el único lugar donde vive el número:

```js
const CONTACT = {
  whatsapp: '56912345678',   // formato internacional, solo dígitos, sin + ni espacios
  messages: {
    demo: 'Hola, quiero solicitar una demostración de NEXO.',
    info: 'Hola, quiero más información sobre NEXO.',
  },
  faqVisibles: 6,            // preguntas frecuentes mostradas por visita
};
```

Los botones del HTML llevan `data-wa="demo"` o `data-wa="info"`; el script arma
el enlace `https://wa.me/<número>?text=<mensaje>` al cargar la página.

> El `href` escrito en el HTML es solo un respaldo. Al cambiar el número en
> `config.js` no hace falta tocar `index.html`.

---

## 2. Reemplazar las imágenes de marca

Ninguna imagen viene generada. Estos son los archivos pendientes y dónde entran:

| Archivo | Dónde se usa | Qué debe ser |
| --- | --- | --- |
| `assets/img/logo-saavia-horizontal.svg` | Navbar (fondo claro) | Isotipo "S" abstracta + wordmark SAAVIA. SVG, fondo transparente, se renderiza a 32 px de alto |
| `assets/img/logo-saavia-horizontal-dark.svg` | Footer y navbar en modo oscuro | La misma pieza en versión para fondo navy |
| `assets/img/isotipo-saavia.svg` | Lockups, avatares | Solo el símbolo, formato cuadrado |
| `favicon.svg` | Pestaña del navegador | El isotipo, legible a 16 px |
| `assets/img/nexo-lockup.svg` | Sección SAAVIA (opcional) | "NEXO" con "by SAAVIA" debajo, tipográfico |
| `assets/img/og-image.jpg` | Vista previa al compartir | 1200×630 px, fondo `#0A1F44` con el lockup NEXO centrado |

**Cómo reemplazar el logo del navbar:** en `index.html`, buscar el comentario
`IMAGEN PENDIENTE: assets/img/logo-saavia-horizontal.svg` y cambiar el bloque

```html
<span class="brand__mark" aria-hidden="true">S</span>
<span class="wordmark wordmark--saavia">SAAVIA</span>
```

por

```html
<img class="brand__logo" src="assets/img/logo-saavia-horizontal.svg" alt="SAAVIA">
```

Mientras no existan los archivos, el navbar y el footer muestran el wordmark
tipográfico (SAAVIA en mayúsculas con espaciado amplio), así que la página se ve
completa y presentable igual. El único recuadro punteado visible está en la
sección "Detrás de NEXO está SAAVIA", con la descripción del logo que falta:
se elimina ese `<div class="img-placeholder">` al poner la imagen real.

---

## 3. Preguntas frecuentes

Las preguntas viven en **`data/faq.json`**. Cada una tiene tres campos:

```json
[
  {
    "categoria": "Operación",
    "q": "¿Cómo me ayuda con la cobranza?",
    "a": "Las cuentas por cobrar nacen de la venta y los pagos se asocian…"
  }
]
```

- `q` y `a` son obligatorios. Una entrada sin ellos se ignora.
- `categoria` es opcional: se muestra como etiqueta sobre la pregunta. Hoy se
  usan Decisión, Operación, Tecnología, Crecimiento y Empezar, pero puedes
  escribir la que quieras.

Hay 14 preguntas y en cada visita se muestran **6 al azar**. Para cambiar
cuántas, editar `faqVisibles` en `js/config.js`; con `0` se muestran todas y el
botón "Ver otras preguntas" desaparece. Ese botón vuelve a sortear sin recargar.

### Cómo se cargan

```
data/faq.json          ← la fuente
     │
     ├── servido por HTTP (hosting, servidor local)
     │      la página lee el JSON y arma la sección con él
     │
     └── abierto con doble clic (file://)
            el navegador bloquea la lectura del JSON,
            y se usa el respaldo escrito en index.html
```

`index.html` contiene una copia estática de las preguntas entre los
comentarios `<!-- faq:inicio -->` y `<!-- faq:fin -->`. Existe por dos razones:
que la sección funcione al abrir el archivo con doble clic, y que los
buscadores vean el texto sin ejecutar JavaScript. **No se edita a mano.**

### Después de editar el JSON

```bash
node tools/sync-faq.js
```

Regenera ese respaldo desde `data/faq.json`. Si no lo corres, en el sitio
publicado igual se ven los cambios (ahí manda el JSON); lo que queda
desactualizado es solo la vista con doble clic y lo que leen los buscadores.

Añadir una pregunta = añadir un objeto al arreglo y correr el comando. No hay
que tocar el HTML ni el JavaScript.

---

## 4. Editar textos

Todo el copy está en `index.html`, en texto plano y en el orden en que aparece
en la página. No hay plantillas ni JSON de contenido.

---

## 5. Colores

Se cambian en un solo lugar: las variables de `css/tokens.css`.

| Variable | Valor | Uso |
| --- | --- | --- |
| `--navy-900` | `#0A1F44` | Fondos oscuros, hero, footer |
| `--blue-600` | `#1B6FE0` | Botones, enlaces, acento primario |
| `--green-500` | `#10B981` | Acento de crecimiento, badges, iconos |
| `--slate-50` | `#F8FAFC` | Fondo de secciones claras |
| `--slate-500` | `#64748B` | Texto secundario |

El gradiente `--gradient-brand` (azul → verde) se usa solo en el hero, el CTA
final y acentos tipográficos puntuales.

El modo oscuro se define en el mismo archivo, en los bloques
`:root[data-theme="dark"]` y `@media (prefers-color-scheme: dark)`.

---

## 6. Pendientes

- [ ] Número real de WhatsApp Business en `js/config.js`
- [ ] Archivos de marca en `assets/img/`
- [ ] Páginas de Privacidad y Términos (los enlaces del footer apuntan a `#`)
- [ ] Dominio real en las etiquetas `og:url` y en el JSON-LD de `index.html`
