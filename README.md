# NovaFacts — Backend

Sistema de gestión financiera para alojamientos de corta estadía: reservas, anticipos, penalidades, facturación y devoluciones. Proyecto académico de Ingeniería de Software 1 (Universidad Nacional de Colombia).

**Spring Boot 3.5.14 · Java 21 · PostgreSQL 15 · JWT · Maven**

---

## Descripción general

El backend expone una API REST que administra el ciclo financiero completo de una reserva de alojamiento:

- **Datos de referencia**: propiedades, canales de reserva, temporadas, políticas de cancelación.
- **Reservas**: creación, actualización y cancelación, con control de solapamiento de fechas por propiedad.
- **Ciclo financiero**: anticipos, penalidades por cancelación, facturas, notas de crédito y devoluciones.
- **Autenticación y autorización**: JWT con roles (Administrador, Contador, Auxiliar contable, Recepcionista).

---

## Arquitectura

Estructura por *feature package* bajo `com.novafacts.backend`:

```
com.novafacts.backend
├── BackendApplication.java     Punto de entrada de Spring Boot
├── auth/                       Login, gestión de usuarios, JWT (filtro, servicio, UserDetailsService)
├── config/                     SecurityConfig, CacheConfig, seeders de datos iniciales
├── common/                     GlobalExceptionHandler, PageResponse
├── property/                   Propiedades
├── canal/                      Canales de reserva (Airbnb, Booking, Web propia, Teléfono, WhatsApp)
├── temporada/                  Temporadas (rangos de fecha, sin solapamiento)
├── politicacancelacion/        Políticas de cancelación por propiedad
├── reservation/                Reservas
├── anticipo/                   Anticipos sobre una reserva
├── penalidad/                  Penalidades por cancelación
├── factura/                    Facturación
├── notacredito/                Notas de crédito
├── devolucion/                 Devoluciones de anticipos
├── dashboard/                  Agregados/resumen financiero
├── rol/                        Roles del sistema
└── invoice/entity/             Enumeración compartida InvoiceStatus (PENDING, PAID, CANCELLED)
```

Cada módulo sigue el patrón `Controller → Service → Repository → Entity`. Los controladores son delgados (solo delegan), la lógica de negocio vive en los servicios, y los repositorios son interfaces `JpaRepository` con consultas derivadas o `@Query` explícito cuando el nombre derivado entraría en conflicto (por ejemplo, `findByClienteEmailAndCheckInDate`).

---

## Tecnologías

| Componente | Tecnología |
|---|---|
| Backend | Spring Boot 3.5.14, Java 21, Spring Data JPA, Spring Security (`@EnableMethodSecurity`), Flyway |
| Autenticación | JWT (JJWT 0.12.6), BCrypt |
| Caché | Spring Cache + Caffeine (`UserDetailsServiceImpl.loadUserByUsername`, TTL configurable) |
| Base de datos | PostgreSQL 15 (`postgres:15.18-alpine` en Docker) |
| Persistencia | Migraciones versionadas con Flyway (`spring.jpa.hibernate.ddl-auto=validate` — el esquema **no** se genera desde las entidades) |
| Tests | JUnit 5, Testcontainers (PostgreSQL real, no H2), Spring Security Test, Mockito |
| Frontend | Vue 3, Vite, Vue Router, Axios, TypeScript (parcial) |
| Contenedores | Docker, Docker Compose |
| Build | Maven (`./mvnw`, sin necesidad de Maven instalado localmente) |

---

## Ubicación de backend y frontend

Este repositorio (`project-backend/`) es un repositorio Git independiente que solo contiene el backend. El frontend vive en un repositorio hermano:

```
NovaFacts/                     (carpeta de trabajo, no es un repo Git)
├── project-backend/           ← este repositorio
└── project-frontend/
    └── frontend/              ← proyecto npm real (Vue 3 + Vite)
```

El frontend consume la API en `http://localhost:8082/api` (URL fija, sin variables de entorno propias) y espera CORS habilitado para `http://localhost:5173` (origen por defecto del servidor de desarrollo de Vite). Ver la guía completa de ejecución de ambos proyectos en [`RUNNING.md`](./RUNNING.md).

