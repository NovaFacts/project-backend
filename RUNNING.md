# Guía de ejecución — NovaFacts Backend

Guía práctica y verificada para clonar y ejecutar el backend (y opcionalmente el frontend) desde cero. Todos los comandos, puertos y rutas de este documento fueron confirmados directamente contra el código actual del repositorio, no contra documentación previa.

---

## 1. Prerrequisitos

| Herramienta | Versión | ¿Cuándo la necesitas? |
|---|---|---|
| **Java (JDK)** | 21 | Siempre — para compilar/ejecutar el backend, incluso si vas a correrlo dentro de Docker (el JAR se compila en el host, no dentro del contenedor). |
| **Docker + Docker Compose** (plugin `docker compose`, v2) | Cualquier versión reciente | Siempre — incluso ejecutando el backend localmente con `spring-boot:run`, necesitas Docker para levantar PostgreSQL y para correr los tests (usan Testcontainers). |
| **Maven** | 3.9+ | Opcional — el repositorio incluye el *wrapper* `./mvnw`, que descarga la versión correcta de Maven automáticamente. No necesitas Maven instalado globalmente. |
| **Node.js** | 18+ (compatible con Vite 8 / Vue 3.5) | Solo si vas a ejecutar el **frontend**. No es necesario para correr únicamente el backend. |
| **npm** | Incluido con Node.js | Solo para el frontend. |

