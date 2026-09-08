/* ==========================================================================
   SAAVIA NEXO — Tema claro / oscuro
   Este archivo se carga de forma bloqueante en el <head>: aplica el tema
   antes del primer render para evitar el destello de tema incorrecto.
   ========================================================================== */

(function () {
  var KEY = 'saavia-theme';

  function stored() {
    try {
      return localStorage.getItem(KEY);
    } catch (e) {
      return null;
    }
  }

  function systemPrefersDark() {
    return (
      window.matchMedia &&
      window.matchMedia('(prefers-color-scheme: dark)').matches
    );
  }

  /* Sin preferencia guardada no se escribe data-theme: el CSS sigue a
     prefers-color-scheme por su cuenta. */
  var saved = stored();
  if (saved === 'dark' || saved === 'light') {
    document.documentElement.setAttribute('data-theme', saved);
  }

  function current() {
    var attr = document.documentElement.getAttribute('data-theme');
    if (attr) return attr;
    return systemPrefersDark() ? 'dark' : 'light';
  }

  function apply(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(KEY, theme);
    } catch (e) {
      /* Modo privado o almacenamiento bloqueado: el tema vale por la sesion. */
    }
    var btn = document.querySelector('[data-theme-toggle]');
    if (btn) {
      btn.setAttribute(
        'aria-label',
        theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'
      );
    }
  }

  window.SaaviaTheme = {
    toggle: function () {
      apply(current() === 'dark' ? 'light' : 'dark');
    },
    init: function () {
      apply(current());
      var btn = document.querySelector('[data-theme-toggle]');
      if (btn) btn.addEventListener('click', window.SaaviaTheme.toggle);
    },
  };
})();