---

## Variables de entorno

El backend **no arranca** sin `JWT_SECRET`. Copia `.env.example` a `.env` y complétalo antes de ejecutar cualquier flujo:

```bash
cp .env.example .env
```

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `JWT_SECRET` | **Sí** | *(sin valor por defecto — falla al arrancar si falta)* | Clave HMAC-SHA256 en Base64. Generar con `openssl rand -base64 48`. |
| `POSTGRES_PASSWORD` | **Sí** | *(sin valor por defecto)* | Contraseña de PostgreSQL. |
| `POSTGRES_USER` | No | `postgres` | Usuario de PostgreSQL. |
| `JWT_EXPIRATION` | No | `86400000` (24 h) | Vigencia del token JWT en milisegundos. |
| `JWT_ISSUER` | No | `novafacts-backend` | Claim `iss` del JWT. |
| `JWT_AUDIENCE` | No | `novafacts-api` | Claim `aud` del JWT. |
| `USER_CACHE_TTL_SECONDS` | No | `30` | TTL de la caché de `UserDetails` (ver sección de autenticación). |
| `ADMIN_EMAIL` | No | `admin@novafacts.com` | Correo del usuario administrador creado automáticamente al arrancar (`AdminUserInitializer`). |
| `ADMIN_PASSWORD` | No | `Admin2024!` | Contraseña inicial de ese usuario. **Cámbiala en cualquier entorno real.** |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173` | Orígenes permitidos por CORS, separados por comas. |
| `SPRING_PROFILES_ACTIVE` | No | `prod` (en `docker-compose.yml`) | Usa `dev` para además ejecutar `DevelopmentDataSeeder` (datos de ejemplo: propiedades, reservas, anticipos, facturas). |

`ADMIN_EMAIL`/`ADMIN_PASSWORD`/`JWT_ISSUER`/`JWT_AUDIENCE`/`USER_CACHE_TTL_SECONDS`/`CORS_ALLOWED_ORIGINS` no aparecen en `.env.example` porque todas tienen un valor por defecto funcional; solo necesitas definirlas si quieres cambiar ese comportamiento.

---

## Autenticación

Flujo: `JwtAuthenticationFilter` → `JwtService` (valida firma/issuer/audience/expiración) → `UserDetailsServiceImpl` (carga el usuario, cacheado con TTL corto vía Caffeine) → `SecurityContextHolder`.

**Login:**

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@novafacts.com",
  "password": "Admin2024!"
}
```

Respuesta `200 OK`:
```json
{
  "token": "<jwt>",
  "rol": "Administrador",
  "nombre": "..."
}
```

Credenciales inválidas (usuario inexistente, contraseña incorrecta o cuenta desactivada) → `401 Unauthorized`:
```json
{"error": "Credenciales inválidas"}
```

**Endpoints protegidos** requieren el header:
```
Authorization: Bearer <token>
```

Un usuario desactivado (`DELETE /api/usuarios/{id}` hace *soft delete*, no borrado físico) deja de poder autenticarse en un plazo acotado por `USER_CACHE_TTL_SECONDS` (30 s por defecto) — no inmediatamente en la siguiente petición, ya que `UserDetailsServiceImpl.loadUserByUsername()` está cacheado.

---

## Roles y control de acceso (RBAC)

Roles sembrados por Flyway (`V2__agregar_rol_y_restricciones.sql`), no configurables desde la API:

| Rol | Alcance |
|---|---|
| **Administrador** | Acceso total: usuarios, propiedades, canales, temporadas, políticas, y todo lo que cubren los demás roles. |
| **Contador** | Facturas, notas de crédito, devoluciones, anticipos y penalidades (lectura y escritura). |
| **Auxiliar contable** | Anticipos y penalidades (lectura y escritura). Sin acceso a facturas, notas de crédito ni devoluciones. |
| **Recepcionista** | Sin permisos administrativos o financieros explícitos. Puede operar sobre reservas y leer datos de referencia, igual que cualquier usuario autenticado. |

