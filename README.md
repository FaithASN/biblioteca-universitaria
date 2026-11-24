# Biblioteca universitaria

API REST para administrar el catálogo, registrar cuentas de lectores y controlar el préstamo y la devolución de ejemplares. Usa Java 21, Spring Boot, Spring Data JPA, Hibernate, Flyway y Spring Security. H2 ofrece una base relacional persistente para la instalación local. También se incluye la configuración para PostgreSQL.

## Instalación y ejecución

Necesitas JDK 21 y Maven 3.9+. Desde la raíz del repositorio, abre PowerShell y prepara la clave JWT y la primera cuenta de administración.

```powershell
$bytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:ADMIN_EMAIL = 'admin@universidad.test'
$env:ADMIN_PASSWORD = Read-Host 'Clave de administración (12 a 72 bytes)' -MaskInput
mvn clean package
java -jar target/biblioteca-universitaria-1.0.0.jar
```

En Linux o macOS puedes generar la clave con `export JWT_SECRET="$(openssl rand -base64 32)"`. Define también `ADMIN_EMAIL` y `ADMIN_PASSWORD` en el entorno antes de ejecutar los mismos comandos de Maven y Java.

La API escucha en `http://localhost:8080`. H2 guarda los datos en `data/`. Flyway aplica el esquema al arrancar. La cuenta ADMIN se crea en el primer arranque con las variables indicadas. Una vez creada, puedes iniciar sin esas dos variables. Volver a definirlas con el mismo correo conserva la contraseña registrada. Conserva la clave JWT fuera del repositorio y reutilízala entre reinicios si necesitas que los tokens vigentes sigan siendo válidos.

### Usar PostgreSQL

Con Docker Compose, define las variables siguientes antes de iniciar Java. Mantén también las variables de seguridad del paso anterior.

```powershell
$env:DB_PASSWORD = Read-Host 'Clave para PostgreSQL' -MaskInput
docker compose up -d --wait
$env:DB_URL = 'jdbc:postgresql://localhost:5432/biblioteca'
$env:DB_USER = 'biblioteca'
java -jar target/biblioteca-universitaria-1.0.0.jar
```

Para una instalación existente de PostgreSQL, crea la base y un usuario con permisos para aplicar la migración y ajusta las tres variables `DB_*`. El volumen de Compose conserva los datos cuando se detiene el contenedor.

## Rutas

Todas las rutas de la tabla llevan el prefijo `/api`. El login devuelve `accessToken`, `tokenType` y `expiresIn` (3600 segundos). Envía `Authorization: Bearer <accessToken>` en las rutas protegidas. En Swagger UI puedes hacerlo con el botón **Authorize**.

| Método y ruta | Acceso | Datos y resultado |
|---|---|---|
| `POST /auth/registro` | Público | `nombre`, `email`, `clave` (mínimo 12 caracteres, máximo 72 bytes) → cuenta USUARIO, 201 |
| `POST /auth/login` | Público | `email`, `clave` → token, 200 |
| `GET /usuarios/yo` | ADMIN o USUARIO | Perfil propio, 200 |
| `GET /usuarios` | ADMIN | Cuentas paginadas, 200 |
| `POST /libros` | ADMIN | `isbn` (13 dígitos), `titulo`, `autor`, `ejemplares` (entero positivo) → libro, 201 |
| `GET /libros` | ADMIN o USUARIO | Catálogo paginado, con `q` opcional para título o autor, 200 |
| `GET /libros/{id}` | ADMIN o USUARIO | Ficha y existencias, 200 |
| `POST /prestamos` | ADMIN | `usuarioId`, `libroId`, `dias` (1 a 30) → préstamo, 201 |
| `POST /prestamos/{id}/devolucion` | ADMIN | Registra la devolución y repone el ejemplar, 200 |
| `GET /prestamos` | ADMIN | Todos los préstamos paginados, 200 |
| `GET /prestamos/mis` | ADMIN o USUARIO | Historial de la cuenta autenticada, 200 |

Las listas admiten `pagina` (0 por defecto) y `tamano` (20 por defecto, máximo 100). Los errores de validación y de negocio devuelven `estado`, `mensaje` y `campos`. Los estados incluyen 400 para datos inválidos, 401 para credenciales o JWT inválidos, 403 para permisos insuficientes, 404 para registros inexistentes y 409 para duplicados, agotamiento o segunda devolución.

- Swagger UI en [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Especificación OpenAPI en [localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## Pruebas con Postman

1. Importa `postman/Biblioteca.postman_collection.json`.
2. Crea un entorno con `baseUrl` (normalmente `http://localhost:8080`), `adminEmail` y `adminPassword`. Introduce el correo y la clave usados al iniciar la aplicación.
3. Ejecuta la colección completa en orden con **Run collection**. La primera petición genera un correo e ISBN de prueba y las siguientes capturan los IDs y tokens. Usa una base de pruebas porque el recorrido crea registros.
4. Guarda el resumen de la ejecución desde el Runner para adjuntarlo al informe si necesitas capturas de la interfaz de Postman.

Para ejecutar la misma colección desde la terminal con Node.js 22 o superior:

```powershell
npx --yes newman@6.2.1 run postman/Biblioteca.postman_collection.json `
  --env-var "adminEmail=$env:ADMIN_EMAIL" --env-var "adminPassword=$env:ADMIN_PASSWORD" `
  -r cli,junit --reporter-junit-export postman/postman-results.xml
```

GitHub Actions ejecuta la colección contra PostgreSQL en cada envío o pull request. El archivo JUnit queda disponible en la ejecución de Actions. No publiques credenciales, tokens emitidos ni archivos de base de datos.

## Modelo de datos

`usuario` y `rol` tienen una relación muchos a muchos mediante `usuario_rol`. Cada registro de `prestamo` pertenece a un usuario y a un libro. La devolución se registra en el préstamo mediante `devuelto_en`. El esquema SQL con claves, restricciones e índices está en `src/main/resources/db/migration/`.
