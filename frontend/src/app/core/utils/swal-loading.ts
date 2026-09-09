import Swal from 'sweetalert2';

// Overlay de carga bloqueante reutilizable (basado en sweetalert2, ya usado
// en Ventas) para operaciones que tardan y necesitan feedback visual claro.
//
// Muchas de estas operaciones responden en pocos milisegundos, tan rápido que
// el overlay alcanza a pintarse pero no a percibirse. Por eso cerrarCargando()
// garantiza un tiempo mínimo en pantalla antes de cerrar.
const DURACION_MINIMA_MS = 600;
let inicioCarga = 0;

export function mostrarCargando(mensaje = 'Guardando'): void {
  inicioCarga = Date.now();
  Swal.fire({
    html: `
      <div class="saavia-loading">
        <span class="saavia-loading__brace" aria-hidden="true">{</span>
        <p class="saavia-loading__text">
          ${mensaje}
          <span class="saavia-loading__dots"><span></span><span></span><span></span></span>
        </p>
        <span class="saavia-loading__brace" aria-hidden="true">}</span>
      </div>
    `,
    allowOutsideClick: false,
    allowEscapeKey: false,
    showConfirmButton: false,
    customClass: { popup: 'saavia-loading-popup' },
  });
}

export function cerrarCargando(): void {
  const transcurrido = Date.now() - inicioCarga;
  const espera = DURACION_MINIMA_MS - transcurrido;
  if (espera > 0) {
    setTimeout(() => Swal.close(), espera);
  } else {
    Swal.close();
  }
}