El usuario que ejecuta Docker debe poder correr `docker` sin `sudo` (pertenecer al grupo `docker`) o tener `sudo` configurado; de lo contrario, `setup.sh` y los tests basados en Testcontainers fallarán por permisos (ver [sección 10](#10-solución-de-problemas)).

---

## 2. Clonar el repositorio

`NovaFacts/` es una carpeta de trabajo, **no un repositorio Git único** — contiene repositorios independientes:

```
NovaFacts/
├── project-backend/       ← este repositorio (Spring Boot API)
├── project-frontend/
│   └── frontend/          ← proyecto npm real (Vue 3 + Vite)
└── project-docs/          ← documentación funcional, diagramas, casos de uso
```

Todos los comandos de esta guía, salvo que se indique lo contrario, se ejecutan **desde `project-backend/`**.

---

## 3. Configuración del entorno

El backend lee su configuración desde variables de entorno. **No arranca si falta `JWT_SECRET`.**

```bash
cd project-backend
cp .env.example .env
```

Edita `.env` y completa como mínimo:

```dotenv
POSTGRES_PASSWORD=elige_una_contraseña
JWT_SECRET=<genera_uno_con_el_comando_de_abajo>
```

Genera un secreto JWT válido (clave HMAC-SHA256 en Base64):

```bash
openssl rand -base64 48
```

### Variables

| Variable | Obligatoria | Uso |
|---|---|---|
| `JWT_SECRET` | **Sí** | Firma y verificación de los tokens JWT. Sin ella, Spring falla al resolver el placeholder `${JWT_SECRET}` en `application.properties` y la aplicación no arranca. |
| `POSTGRES_PASSWORD` | **Sí** | Contraseña del usuario de PostgreSQL, tanto para el contenedor de la base de datos como para la conexión del backend. |
| `POSTGRES_USER` | No (default `postgres`) | Usuario de PostgreSQL. |
| `JWT_EXPIRATION` | No (default `86400000` = 24 h) | Vigencia del token en milisegundos. |
| `JWT_ISSUER` / `JWT_AUDIENCE` | No | Claims `iss`/`aud` del JWT, usados en la validación de `JwtService`. |
| `USER_CACHE_TTL_SECONDS` | No (default `30`) | Cuánto tiempo se cachea `UserDetails` antes de volver a consultar la base de datos — afecta cuánto tarda en aplicarse la desactivación de un usuario. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | No (defaults `admin@novafacts.com` / `Admin2024!`) | Credenciales del usuario administrador que `AdminUserInitializer` crea automáticamente si no existe ninguno. Se usan para el primer login. |
| `CORS_ALLOWED_ORIGINS` | No (default `http://localhost:5173`) | Orígenes permitidos por CORS, para que el frontend pueda llamar a la API. |
| `SPRING_PROFILES_ACTIVE` | No (default `prod` en `docker-compose.yml`) | Pon `dev` para que además corra `DevelopmentDataSeeder` (propiedades, reservas, anticipos y facturas de ejemplo). |

`.env` está en `.gitignore` — nunca se sube al repositorio.

---

## 4. Compilar el backend

```bash
chmod +x mvnw      # solo la primera vez, si el bit de ejecución no está presente
./mvnw clean package
```

**Qué hace este comando:**
1. Limpia `target/` (`clean`).
2. Compila el código (`compile`).
3. Ejecuta la suite completa de tests (`test` — parte del ciclo de vida por defecto de Maven; **requiere que Docker esté corriendo**, porque los tests usan Testcontainers con PostgreSQL real, no una base en memoria).
4. Empaqueta un JAR ejecutable de Spring Boot (`package`), vía `spring-boot-maven-plugin` (`repackage`).

**Artefacto generado:** `target/backend-0.0.1-SNAPSHOT.jar` (nombre derivado de `artifactId`/`version` en `pom.xml`).

**Por qué el JAR debe existir antes de usar Docker:** el `Dockerfile` de este proyecto **no compila dentro del contenedor** — es de una sola etapa y simplemente copia el artefacto ya construido:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Si `target/*.jar` no existe (o está desactualizado respecto al código fuente), `docker build`/`docker compose up --build` fallará o empaquetará una versión vieja de la aplicación. Siempre corre `./mvnw clean package` antes de construir la imagen.

Si quieres saltar los tests para compilar más rápido (por ejemplo, si ya los corriste antes): `./mvnw clean package -DskipTests`.

---

## 5. Ejecutar con Docker (recomendado)

```bash
# 1. Compilar el JAR (paso 4, si no lo hiciste ya)
./mvnw clean package

# 2. Levantar todo el stack
docker compose up -d
```

**Qué se levanta:**

| Servicio | Contenedor | Puerto host | Puerto interno | Imagen |
|---|---|---|---|---|
| PostgreSQL | `novafacts_postgres` | `5434` | `5432` | `postgres:15.18-alpine` |
| Backend (Spring Boot) | `novafacts_backend` | `8082` | `8082` | Construida localmente desde `Dockerfile` |

El backend espera a que PostgreSQL pase su *healthcheck* (`pg_isready`) antes de arrancar (`depends_on: condition: service_healthy`).

**Verificar que todo esté corriendo:**

```bash
docker compose ps
# Ambos servicios deben mostrar "Up" / "healthy"

docker compose logs spring_app --tail 30
# Busca la línea: "Started BackendApplication in X seconds"
```

**Por qué este es el método recomendado:** es el único que replica el entorno de despliegue real (imagen JRE fijada a una versión, PostgreSQL con la misma versión pinneada en `docker-compose.yml`, variables de entorno inyectadas igual que en producción), y no requiere tener PostgreSQL instalado en el host — todo queda aislado en contenedores con un volumen persistente (`postgres_data`).

Para reconstruir la imagen del backend tras cambiar código (no solo reiniciar): `docker compose up --build -d`.

---

## 6. Ejecutar el backend localmente (sin contenedor de la app)

Útil para desarrollo activo con recarga rápida, dejando solo la base de datos en Docker.

```bash
# 1. Levantar solo PostgreSQL
docker compose up -d postgres_db

# 2. Exportar las variables necesarias (o cargarlas desde .env)
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=tu_contraseña
export JWT_SECRET=$(grep JWT_SECRET .env | cut -d= -f2)

# 3. Arrancar Spring Boot
./mvnw spring-boot:run
```

**Puertos y configuración usada:** en este modo, `application.properties` usa `spring.datasource.url=jdbc:postgresql://localhost:5434/novafacts_db` — apunta al puerto expuesto en el host por el contenedor de PostgreSQL (`5434`), no al puerto interno del contenedor. El backend queda igualmente en el puerto `8082`. Esta URL hardcodeada solo aplica a este modo de ejecución local; cuando corres vía `docker compose up` (sección 5), `docker-compose.yml` la sobreescribe con `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres_db:5432/novafacts_db` (resolución por nombre de servicio dentro de la red de Docker).

---

## 7. Ejecutar el frontend

```bash
cd ../project-frontend/frontend
npm install
npm run dev
```

**URL esperada:** `http://localhost:5173` (puerto por defecto de Vite).

El frontend llama a la API en `http://localhost:8082/api` (URL fija en el código, sin variable de entorno propia) — asegúrate de que el backend ya esté corriendo (sección 5 o 6) antes de probar el login desde la interfaz. El backend debe permitir CORS desde `http://localhost:5173`, que es el valor por defecto de `CORS_ALLOWED_ORIGINS` si no lo sobreescribes.

---

## 8. Uso de `setup.sh`

```bash
bash setup.sh
```

**Qué automatiza:** valida que `.env` exista y tenga `JWT_SECRET`/`POSTGRES_PASSWORD`, verifica que Java/Node/npm estén instalados, instala las dependencias del frontend (`npm install`), compila y prueba el backend (`./mvnw clean package` + `./mvnw test`), reconstruye y levanta el stack de Docker (`docker compose down && docker compose up --build -d`), libera el puerto `5173` si está ocupado, y finalmente arranca `npm run dev`.

**Cuándo usarlo:** es razonable para un primer *bootstrap* completo en una máquina nueva, ya que es el único camino que también prepara el frontend. Si solo necesitas el backend, o ya tienes el entorno preparado, los pasos 4-7 de esta guía por separado son más rápidos y directos.

**Limitaciones verificadas en el script actual** (no asumidas — confirmadas leyendo `setup.sh` línea por línea):

- **Ejecuta los tests dos veces sin necesidad.** `./mvnw clean package` ya corre la suite completa de tests como parte del ciclo de vida por defecto de Maven (ver sección 4); el script llama `./mvnw test` inmediatamente después, repitiendo toda la suite (incluye levantar Testcontainers de nuevo) sin ningún beneficio adicional. Esto aproximadamente duplica el tiempo de este paso.
- **Usa `sudo docker compose` y `sudo fuser`.** Si tu usuario ya pertenece al grupo `docker` (lo habitual en instalaciones modernas), `sudo` es innecesario y solo añade una solicitud de contraseña. En entornos donde sí se requiere, esto funciona correctamente; en los que no, es fricción evitable.
- **No verifica que el daemon de Docker esté accesible antes de avanzar.** El script comprueba que los binarios `java`/`node`/`npm` existan, pero no hace un `docker version`/chequeo de conectividad temprano. Si el daemon no está corriendo o el socket no es accesible, el fallo ocurre recién en el paso de tests (paso 5 de 6), después de haber compilado todo, en vez de fallar rápido al inicio.

Ninguna de estas limitaciones impide que el script funcione — son ineficiencias y suposiciones de entorno, no errores funcionales. No lo recomiendo *ciegamente* como el único camino: para trabajo diario de desarrollo, usar los comandos de las secciones 4-7 por separado da más control y evita la duplicación de tests.

---

## 9. Verificar la instalación

**PostgreSQL está corriendo:**
```bash
docker compose ps postgres_db
# STATUS debe decir "Up ... (healthy)"

docker compose exec postgres_db pg_isready -U postgres
# Esperado: "accepting connections"
```

**El backend está saludable:**
```bash
docker compose logs spring_app --tail 20
# Busca: "Started BackendApplication in X seconds"
```

**La autenticación funciona**, usando las credenciales por defecto creadas por `AdminUserInitializer` (o las que definiste en `ADMIN_EMAIL`/`ADMIN_PASSWORD`):
```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@novafacts.com","password":"Admin2024!"}'
```
Respuesta esperada — `200 OK` con un JWT:
```json
{"token":"eyJ...", "rol":"Administrador", "nombre":"..."}
```
Si en cambio recibes `401` con `{"error":"Credenciales inválidas"}`, revisa que `ADMIN_EMAIL`/`ADMIN_PASSWORD` coincidan con lo que configuraste, o que el usuario admin realmente se haya creado (revisa los logs del contenedor al arrancar — `AdminUserInitializer` imprime un banner de advertencia con las credenciales usadas).

**Un endpoint protegido responde correctamente con el token:**
```bash
TOKEN="<pega aquí el token de la respuesta anterior>"
curl http://localhost:8082/api/temporadas -H "Authorization: Bearer $TOKEN"
# Esperado: 200 con una lista JSON (puede estar vacía si no sembraste datos de ejemplo)
```

**El frontend carga:** abre `http://localhost:5173` en el navegador — debería mostrar la pantalla de login sin errores de CORS en la consola.

**Puertos usados en total:** `5434` (PostgreSQL, host), `8082` (backend), `5173` (frontend, solo en modo desarrollo con `npm run dev`).

---

## 10. Solución de problemas

**El daemon de Docker no está corriendo o no es accesible**
→ Síntoma: `docker compose up` falla con `Cannot connect to the Docker daemon`, o los tests fallan con `Could not find a valid Docker environment`. Verifica con `docker version` (debe mostrar tanto `Client` como `Server`). Si `Server` falla con `permission denied`, tu usuario no pertenece al grupo `docker` — añádelo con `sudo usermod -aG docker $USER` y reinicia la sesión, o usa `sudo` para los comandos de Docker.

**`Could not resolve placeholder 'JWT_SECRET'` al arrancar**
→ `.env` no existe, no se cargó, o `JWT_SECRET` está vacío. Si usas `docker compose`, confirma que `.env` esté en `project-backend/` (Docker Compose lo carga automáticamente desde el directorio del `docker-compose.yml`). Si corres `spring-boot:run` localmente, confirma que exportaste la variable en la misma shell.

**`docker compose up` construye una versión vieja de la aplicación, o falla porque no hay JAR**
→ No corriste `./mvnw clean package` antes, o lo corriste antes de tu último cambio de código. El `Dockerfile` no compila nada — solo copia `target/*.jar`. Vuelve a compilar y reconstruye con `docker compose up --build -d`.

**Puerto ocupado (`5434`, `8082` o `5173`)**
→ Verifica qué proceso lo usa: `lsof -i :8082` (o el puerto correspondiente). Si es un contenedor de una ejecución anterior, `docker compose down` lo libera. Si es un proceso local (por ejemplo, otro `spring-boot:run` colgado), termínalo o cambia el puerto en `docker-compose.yml`/`application.properties`.

**Los tests fallan con `Could not find a valid Docker environment` (Testcontainers)**
→ Los tests de este proyecto usan PostgreSQL real vía Testcontainers, no una base en memoria — necesitan acceso genuino al daemon de Docker, igual que el punto anterior sobre permisos. Confirma `docker version` funciona sin `sudo` antes de correr `./mvnw test`.

**Error de conexión a la base de datos al correr `spring-boot:run` localmente**
→ Confirma que el contenedor de PostgreSQL esté corriendo (`docker compose ps postgres_db`) y que `POSTGRES_PASSWORD` en tu shell coincida con el valor en `.env` que usó el contenedor al crearse. Si cambiaste la contraseña después de que el volumen `postgres_data` ya existía, PostgreSQL sigue usando la contraseña original del volumen — necesitas recrearlo (`docker compose down -v`, **esto borra los datos**) o usar la contraseña original.

**`403 Forbidden` inesperado en un endpoint**
→ El token es válido pero el rol del usuario no tiene permiso sobre ese recurso — revisa la tabla de RBAC en `README.md`. El cuerpo de un `403` va vacío (no personalizado); no esperes un JSON con detalle del motivo.

**`401 Unauthorized` en un endpoint protegido**
→ Falta el header `Authorization: Bearer <token>`, el token expiró (`JWT_EXPIRATION`, 24 h por defecto), o el usuario fue desactivado y la caché de `UserDetails` (`USER_CACHE_TTL_SECONDS`, 30 s por defecto) ya venció desde la desactivación.

**Problemas instalando dependencias del frontend**
→ `npm install` fallando por versión de Node: confirma Node 18+ (`node --version`). Si `npm install` deja `node_modules` en un estado inconsistente tras una interrupción, bórralo y reinstala: `rm -rf node_modules package-lock.json && npm install` (ojo: esto puede actualizar versiones si `package-lock.json` no se conserva del repositorio — normalmente no necesitas borrar `package-lock.json`, solo `node_modules`, si el problema es una instalación parcial).
