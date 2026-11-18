/*
 * Casilla "Mostrar contraseñas" del formulario de nueva empresa.
 * Mejora progresiva: sin JavaScript la casilla queda oculta y el formulario funciona igual.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var contenedor = document.querySelector('[data-mostrar-claves]');
        if (!contenedor) {
            return;
        }
        var casilla = contenedor.querySelector('input[type="checkbox"]');
        var campos = document.querySelectorAll('#password, #confirmacion');
        contenedor.hidden = false;
        casilla.addEventListener('change', function () {
            campos.forEach(function (campo) {
                campo.type = casilla.checked ? 'text' : 'password';
            });
        });
    });
})();
