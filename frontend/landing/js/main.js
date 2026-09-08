/* ==========================================================================
   SAAVIA NEXO — Interacciones
   WhatsApp, navbar sticky, menu movil y reveal on scroll.
   Sin dependencias externas.
   ========================================================================== */

(function () {
  'use strict';

  /* --- Enlaces de WhatsApp ---------------------------------------------- */
  /* Cada [data-wa] es un <a> real. Si el script falla, el href de respaldo
     del HTML sigue llevando a wa.me. */
  function initWhatsapp() {
    if (typeof CONTACT === 'undefined' || !CONTACT.whatsapp) return;

    var links = document.querySelectorAll('[data-wa]');
    Array.prototype.forEach.call(links, function (link) {
      var key = link.getAttribute('data-wa');
      var text = (CONTACT.messages && CONTACT.messages[key]) || '';
      var href = 'https://wa.me/' + CONTACT.whatsapp;
      if (text) href += '?text=' + encodeURIComponent(text);

      link.setAttribute('href', href);
      link.setAttribute('target', '_blank');
      link.setAttribute('rel', 'noopener noreferrer');
    });
  }

  /* --- Navbar sticky ----------------------------------------------------- */
  function initNav() {
    var nav = document.querySelector('[data-nav]');
    if (!nav) return;

    var ticking = false;

    function update() {
      nav.classList.toggle('is-scrolled', window.scrollY > 40);
      ticking = false;
    }

    window.addEventListener(
      'scroll',
      function () {
        if (!ticking) {
          window.requestAnimationFrame(update);
          ticking = true;
        }
      },
      { passive: true }
    );

    update();
  }

  /* --- Menu movil -------------------------------------------------------- */
  function initMenu() {
    var burger = document.querySelector('[data-burger]');
    var panel = document.querySelector('[data-panel]');
    if (!burger || !panel) return;

    function close() {
      panel.classList.remove('is-open');
      burger.setAttribute('aria-expanded', 'false');
      document.body.classList.remove('is-locked');
    }

    function open() {
      panel.classList.add('is-open');
      burger.setAttribute('aria-expanded', 'true');
      document.body.classList.add('is-locked');
    }

    burger.addEventListener('click', function () {
      if (panel.classList.contains('is-open')) close();
      else open();
    });

    panel.addEventListener('click', function (event) {
      if (event.target.closest('a')) close();
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && panel.classList.contains('is-open')) {
        close();
        burger.focus();
      }
    });

    /* Al pasar a desktop el panel deja de tener sentido. */
    window.addEventListener('resize', function () {
      if (window.innerWidth > 900 && panel.classList.contains('is-open')) {
        close();
      }
    });
  }

  /* --- Reveal on scroll -------------------------------------------------- */
  function initReveal() {
    var items = document.querySelectorAll('.reveal');
    if (!items.length) return;

    var reduced =
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reduced || !('IntersectionObserver' in window)) {
      Array.prototype.forEach.call(items, function (el) {
        el.classList.add('is-visible');
      });
      return;
    }

    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: '0px 0px -10% 0px', threshold: 0.08 }
    );

    Array.prototype.forEach.call(items, function (el, i) {
      /* Escalonado leve dentro de un mismo grupo. */
      el.style.transitionDelay = (i % 4) * 70 + 'ms';
      observer.observe(el);
    });
  }

  /* --- Preguntas frecuentes ---------------------------------------------- */
  /* Origen de los datos: data/faq.json.
     index.html trae ademas un respaldo estatico (generado con
     `node tools/sync-faq.js`) para dos situaciones donde el JSON no se puede
     leer: la pagina abierta con doble clic (file://, el navegador bloquea la
     lectura) y los buscadores, que ven el HTML sin ejecutar nada.
     Pase lo que pase con el JSON, la seccion nunca queda vacia. */

  var FAQ_URL = 'data/faq.json';

  function initFaq() {
    var lista = document.querySelector('[data-faq]');
    if (!lista) return;

    function continuar() {
      sortearFaq(lista);
    }

    /* Navegador sin fetch: se queda con el respaldo del HTML. */
    if (!window.fetch || !window.Promise) {
      continuar();
      return;
    }

    fetch(FAQ_URL, { cache: 'no-cache' })
      .then(function (resp) {
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        return resp.json();
      })
      .then(function (datos) {
        if (Array.isArray(datos) && datos.length) pintarFaq(lista, datos);
      })
      .catch(function () {
        /* Sin JSON: sigue el respaldo que ya esta en el HTML. */
      })
      .then(continuar);
  }

  /* Reemplaza el respaldo por lo que venga del JSON.
     Todo el texto entra con textContent: nunca se interpreta como HTML. */
  function pintarFaq(lista, datos) {
    var fragmento = document.createDocumentFragment();
    var validas = 0;

    datos.forEach(function (item) {
      if (!item || !item.q || !item.a) return;
      validas++;

      var detalle = document.createElement('details');
      detalle.className = 'faq-item';

      var resumen = document.createElement('summary');
      resumen.className = 'faq-item__q';

      var cabeza = document.createElement('span');
      cabeza.className = 'faq-item__head';

      if (item.categoria) {
        var cat = document.createElement('span');
        cat.className = 'faq-item__cat';
        cat.textContent = item.categoria;
        cabeza.appendChild(cat);
      }

      var titulo = document.createElement('span');
      titulo.className = 'faq-item__title';
      titulo.textContent = item.q;
      cabeza.appendChild(titulo);

      var marca = document.createElement('span');
      marca.className = 'faq-item__mark';
      marca.setAttribute('aria-hidden', 'true');

      resumen.appendChild(cabeza);
      resumen.appendChild(marca);

      var cuerpo = document.createElement('div');
      cuerpo.className = 'faq-item__a';
      var parrafo = document.createElement('p');
      parrafo.textContent = item.a;
      cuerpo.appendChild(parrafo);

      detalle.appendChild(resumen);
      detalle.appendChild(cuerpo);
      fragmento.appendChild(detalle);
    });

    /* Si el JSON no traia ninguna pregunta usable, no se toca el respaldo. */
    if (!validas) return;

    lista.innerHTML = '';
    lista.appendChild(fragmento);
  }

  /* Muestra al azar CONTACT.faqVisibles preguntas de las que haya. */
  function sortearFaq(lista) {
    var items = Array.prototype.slice.call(lista.querySelectorAll('.faq-item'));
    var visibles =
      typeof CONTACT !== 'undefined' && typeof CONTACT.faqVisibles === 'number'
        ? CONTACT.faqVisibles
        : 6;

    var boton = document.querySelector('[data-faq-shuffle]');

    /* Nada que recortar: se muestran todas y el boton no aparece. */
    if (visibles <= 0 || visibles >= items.length) return;

    function sortear() {
      /* Fisher-Yates sobre una copia: elige cuales se muestran, pero las
         visibles conservan el orden del archivo. */
      var baraja = items.slice();
      for (var i = baraja.length - 1; i > 0; i--) {
        var j = Math.floor(Math.random() * (i + 1));
        var tmp = baraja[i];
        baraja[i] = baraja[j];
        baraja[j] = tmp;
      }

      var elegidas = baraja.slice(0, visibles);
      items.forEach(function (el) {
        el.hidden = elegidas.indexOf(el) === -1;
        el.open = false;
      });
    }

    sortear();

    if (boton) {
      boton.hidden = false;
      boton.addEventListener('click', sortear);
    }
  }

  /* --- Ano del footer ---------------------------------------------------- */
  function initYear() {
    var el = document.querySelector('[data-year]');
    if (el) el.textContent = new Date().getFullYear();
  }

  function init() {
    initWhatsapp();
    initNav();
    initMenu();
    initFaq();
    initReveal();
    initYear();
    if (window.SaaviaTheme) window.SaaviaTheme.init();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
