#!/usr/bin/env node
/**
 * Regenera el bloque de preguntas frecuentes de index.html a partir de
 * data/faq.json.
 *
 * Ese bloque es solo un RESPALDO: en un servidor la pagina lee el JSON
 * directamente. El respaldo importa cuando index.html se abre con doble clic
 * (file://), donde el navegador bloquea la lectura del JSON, y para que el
 * texto de las preguntas exista en el HTML que ven los buscadores.
 *
 * Uso:  node tools/sync-faq.js
 */

const fs = require('fs');
const path = require('path');

const raiz = path.join(__dirname, '..');
const rutaJson = path.join(raiz, 'data', 'faq.json');
const rutaHtml = path.join(raiz, 'index.html');

const INICIO = '<!-- faq:inicio -->';
const FIN = '<!-- faq:fin -->';

/* El texto del JSON entra al HTML: hay que escaparlo. */
function escapar(texto) {
  return String(texto)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function main() {
  let datos;
  try {
    datos = JSON.parse(fs.readFileSync(rutaJson, 'utf8'));
  } catch (e) {
    console.error('No se pudo leer data/faq.json:', e.message);
    process.exit(1);
  }

  if (!Array.isArray(datos) || datos.length === 0) {
    console.error('data/faq.json debe ser un arreglo con al menos una pregunta.');
    process.exit(1);
  }

  datos.forEach(function (item, i) {
    if (!item || !item.q || !item.a) {
      console.error('La pregunta ' + (i + 1) + ' no tiene "q" y "a".');
      process.exit(1);
    }
  });

  const bloques = datos.map(function (item) {
    const categoria = item.categoria
      ? '\n                  <span class="faq-item__cat">' + escapar(item.categoria) + '</span>'
      : '';
    return [
      '            <details class="faq-item">',
      '              <summary class="faq-item__q">',
      '                <span class="faq-item__head">' + categoria,
      '                  <span class="faq-item__title">' + escapar(item.q) + '</span>',
      '                </span>',
      '                <span class="faq-item__mark" aria-hidden="true"></span>',
      '              </summary>',
      '              <div class="faq-item__a"><p>' + escapar(item.a) + '</p></div>',
      '            </details>',
    ].join('\n');
  });

  const html = fs.readFileSync(rutaHtml, 'utf8');
  const desde = html.indexOf(INICIO);
  const hasta = html.indexOf(FIN);

  if (desde === -1 || hasta === -1 || hasta < desde) {
    console.error('No encontre los marcadores ' + INICIO + ' y ' + FIN + ' en index.html.');
    process.exit(1);
  }

  const nuevo =
    html.slice(0, desde + INICIO.length) +
    '\n' +
    bloques.join('\n') +
    '\n          ' +
    html.slice(hasta);

  if (nuevo === html) {
    console.log('El respaldo de index.html ya estaba al dia (' + datos.length + ' preguntas).');
    return;
  }

  fs.writeFileSync(rutaHtml, nuevo, 'utf8');
  console.log('index.html actualizado con ' + datos.length + ' preguntas desde data/faq.json.');
}

main();
