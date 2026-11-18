# StockTag — Contexto para Claude Code

## Qué es
StockTag es una aplicación web de inventario para pymes (tiendas de barrio, ferreterías, papelerías).
Proyecto final de semestre. Equipo de 3 personas, plazo de 1 semana. El profesor exige Java.
El plan completo del proyecto está en `docs/plan-stocktag.md` (si existe); este archivo es el resumen técnico.

## Stack (no cambiar sin acordarlo con el equipo)
- Java 21, Spring Boot 3, Maven (usar el wrapper `./mvnw`)
- Spring Web, Spring Data JPA, Spring Validation, Spring Security
- Vistas: Thymeleaf + Bootstrap 5.3 (por CDN). Sin React ni frontend separado.
- Base de datos: H2 en modo archivo (`jdbc:h2:file:./data/stocktag`), para que los datos no se pierdan al reiniciar.
- Pruebas: JUnit 5 + Spring Boot Test.
- Entregable final: un `.jar` ejecutable (`java -jar stocktag.jar`).

## Comandos
- Ejecutar: `./mvnw spring-boot:run` → http://localhost:8080
- Pruebas: `./mvnw test`
- Empaquetar: `./mvnw clean package` → `target/stocktag-*.jar`

## Estructura de paquetes
Paquete base: `com.stocktag`
- `model` — entidades JPA
- `repository` — interfaces Spring Data
- `service` — lógica de negocio (aquí van las reglas, nunca en los controllers)
- `controller` — controllers MVC que devuelven vistas Thymeleaf
- `security` — configuración de Spring Security y carga de usuarios
- `config` — datos iniciales y otras configuraciones
- Plantillas en `src/main/resources/templates/`, separadas en `admin/` y `inventario/`

## Modelo de dominio
- `Empresa`: id, nombre, nit, fechaAlta, activa (boolean)
- `Usuario`: id, username (único), password (BCrypt), rol (enum `Rol`: ADMIN_PLATAFORMA, EMPRESA), empresa (null para ADMIN_PLATAFORMA)
- `Producto`: id, codigoBarras, nombre, cantidad, stockMinimo, empresa. `codigoBarras` es único **por empresa** (restricción única compuesta empresa + codigoBarras).
- `Movimiento`: id, producto, tipo (enum `TipoMovimiento`: ENTRADA, SALIDA), cantidad, fecha
- `InventarioService` con los métodos del plan: `registrarMovimiento()`, `consultarStock()`, `verificarAlertasStock()`, `generarReporteCSV()`
- `EmpresaService`: `registrarEmpresa()` (crea la empresa y su usuario), `cambiarEstadoEmpresa()`, `listarEmpresas()`

## Roles y seguridad
- Solo dos roles. `ADMIN_PLATAFORMA` puede entrar a `/admin/**` (gestión de empresas) y NO ve inventarios. `EMPRESA` entra a todo lo demás.
- Login por formulario (Spring Security). No hay registro público: las empresas las crea el administrador.
- Una empresa desactivada no puede iniciar sesión.
- Al arrancar por primera vez, se crea un usuario ADMIN_PLATAFORMA inicial (credenciales en `application.properties`).
- **REGLA DE ORO:** la empresa siempre se obtiene del usuario autenticado dentro del service. Nunca se recibe por parámetro de URL ni de formulario. Toda consulta de productos o movimientos se filtra por esa empresa.

## Reglas de negocio
- Una SALIDA no puede dejar el stock en negativo; si pasa, se muestra un error en la vista.
- Todo movimiento se guarda con fecha y hora.
- Alerta de stock bajo cuando `cantidad <= stockMinimo`.
- Escaneo: los lectores USB/Bluetooth funcionan como un teclado (escriben el código y presionan Enter). El dashboard tiene un input con `autofocus` dentro de un formulario. Si el código no existe en la empresa, se ofrece crear el producto.
- El CSV se exporta con encabezados en español y codificación UTF-8.

## Modo claro / oscuro
- El usuario puede elegir entre tema claro y oscuro con un botón (ícono sol/luna) en la barra de navegación, visible en todas las pantallas, incluidos el login y el panel `/admin`.
- Se usa el soporte nativo de Bootstrap 5.3: atributo `data-bs-theme="light|dark"` en la etiqueta `<html>`.
- La elección se guarda en `localStorage` (clave `stocktag-tema`). Si no hay elección guardada, se usa la preferencia del sistema (`prefers-color-scheme`).
- Un script pequeño dentro del `<head>` aplica el tema antes de que la página se dibuje, para que no haya parpadeo.
- Todo vive en un fragmento Thymeleaf compartido (`templates/fragments/layout.html`) y en `static/js/tema.js`. Ninguna vista debe definir su propio tema.
- No usar colores fijos (`bg-white`, `text-dark`, colores hex en CSS). Usar las clases y variables de Bootstrap (`bg-body`, `text-body`, `var(--bs-...)`) para que ambos temas se vean bien.

## Diseño
- Para decisiones visuales (layout, color, tipografía, espaciado, componentes, accesibilidad) usar el skill `ui-ux-pro-max` (`.claude/skills/ui-ux-pro-max/`). Su buscador se ejecuta con `python3 .claude/skills/ui-ux-pro-max/scripts/search.py "<consulta>" --domain <dominio>`.
- Estilo de referencia: **Data-Dense Dashboard** (tablas compactas, mucha información por pantalla, jerarquía clara).
- Se aplica con **Bootstrap 5.3 + Thymeleaf**. No usar Tailwind ni React aunque el skill los sugiera: traducir sus recomendaciones a clases y variables de Bootstrap (respetando la sección "Modo claro / oscuro").
- Sin emojis (ni como íconos ni en textos) y sin degradados.
- Ignorar las recomendaciones de landing page (hero, testimonios, CTA de marketing, etc.): StockTag es una herramienta interna, no un sitio de marketing.

## Convenciones
- Código (clases, métodos, variables) en español, siguiendo los nombres del plan. Textos de la interfaz en español.
- Inyección por constructor, sin `@Autowired` en campos.
- Validaciones con anotaciones (`@NotBlank`, `@Min`) y errores mostrados en el formulario.
- Cambios pequeños y enfocados. No reestructurar archivos de otros módulos sin que se pida.
- Cada service nuevo debe tener al menos una prueba unitaria.

## Trabajo en equipo
- Ramas `feature/<nombre>`, Pull Requests pequeños, merge a `main` a diario.
- Responsables por módulo (cada uno edita principalmente sus archivos):
  - Modelo, repositorios, `InventarioService`, pruebas
  - Vistas de inventario: dashboard, escaneo, lista, alertas, formulario de producto
  - Seguridad, panel `/admin`, historial de movimientos, CSV, datos de ejemplo, empaquetado