Reglas de autorización (`SecurityConfig`, `@EnableMethodSecurity`):

| Recurso | Lectura (GET) | Escritura (POST/PUT/DELETE) |
|---|---|---|
| `/api/auth/**` | público | — |
| `/api/usuarios/**` | `ADMINISTRADOR` | `ADMINISTRADOR` |
| `/api/roles/**` | `ADMINISTRADOR` | *(sin endpoints de escritura)* |
| `/api/propiedades/**`, `/api/canales/**`, `/api/temporadas/**`, `/api/politicas/**` | cualquier usuario autenticado | `ADMINISTRADOR` |
| `/api/facturas/**`, `/api/notas-credito/**`, `/api/devoluciones/**` | `ADMINISTRADOR`, `CONTADOR` | `ADMINISTRADOR`, `CONTADOR` |
| `/api/anticipos/**`, `/api/penalidades/**` | `ADMINISTRADOR`, `CONTADOR`, `AUXILIAR_CONTABLE` | `ADMINISTRADOR`, `CONTADOR`, `AUXILIAR_CONTABLE` |
| `/api/reservas/**` (alias `/api/reservations`) | cualquier usuario autenticado | cualquier usuario autenticado |
| `/api/dashboard` | cualquier usuario autenticado | — |

> `/api/reservas` y `/api/dashboard` no tienen restricción de rol específica: cualquier usuario autenticado (incluido Recepcionista) puede acceder. En el caso de `/api/dashboard`, esto expone agregados financieros (total de anticipos, conteos por estado de factura) sin la misma restricción que tienen `/api/anticipos` y `/api/facturas` directamente — es un hallazgo documentado (M-14) en la auditoría más reciente, no un comportamiento a asumir como definitivo.

**Comportamiento de respuesta según el error:**

| Situación | Código | Cuerpo |
|---|---|---|
| Credenciales inválidas / token ausente o inválido | `401` | `{"error": "..."}` |
| Autenticado pero sin el rol requerido | `403` | *(cuerpo vacío — comportamiento por defecto de Spring Security, no personalizado)* |
| Validación de payload (`@Valid`) | `400` | `{"error": "campo: mensaje; campo2: mensaje2"}` (todos los errores, no solo el primero) |
| Recurso no encontrado | `404` | `{"error": "..."}` |
| Conflicto de negocio (duplicados, referencias existentes, condición de carrera) | `409` | `{"error": "..."}` |
| Error no controlado | `500` | `{"error": "Error interno del servidor"}` (sin detalles internos ni stack trace) |

---

## Endpoints principales

Todas las rutas responden JSON. El listado (`GET` sin sufijo) de los módulos marcados con 📄 acepta paginación (`?page=0&size=20`, tamaño máximo 100) y responde `PageResponse<T>`; el resto de listados responde un arreglo simple.

