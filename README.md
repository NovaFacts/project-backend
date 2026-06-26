# NovaFacts — Backend

Spring Boot 3.5 · Java 21 · PostgreSQL · JWT

---

## Requisitos previos

| Herramienta | Versión mínima |
|---|---|
| Java (JDK) | 21 |
| Docker + Docker Compose | cualquier versión reciente |
| Maven (opcional) | 3.9+ — o usa el wrapper `./mvnw` incluido |

---

## Configuración del entorno

El backend requiere variables de entorno para arrancar. **Sin ellas, la aplicación no inicia.**

### 1. Copia el archivo de ejemplo

```bash
cp .env.example .env
```

### 2. Edita `.env` y completa los valores obligatorios

```dotenv
# Base de datos
POSTGRES_USER=postgres
POSTGRES_PASSWORD=elige_una_contraseña_segura

# JWT — OBLIGATORIO, no tiene valor por defecto
JWT_SECRET=<genera_un_secreto_con_el_comando_de_abajo>
```

### 3. Genera un secreto JWT seguro

```bash
openssl rand -base64 48
```

Copia el resultado como valor de `JWT_SECRET` en tu `.env`. Usa un secreto diferente en cada entorno (desarrollo, staging, producción). **Nunca compartas ni subas este valor al repositorio.**

### Variables disponibles

| Variable | Obligatoria | Descripción |
|---|---|---|
| `POSTGRES_PASSWORD` | Sí | Contraseña de PostgreSQL |
| `POSTGRES_USER` | No | Usuario de PostgreSQL (default: `postgres`) |
| `JWT_SECRET` | **Sí** | Clave HMAC-SHA256 en Base64 (mín. 32 bytes) |
| `JWT_EXPIRATION` | No | Tiempo de vida del token en ms (default: `86400000` = 24 h) |

---

## Ejecución con Docker Compose (recomendado)

```bash
# Desde project-backend/
docker compose up -d
```

Esto levanta:
- **PostgreSQL** en el puerto `5434` del host
- **Spring Boot** en el puerto `8082` del host

Verificar que todo esté corriendo:

```bash
docker compose ps
curl http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'
# Esperado: 500 (usuario no existe) — confirma que el servidor responde
```

---

## Ejecución local (sin Docker)

Asegúrate de tener PostgreSQL corriendo (puedes levantar solo el contenedor de DB):

```bash
docker compose up -d postgres_db
```

Luego exporta las variables y arranca Spring Boot:

```bash
# Las variables se cargan desde .env si usas setup.sh,
# o expórtalas manualmente:
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=tu_contraseña
export JWT_SECRET=$(grep JWT_SECRET .env | cut -d= -f2)

./mvnw spring-boot:run
```

---

## Setup completo (primera vez)

El script `setup.sh` verifica dependencias, instala el frontend, compila el backend, corre los tests y levanta el stack completo:

```bash
# Desde project-backend/
# Asegúrate de que .env existe y tiene JWT_SECRET antes de correr esto
bash setup.sh
```

El script falla con un mensaje claro si `.env` no existe o si `JWT_SECRET` o `POSTGRES_PASSWORD` están vacíos.

---

## Comandos Maven útiles

```bash
./mvnw clean compile          # compilar
./mvnw test                   # correr todos los tests
./mvnw spring-boot:run        # arrancar en modo desarrollo (puerto 8082)
```

---

## Endpoints principales

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/login` | No | Autentica usuario, devuelve JWT |
| `POST` | `/api/users` | Sí | Crea usuario |
| `GET` | `/api/users` | Sí | Lista usuarios |
| `DELETE` | `/api/users/{id}` | Sí | Elimina usuario |

Los endpoints protegidos requieren el header:
```
Authorization: Bearer <token>
```

---

## Solución de problemas

**La aplicación no arranca y dice `Could not resolve placeholder 'JWT_SECRET'`**
→ El archivo `.env` no existe o `JWT_SECRET` está vacío. Sigue la sección de configuración del entorno.

**Error de conexión a la base de datos**
→ Verifica que el contenedor de PostgreSQL esté corriendo: `docker compose ps`. Comprueba `POSTGRES_PASSWORD` en `.env`.

**`401 Unauthorized` al llamar a un endpoint protegido**
→ Incluye el header `Authorization: Bearer <token>`. Obtén el token con `POST /api/auth/login`.

**`403 Forbidden` inesperado**
→ El token es válido pero el usuario no tiene permiso sobre ese recurso. En la fase actual, todos los usuarios autenticados tienen acceso a `/api/users/**`.
