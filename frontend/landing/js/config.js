/* ==========================================================================
   SAAVIA ERP — Configuracion de contacto
   Unico archivo a editar para cambiar el numero de WhatsApp o los mensajes.
   ========================================================================== */

const CONTACT = {
  /* REEMPLAZAR: formato internacional, solo digitos, sin "+" ni espacios.
     Ejemplo Chile: 56912345678 */
  whatsapp: '56900000000',

  /* Mensaje precargado segun el boton. La clave se referencia en el HTML
     con el atributo data-wa="demo" | "info". */
  messages: {
    demo: 'Hola, quiero solicitar una demostración de SAAVIA ERP.',
    info: 'Hola, quiero más información sobre SAAVIA ERP.',
  },

  /* Cuantas preguntas frecuentes se muestran en cada visita, elegidas al
     azar del total que hay en index.html. Poner 0 las muestra todas. */
  faqVisibles: 6,
};