| Módulo | Ruta base | Operaciones | Notas |
|---|---|---|---|
| Autenticación | `/api/auth` | `POST /login` | Público |
| Usuarios | `/api/usuarios` 📄 | `GET`, `POST`, `DELETE /{id}` | Solo `ADMINISTRADOR`. `DELETE` es *soft delete* (desactiva, no borra). |
| Roles | `/api/roles` | `GET` | Solo lectura, para todos los roles `ADMINISTRADOR` |
| Propiedades | `/api/propiedades` 📄 | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | Lectura: cualquier autenticado. Escritura: solo `ADMINISTRADOR`. |
| Canales | `/api/canales` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | ídem |
| Temporadas | `/api/temporadas` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | ídem. Sin solapamiento de fechas; nombre único (case-insensitive). |
| Políticas de cancelación | `/api/politicas` | `GET`, `GET /{id}`, `GET /propiedad/{propiedadId}`, `POST`, `PUT /{id}`, `DELETE /{id}` | ídem |
| Reservas | `/api/reservas` (alias `/api/reservations`) 📄 | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | Cualquier usuario autenticado. Control de solapamiento por propiedad. |
| Anticipos | `/api/anticipos` 📄 | `GET`, `GET /{id}`, `GET /by-reserva/{reservaId}`, `POST`, `DELETE /{id}` | `ADMINISTRADOR`, `CONTADOR`, `AUXILIAR_CONTABLE` |
| Penalidades | `/api/penalidades` 📄 | `GET`, `GET /{id}`, `GET /by-reserva/{reservaId}`, `POST`, `DELETE /{id}` | ídem |
| Facturas | `/api/facturas` 📄 | `GET`, `GET /{id}`, `GET /by-reserva/{reservaId}`, `POST`, `PUT /{id}/emitir`, `PUT /{id}/anular`, `DELETE /{id}` | `ADMINISTRADOR`, `CONTADOR` |
| Notas de crédito | `/api/notas-credito` 📄 | `GET`, `GET /{id}`, `GET /by-factura/{facturaId}`, `POST`, `DELETE /{id}` | ídem |
| Devoluciones | `/api/devoluciones` 📄 | `GET`, `GET /{id}`, `GET /by-reserva/{reservaId}`, `POST`, `PUT /{id}/procesar`, `PUT /{id}/rechazar`, `DELETE /{id}` | ídem |
| Dashboard | `/api/dashboard` | `GET` | Resumen agregado. Cualquier usuario autenticado (ver nota de RBAC arriba). |

---

## Ejecución rápida

```bash
cp .env.example .env        # completa POSTGRES_PASSWORD y JWT_SECRET
./mvnw clean package        # genera el JAR que el Dockerfile copia
docker compose up -d        # levanta PostgreSQL (5434) + backend (8082)
```

Guía completa, con todas las alternativas de ejecución y solución de problemas, en [`RUNNING.md`](./RUNNING.md).

---

## Comandos Maven

```bash
./mvnw clean compile                              # compilar
./mvnw test                                       # correr toda la suite (usa Testcontainers, requiere Docker)
./mvnw test -Dtest=NombreDeLaClase                # correr una sola clase de test
./mvnw test -Dtest=Clase#metodo                   # correr un solo método
./mvnw spring-boot:run                            # arrancar en modo desarrollo (puerto 8082)
```

Los tests usan PostgreSQL real vía Testcontainers (`application-test.properties`), no una base en memoria — el daemon de Docker debe estar accesible para ejecutarlos.

---

## `setup.sh`

Script de arranque para un checkout limpio: valida `.env`, verifica Java/Node/npm, instala dependencias del frontend, compila y prueba el backend, reconstruye el stack de Docker, y arranca el servidor de desarrollo del frontend. Ver el detalle de qué hace exactamente, sus limitaciones actuales y cuándo conviene usarlo (o no) en [`RUNNING.md`](./RUNNING.md#8-uso-de-setupsh).

---

## Flujo de desarrollo

1. Levanta la base de datos: `docker compose up -d postgres_db`.
2. Corre el backend en modo desarrollo: `./mvnw spring-boot:run` (recarga manual; sin *devtools* configurado).
3. Corre el frontend: `cd ../project-frontend/frontend && npm run dev` (puerto 5173, recarga en caliente).
4. Antes de un commit: `./mvnw test` para validar contra PostgreSQL real vía Testcontainers.
5. Las migraciones nuevas van en `src/main/resources/db/migration/`, numeradas secuencialmente (`V16__...sql`, etc.) — el esquema se valida (`ddl-auto=validate`), nunca se genera automáticamente desde las entidades.

---

## Documentación adicional

- [`RUNNING.md`](./RUNNING.md) — guía completa paso a paso para clonar y ejecutar el proyecto desde cero.
- [`AUDIT_v5.md`](https://github.com/NovaFacts/project-docs/blob/main/audits/backend/AUDIT_v5.md) — auditoría de seguridad y calidad de código más reciente (hallazgos, severidad, estado de resolución). El historial completo de auditorías (`AUDIT.md`–`AUDIT_v5.md`) vive en [`project-docs/audits/backend/`](https://github.com/NovaFacts/project-docs/tree/main/audits/backend).
