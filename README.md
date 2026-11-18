# StockTag

StockTag es una aplicación web de inventario para pymes (tiendas de barrio, ferreterías, papelerías).
Cada empresa lleva el stock de sus productos escaneando el código de barras con un lector USB o Bluetooth
(o escribiéndolo a mano), registra entradas y salidas, recibe alertas de stock bajo y descarga su
inventario en CSV. Un administrador de plataforma da de alta las empresas y puede activarlas o desactivarlas.

Hecha con Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Thymeleaf y Bootstrap 5.3.
La base de datos es H2 en modo archivo (carpeta `data/`), así que los datos se conservan al reiniciar.
Bootstrap y Bootstrap Icons van dentro de la aplicación: funciona sin conexión a internet.

## Requisitos

- **Java 21** (JDK para compilar; basta el JRE para ejecutar el `.jar`). Comprobar con `java -version`.
- No hace falta instalar Maven: el proyecto trae el wrapper `./mvnw` (en Windows, `mvnw.cmd`).
  La primera compilación descarga las dependencias, así que necesita internet una vez.

## Cómo ejecutar

### Con el wrapper de Maven (desarrollo)

```bash
./mvnw spring-boot:run
```

Abrir http://localhost:8080

### Con el `.jar` (entrega)

Generar el ejecutable:

```bash
./mvnw clean package
```

Queda en `target/stocktag.jar`. Para ejecutarlo:

```bash
java -jar target/stocktag.jar
```

El `.jar` se puede copiar a cualquier carpeta y ejecutar desde allí. La primera vez crea la carpeta `data/`
junto a donde se ejecuta, con la base de datos, el usuario administrador y la empresa de ejemplo.
Para empezar de cero basta con borrar la carpeta `data/` (con la aplicación detenida).

Opciones útiles al ejecutar:

| Qué | Cómo |
|-----|------|
| Arrancar sin la empresa de ejemplo | `java -jar stocktag.jar --stocktag.datos-ejemplo=false` |
| Cambiar las credenciales del administrador inicial | variables de entorno `STOCKTAG_ADMIN_USERNAME` y `STOCKTAG_ADMIN_PASSWORD` (solo se usan si todavía no existe un administrador) |
| Usar otro puerto | `java -jar stocktag.jar --server.port=9090` |

## Usuarios de prueba

| Usuario | Contraseña | Rol | Entra a |
|---------|------------|-----|---------|
| `admin` | `admin123` | Administrador de plataforma | `/admin` (gestión de empresas) |
| `tienda` | `tienda123` | Empresa "Tienda La Esquina" | Inventario |

La empresa de ejemplo se crea por defecto con 8 productos y dos semanas de entradas y salidas, para que el
dashboard, las alertas, el historial y el CSV tengan datos desde el primer arranque. Tres productos quedan
con stock bajo y uno agotado.

## Funcionalidades por rol

### Administrador de plataforma (`admin`)

- Lista de empresas con NIT, usuario, fecha de alta, número de productos y estado.
- Registro de empresas: crea la empresa y su usuario de acceso en un solo paso (no hay registro público).
- Activar y desactivar empresas. Una empresa desactivada no puede iniciar sesión y sus sesiones abiertas se cierran.
- No ve los inventarios de las empresas.

### Empresa (por ejemplo, `tienda`)

- **Escanear:** campo con foco automático para el lector de códigos de barras (escribe el código y presiona Enter).
  Muestra el producto y su stock y permite registrar una entrada o una salida. Si el código no existe,
  ofrece crear el producto.
- **Productos:** lista completa con cantidad, stock mínimo y estado; filtro de solo los que tienen stock bajo.
  Formulario para crear productos con stock inicial.
- **Alertas:** productos con `cantidad <= stock mínimo`, primero los agotados, con las unidades que faltan.
  El número de alertas se ve siempre en la barra de navegación.
- **Movimientos:** historial de entradas y salidas con fecha y hora, filtros por tipo y rango de fechas, y paginación.
- **Reporte CSV:** descarga del inventario con encabezados en español, UTF-8 y separador `;` (se abre bien en Excel).
- Una salida nunca puede dejar el stock en negativo: se muestra el error en la pantalla.
- Cada empresa solo ve y modifica sus propios productos y movimientos.

### Para todos

- Tema claro u oscuro con el botón sol/luna de la barra (también en el login). Se recuerda en el navegador
  y, si no se ha elegido, sigue la preferencia del sistema.

## Pruebas

```bash
./mvnw test
```

Las pruebas usan una base H2 en memoria, así que no tocan la carpeta `data/`. Cubren los services
(`InventarioService`, `EmpresaService`, `UsuarioDetailsService`), la seguridad por roles, las vistas,
el reporte CSV y los datos de ejemplo.

## Estructura

```
src/main/java/com/stocktag/
  model/        entidades JPA (Empresa, Usuario, Producto, Movimiento)
  repository/   interfaces Spring Data
  service/      reglas de negocio (InventarioService, EmpresaService)
  controller/   controllers MVC que devuelven vistas Thymeleaf
  security/     Spring Security y carga de usuarios
  config/       datos iniciales (administrador y empresa de ejemplo)
src/main/resources/
  templates/    vistas Thymeleaf (admin/, inventario/, fragments/)
  static/       CSS y JavaScript propios
```
