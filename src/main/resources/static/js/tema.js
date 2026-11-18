/*
 * Modo claro / oscuro de StockTag.
 * - El tema inicial lo aplica el script pequeño del <head> (fragments/layout.html) para evitar parpadeo.
 * - Este archivo maneja el botón sol/luna, guarda la elección en localStorage (clave "stocktag-tema")
 *   y, mientras el usuario no haya elegido, sigue la preferencia del sistema (prefers-color-scheme).
 */
(function () {
    'use strict';

    var CLAVE = 'stocktag-tema';
    var raiz = document.documentElement;
    var preferenciaOscura = window.matchMedia('(prefers-color-scheme: dark)');

    function leerGuardado() {
        try {
            var valor = localStorage.getItem(CLAVE);
            return valor === 'light' || valor === 'dark' ? valor : null;
        } catch (e) {
            return null;
        }
    }

    function guardar(tema) {
        try {
            localStorage.setItem(CLAVE, tema);
        } catch (e) {
            // Sin localStorage (modo privado, etc.): el tema solo dura esta página.
        }
    }

    function temaActual() {
        return raiz.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light';
    }

    function actualizarBotones() {
        var oscuro = temaActual() === 'dark';
        var texto = oscuro ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro';
        document.querySelectorAll('[data-tema-toggle]').forEach(function (boton) {
            boton.setAttribute('aria-label', texto);
            boton.setAttribute('title', texto);
            boton.setAttribute('aria-pressed', String(oscuro));
        });
    }

    function aplicar(tema) {
        raiz.setAttribute('data-bs-theme', tema);
        actualizarBotones();
    }

    // Si el usuario no ha elegido, seguir los cambios del sistema.
    preferenciaOscura.addEventListener('change', function (evento) {
        if (!leerGuardado()) {
            aplicar(evento.matches ? 'dark' : 'light');
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
        actualizarBotones();
        document.querySelectorAll('[data-tema-toggle]').forEach(function (boton) {
            boton.addEventListener('click', function () {
                var nuevo = temaActual() === 'dark' ? 'light' : 'dark';
                guardar(nuevo);
                aplicar(nuevo);
            });
        });
    });
})();
