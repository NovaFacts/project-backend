# NovaFacts Backend — Release-Readiness Audit

*Audited against commit `b9b4565` + working-tree changes on 2026-06-30. 98 Java source files and 13 SQL migrations reviewed.*

---

## 1. Executive Summary

| Dimension | Estimate |
|-----------|----------|
| Feature completion | ~78% |
| Production readiness | **~45%** |
| Maintainability | ~65% |
| Technical debt | **Medium-High** |

The project has solid bones: a well-organized feature-package layout, proper JWT authentication, granular RBAC, pessimistic locking for double-booking prevention, idempotent Flyway migrations, and a sound financial state machine. However, several issues would cause real harm in production — the two most serious are a **data-corruption bug** in devolucion deletion and **SQL credentials logged at TRACE level** in the main config. The Docker story also has a critical port mismatch and inadvertently runs demo-data seeding against what looks like a production compose file. None of these are architectural rewrites; they are targeted fixes.

---

## 2. Scorecard

| Area | Score | Verdict |
|------|-------|---------|
| Architecture | 68/100 | Solid structure, dead `booking` package, oversized service constructors |
| Backend | 67/100 | State machine correct, two data-integrity bugs, missing status-transition guards |
| Database | 71/100 | Good migrations, missing DB-level uniqueness on `propiedad.nombre`, no financial-table indexes |
| Security | 65/100 | JWT + RBAC solid, SQL TRACE logging in prod config, no rate limiting, weak JWT claims |
| API Design | 60/100 | REST-correct, inconsistent pagination types, missing `@Email` on client email, wrong response wrapping on one endpoint |
| Docker | 48/100 | Wrong EXPOSE port, dev profile hardcoded, no `.dockerignore`, CORS_ALLOWED_ORIGINS absent |
| Frontend Integration | 42/100 | No OpenAPI docs, localhost CORS default leaks into prod, API version not in path |
| Code Quality | 72/100 | Consistent patterns, dead `booking` + empty test packages, SQL logging left in base config |
| Maintainability | 66/100 | Feature packages clear, inconsistent pagination across services, 10-arg service constructor |
| Production Readiness | **43/100** | Three critical deployment blockers must be resolved first |
| **Overall** | **63/100** | Approaching release; 4–6 focused fixes needed |

---

## 3. Findings

### CRITICAL

---

**C-1 — DevolucionService.delete() leaves anticipo stranded in DEVUELTO state (data corruption)**

- **Problem:** `DevolucionService.delete()` at line 129 deletes a PENDIENTE devolucion but never reverts `anticipo.estado` from `DEVUELTO` back to `REGISTRADO`. After deletion the anticipo is permanently stuck: `DEVUELTO` but no devolucion exists. It cannot be applied to a factura (FacturaService checks `REGISTRADO`) and cannot create a new devolucion (DevolucionService checks `REGISTRADO`). The advance payment becomes unrecoverable.
- **Impact:** Financial record corruption. Operational: accounting staff must manually fix the database via SQL.
- **Location:** `devolucion/service/DevolucionService.java:129`
- **Fix:** Before `devolucionRepository.delete(devolucion)`, load the associated anticipo and set `anticipo.setEstado(AnticipoEstado.REGISTRADO); anticipoRepository.save(anticipo);`
- **Priority:** Must fix before any real financial data enters the system.
- **Effort:** 15 minutes.

---

**C-2 — SQL query parameters logged at TRACE in the base `application.properties`**

- **Problem:** `application.properties` (lines 13–15) sets `spring.jpa.show-sql=true`, `logging.level.org.hibernate.SQL=DEBUG`, and `logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE`. The TRACE level logs the actual bound values — including password hashes during login queries — to stdout. The Docker container inherits these settings since `SPRING_PROFILES_ACTIVE=dev` overrides them only if `application-dev.properties` explicitly re-declares them (it does not).
- **Impact:** Password hashes (and all PII in queries) written to container stdout in production.
- **Location:** `src/main/resources/application.properties:10,14,15`
- **Fix:** Move all three lines to `application-dev.properties`. The base config should be prod-safe by default.
- **Priority:** Must fix before production deployment.
- **Effort:** 5 minutes.

---

**C-3 — Dockerfile EXPOSE port mismatch (8081 vs 8082)**

- **Problem:** `Dockerfile` line 4 says `EXPOSE 8081`, but the app binds to `8082` (defined in `application.properties:17`). Docker Compose's explicit `ports: "8082:8082"` override makes this work in compose, but any Kubernetes manifest, platform-as-a-service, or `docker run -P` invocation uses EXPOSE to determine the forwarded port — those would silently forward the wrong port.
- **Impact:** Silent failure in any non-compose deployment.
- **Location:** `Dockerfile:4`
- **Fix:** Change `EXPOSE 8081` to `EXPOSE 8082`.
- **Priority:** Fix immediately.
- **Effort:** 1 line.

---

**C-4 — `docker-compose.yml` hardcodes `SPRING_PROFILES_ACTIVE=dev`, running the demo seeder against a production database**

- **Problem:** `docker-compose.yml` line 30 sets `SPRING_PROFILES_ACTIVE=dev` with no provision to override it. `DevelopmentDataSeeder` is `@Profile("dev")`, so it runs in this compose setup on every clean deploy. If this file is used to deploy the production stack — which is the only compose file in the repository — the seeder populates the production database with fake users and reservations.
- **Impact:** Demo data in production. Seeded users (`contador@novafacts.com`, `auxiliar@novafacts.com`, etc.) with known passwords become live accounts.
- **Location:** `docker-compose.yml:30`
- **Fix:** Change to `SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}` and create an `application-prod.properties` that sets `app.seed-demo-data=false` and suppresses SQL logging.
- **Priority:** Must fix before production deployment.
- **Effort:** 30 minutes.

---

### HIGH

---

**H-1 — `ReservationService.update()` has no status-transition validation**

- **Problem:** `update()` at line 166 does `reservation.setStatus(request.getStatus())` with no guard. Any role that can update a reservation can transition a CANCELLED or COMPLETED reservation back to CONFIRMED. There is no state machine.
- **Impact:** A CANCELLED reservation with an associated `Penalidad` can be re-CONFIRMED. Financial records become inconsistent.
- **Location:** `reservation/service/ReservationService.java:166`
- **Fix:** Add allowed-transition validation: COMPLETED and CANCELLED are terminal states that cannot be changed; CONFIRMED can transition to CANCELLED or COMPLETED.
- **Effort:** 45 minutes.

---

**H-2 — `AnticipoService.delete()` silently allows deleting DEVUELTO anticipos, producing a cryptic FK error**

- **Problem:** The delete guard at line 76 only blocks `APLICADO`. An anticipo in `DEVUELTO` state has a child `devolucion` row via FK. The DELETE will throw a `DataIntegrityViolationException` caught by `GlobalExceptionHandler` and returned as `"Conflicto de datos"` — giving the user no actionable information.
- **Impact:** Poor UX; the user doesn't know what child record blocks the delete.
- **Location:** `anticipo/service/AnticipoService.java:76`
- **Fix:** Add `AnticipoEstado.DEVUELTO` to the guard with a descriptive message: `"No se puede eliminar un anticipo que ya tiene una devolución asociada"`.
- **Effort:** 5 minutes.

---

**H-3 — `ReservationRepository.findByClienteEmail` returns `Optional<Reservation>` but the field is not unique**

- **Problem:** `Reservation.clienteEmail` has no `@Column(unique=true)` and no DB UNIQUE constraint. Spring Data derives the query as `SELECT * FROM reserva WHERE cliente_email = ?`. If two reservations share an email, Spring Data throws `IncorrectResultSizeDataAccessException` at runtime.
- **Impact:** Potential `500` errors in production if any client makes two bookings.
- **Location:** `reservation/repository/ReservationRepository.java:14`
- **Fix:** The seeder should use a more stable key (e.g., email + check-in date). The `findByClienteEmail` method should either be removed or changed to return `List<Reservation>`.
- **Effort:** 20 minutes.

---

**H-4 — No rate limiting on login endpoint**

- **Problem:** `POST /api/auth/login` has no brute-force protection. An attacker can submit unlimited login attempts. The timing equalization prevents email enumeration but does not prevent credential stuffing.
- **Impact:** Brute-force attacks on user accounts are trivial; no account lockout or CAPTCHA after N failures.
- **Location:** `auth/controller/AuthController.java:25`
- **Fix:** Add a Bucket4j rate-limiter at the service layer or a counter in Redis/DB per email. Alternatively, enforce at the nginx/proxy layer.
- **Effort:** 2–4 hours.

---

**H-5 — `PropertyService.delete()` soft-deletes but `ReservationService.create()` doesn't check `activa`**

- **Problem:** `PropertyService.delete()` sets `activa=false`. However, `ReservationService.create()` calls `lockPropertyOrThrow(propertyId)` which finds the property (it still exists), never checking if it's active. New reservations can be created against a deactivated property.
- **Impact:** Deactivated properties can still accept bookings — violates the core business rule.
- **Location:** `reservation/service/ReservationService.java:99` / `property/repository/PropertyRepository.java`
- **Fix:** Add an `activa = true` filter to `findByIdForUpdate`, or check `property.getActiva()` after the lock and throw 409.
- **Effort:** 10 minutes.

---

**H-6 — `PropertyService.findAll()` returns deactivated (soft-deleted) properties**

- **Problem:** `propertyRepository.findAll()` returns all rows regardless of `activa`. Deactivated properties show up in listings.
- **Impact:** UI shows "deleted" properties; clients can attempt to create reservations for them (compounds H-5).
- **Location:** `property/service/PropertyService.java:26`
- **Fix:** Add `findByActivaTrue()` to `PropertyRepository` and use it in `findAll()`.
- **Effort:** 10 minutes.

---

**H-7 — `UserController.deleteUser()` allows admin self-deletion**

- **Problem:** An ADMINISTRADOR can soft-delete themselves (`activo=false`). If they are the only admin, the system has no remaining admin account. `AdminUserInitializer` would need a container restart to recreate it.
- **Impact:** Admin lockout.
- **Location:** `auth/service/UserService.java:57`
- **Fix:** Retrieve the authenticated user's email from `SecurityContextHolder`; throw `CONFLICT` if it matches the target user's username.
- **Effort:** 10 minutes.

---

**H-8 — Dead `booking` package — financial math uses `double`, not `BigDecimal`**

- **Problem:** `booking/service/InvoiceCalculator.java` uses `double` for all monetary calculations (subtotal, tax, discount). This is a known anti-pattern for financial software. The package has no controller, no repository, no JPA entity — it is entirely disconnected from the production reservation system. It has 31 passing unit tests that give false confidence.
- **Impact:** Tests "pass" but validate logic that isn't used in production. The real `FacturaService` uses `BigDecimal` correctly. Risk: if someone wires `InvoiceCalculator` into production, financial rounding errors follow immediately.
- **Location:** `booking/` — entire package.
- **Fix:** Either delete the package entirely (remove 3 files and 31 tests), or replace `double` with `BigDecimal` and wire `InvoiceCalculator` into `FacturaService.create()` to replace duplicated tax/discount logic.
- **Effort:** Delete: 5 minutes. Integrate: 2 hours.

---

### MEDIUM

---

**M-1 — `NotaCreditoService.delete()` has no state guard**

- **Problem:** A nota de crédito can be deleted at any point with no restrictions. If the nota was already reflected in external accounting, its deletion creates an audit gap.
- **Location:** `notacredito/service/NotaCreditoService.java:93`
- **Fix:** Add a business rule — e.g., notas linked to PAID facturas cannot be deleted, or add a `cancelada` state.
- **Effort:** 20 minutes.

---

**M-2 — Missing `@Email` validation on `clienteEmail` in both reservation DTOs**

- **Problem:** `CreateReservationRequest` and `UpdateReservationRequest` have `@Size(max=150)` on `clienteEmail` but no `@Email` constraint. A malformed email passes server-side validation and is stored in the DB.
- **Location:** `reservation/dto/CreateReservationRequest.java:27`, `UpdateReservationRequest.java:28`
- **Fix:** Add `@Email(message = "El formato del email del cliente no es válido")` to both.
- **Effort:** 2 minutes.

---

**M-3 — Inconsistent pagination: some GET-all endpoints return `List<T>` instead of `Page<T>`**

- **Problem:** `AnticipoService`, `DevolucionService`, `PenalidadService`, and `NotaCreditoService` all do `findAll().stream().map(...)toList()` — returning the **full table** with no pagination. In contrast, `FacturaService`, `ReservationService`, and `UserService` properly paginate.
- **Impact:** As data grows, these endpoints become slow and memory-intensive.
- **Location:** `anticipo/service/AnticipoService.java:37`, `devolucion/service/DevolucionService.java:46`, `penalidad/service/PenalidadService.java:39`, `notacredito/service/NotaCreditoService.java:40`
- **Fix:** Adopt the same `PageRequest` pattern used in `FacturaService.findAll()`.
- **Effort:** 2 hours.

---

**M-4 — JWT token has no `iss` (issuer) or `aud` (audience) claims**

- **Problem:** `JwtService.generateToken()` sets only `subject` and `rol` claim. Without `iss` and `aud`, any JWT signed with the same secret from any other service would be accepted by this backend.
- **Location:** `auth/jwt/JwtService.java:24`
- **Fix:** Add `.issuer("novafacts-backend").audience().add("novafacts-api").and()` to the builder, and validate them in `isTokenValid()`.
- **Effort:** 30 minutes.

---

**M-5 — No refresh token endpoint**

- **Problem:** Tokens expire after 24 hours with no refresh mechanism. Users must fully re-authenticate after expiry. There is no `POST /api/auth/refresh` endpoint.
- **Impact:** Poor UX for long sessions; forces re-login on expiry.
- **Fix:** Implement a refresh token flow (short-lived access token + longer-lived refresh token stored server-side).
- **Effort:** 3–4 hours.

---

**M-6 — `propiedad.nombre` has no DB-level UNIQUE constraint**

- **Problem:** V3 migration explicitly dropped the unique constraint. Uniqueness is now enforced only via `propertyRepository.existsByNameIgnoreCase()` in `PropertyService` — a TOCTOU race condition. Two concurrent `POST /api/propiedades` requests with the same name would both pass the check and both INSERT.
- **Location:** `V3__reference_data_y_correccion_propiedad.sql:13`, `property/service/PropertyService.java:40`
- **Fix:** Add a Flyway migration: `CREATE UNIQUE INDEX idx_propiedad_nombre_ci ON propiedad (LOWER(nombre));`
- **Effort:** 15 minutes.

---

**M-7 — `ReservationController.getById()` is the only endpoint missing `ResponseEntity` wrapping**

- **Problem:** `getById()` at line 32 returns `ReservationResponse` directly (implicit 200), while every other controller method returns `ResponseEntity<T>`. Inconsistent pattern.
- **Location:** `reservation/controller/ReservationController.java:32`
- **Fix:** `return ResponseEntity.ok(reservationService.findById(id));`
- **Effort:** 1 line.

---

**M-8 — No `.dockerignore` file**

- **Problem:** No `.dockerignore` exists. Every `docker build` sends the full working tree (including `target/`, `.git/`, source files) to the Docker daemon. Slows builds and inflates context size.
- **Fix:** Create `.dockerignore` with at minimum: `.git`, `.mvn`, `src/`, `*.md`.
- **Effort:** 5 minutes.

---

**M-9 — CORS `allowedOrigins` defaults to `http://localhost:5173` in production Docker container**

- **Problem:** `docker-compose.yml` does not pass `CORS_ALLOWED_ORIGINS`. The container's `application.properties` defaults to `http://localhost:5173`. In production, CORS requests from the real frontend domain are blocked.
- **Location:** `docker-compose.yml` — missing env var
- **Fix:** Add `- CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:-http://localhost:5173}` to `docker-compose.yml` and require it to be set in `.env` for production.
- **Effort:** 1 line.

---

**M-10 — `PenalidadService.create()` doesn't validate that reservation is CANCELLED**

- **Problem:** A penalty can be created for any reservation regardless of status. Creating a `Penalidad` against a CONFIRMED (active) or COMPLETED reservation violates business rules — penalties are for cancellations.
- **Location:** `penalidad/service/PenalidadService.java:55`
- **Fix:** Add `if (reserva.getStatus() != ReservationStatus.CANCELLED) throw 409 "Solo se puede crear una penalidad sobre una reserva cancelada"`.
- **Effort:** 5 minutes.

---

### LOW

---

**L-1 — `pom.xml` has empty metadata fields**

`<name/>`, `<description/>`, `<url/>`, `<licenses>`, `<developers>`, `<scm>` are all empty. Produces warnings in Maven output.

**Location:** `pom.xml:13-28`

---

**L-2 — `UserDetailsServiceImpl.loadUserByUsername()` logs the username in exception message**

`"Usuario no encontrado: " + username` appears in Spring Security logs. In production, ensure log level filters this or replace with a generic message.

**Location:** `auth/service/UserDetailsServiceImpl.java:28`

---

**L-3 — Empty test packages (`invoice/`, `payment/`)**

`src/test/java/com/novafacts/backend/invoice/` and `.../payment/` exist but are empty — leftover from an older sprint structure. Delete them.

---

**L-4 — `AnticipoRequest` does not validate that `fechaPago` is not in the future**

An advance payment can be dated months in the future without rejection.

**Location:** `anticipo/dto/AnticipoRequest.java:19`

---

**L-5 — CORS allowed methods do not include `PATCH`**

If any future endpoint uses `PATCH`, browser preflight will fail silently. Adding it now is zero cost.

**Location:** `config/SecurityConfig.java:81`

---

## 4. Missing Features

| Feature | Status |
|---------|--------|
| JWT refresh token (`POST /api/auth/refresh`) | Missing |
| Reservation status-transition validation | Missing |
| Deactivated property exclusion from `GET /api/propiedades` | Missing |
| New-reservation guard on deactivated properties | Missing |
| `NotaCredito` lifecycle / state field | Missing |
| `PUT /api/anticipos/{id}` (update) | Missing |
| `PUT /api/penalidades/{id}` (update) | Missing |
| Account change-password endpoint | Missing |
| Audit log (`log_transaccion` entity from schema) | Missing |
| `GET /api/usuarios/{id}` (single-user retrieval) | Missing |
| `application-prod.properties` | Missing |
| API versioning (e.g., `/api/v1/`) | Missing |
| OpenAPI / Swagger documentation | Missing |

---

## 5. Refactoring Opportunities

**R-1 — Extract `AuthenticatedUserResolver` helper**

Five services independently do the same three-line pattern to resolve the calling user from `SecurityContextHolder`. Extract to a `@Component AuthenticatedUserResolver.getOrThrow()`.

**R-2 — Delete or integrate the `booking` package**

`booking/model/Booking.java` + `BookingValidator` + `InvoiceCalculator` have zero production integration. Either delete (3 files, 31 tests) or convert `InvoiceCalculator` to `BigDecimal` and wire it into `FacturaService.create()` to replace its duplicated tax/discount logic.

**R-3 — Reduce `ReservationService` constructor arity**

`ReservationService` takes 10 constructor arguments. Four (`anticipoRepository`, `penalidadRepository`, `facturaRepository`, `devolucionRepository`) are used only in `delete()` to check for financial children. Extract to a `ReservationDeletionGuard` `@Component`.

**R-4 — Consolidate entity → response mappers**

Every service has a private `toResponse(Entity)` method inlined within itself. A dedicated mapper class would make the mapping testable in isolation and reduce per-service surface area.

**R-5 — Normalize enum serialization across the API**

`AnticipoService.toResponse()` serializes `estado` as lowercase (`a.getEstado().name().toLowerCase()`), while `ReservationResponse` and `FacturaResponse` serialize their enums as uppercase (`"CONFIRMED"`, `"PENDING"`). The API is inconsistent. Pick one convention and enforce it with a Jackson `@JsonProperty` or a `@JsonSerialize` annotation.

---

## 6. Release Readiness

**Verdict: Not ready for production.**

The six items below represent active harm to data integrity, security, or availability and must be resolved before any real data enters the system:

1. **C-1** — Deleting a PENDIENTE devolucion permanently corrupts the associated anticipo.
2. **C-2** — BCrypt password hashes and all PII in queries are logged at TRACE to stdout in production.
3. **C-3** — Dockerfile EXPOSE 8081 ≠ app port 8082; silently breaks non-compose deployments.
4. **C-4** — The only compose file runs the demo seeder, creating fake accounts with known passwords in production.
5. **H-3** — `findByClienteEmail` returning `Optional<Reservation>` throws a 500 when any client makes a second booking.
6. **H-5** — Deactivated properties accept new reservations.

Everything else is important but could ship with a tracked ticket. The six items above are blockers.

---

## 7. Final Roadmap (Shortest Path to Production)

| # | Priority | Task | Effort |
|---|----------|------|--------|
| 1 | CRITICAL | Fix `DevolucionService.delete()` — revert anticipo to REGISTRADO before deleting devolucion | 15 min |
| 2 | CRITICAL | Move SQL logging (`show-sql`, `SQL=DEBUG`, `BasicBinder=TRACE`) to `application-dev.properties` | 5 min |
| 3 | CRITICAL | Fix `Dockerfile` `EXPOSE 8081` → `EXPOSE 8082` | 1 min |
| 4 | CRITICAL | Change `docker-compose.yml` `SPRING_PROFILES_ACTIVE=dev` to `${SPRING_PROFILES_ACTIVE:-prod}`; create `application-prod.properties` | 30 min |
| 5 | HIGH | Change `ReservationRepository.findByClienteEmail` to return `List<Reservation>`; fix seeder lookup key | 20 min |
| 6 | HIGH | Add status-transition validation to `ReservationService.update()` | 45 min |
| 7 | HIGH | `PropertyService`: filter `activa=true` in `findAll()`; check `activa` in `lockPropertyOrThrow()` | 15 min |
| 8 | HIGH | Add `AnticipoEstado.DEVUELTO` guard to `AnticipoService.delete()` with descriptive message | 5 min |
| 9 | HIGH | Guard against admin self-deletion in `UserService.deleteUser()` | 10 min |
| 10 | MEDIUM | Add `@Email` to `clienteEmail` in both reservation DTOs | 2 min |
| 11 | MEDIUM | Add Flyway migration for case-insensitive UNIQUE index on `propiedad.nombre` | 10 min |
| 12 | MEDIUM | Add `CORS_ALLOWED_ORIGINS` to `docker-compose.yml` | 5 min |
| 13 | MEDIUM | Validate reservation is CANCELLED before creating a `Penalidad` | 5 min |
| 14 | MEDIUM | Add `NotaCreditoService.delete()` state guard | 20 min |
| 15 | MEDIUM | Create `.dockerignore` | 5 min |
| 16 | MEDIUM | Fix `ReservationController.getById()` to return `ResponseEntity<ReservationResponse>` | 1 min |
| 17 | MEDIUM | Add JWT `iss`/`aud` claims in `JwtService` and validate in `isTokenValid()` | 30 min |
| 18 | MEDIUM | Add `GET /api/usuarios/{id}` and `PUT /api/usuarios/{id}/password` endpoints | 45 min |
| 19 | MEDIUM | Paginate the four unbounded `findAll()` services (Anticipo, Devolucion, Penalidad, NotaCredito) | 2 hrs |
| 20 | MEDIUM | Add rate limiting on `POST /api/auth/login` | 2–4 hrs |
| 21 | LOWER | Extract `AuthenticatedUserResolver` to eliminate 5× duplicated SecurityContext pattern | 30 min |
| 22 | LOWER | Normalize enum serialization (all lowercase or all uppercase) across all API responses | 45 min |
| 23 | LOWER | Delete orphaned `booking` package; delete empty `invoice/` and `payment/` test dirs | 30 min |
| 24 | LOWER | Add `springdoc-openapi` for automatic API documentation | 1 hr |
| 25 | LOWER | Add refresh token endpoint | 3–5 hrs |
| 26 | LATER | Implement audit log (`log_transaccion` entity from domain schema) | 4–8 hrs |

**Estimated effort to clear all CRITICAL + HIGH blockers: ~3 hours.**
Full production polish (pagination, JWT hardening, rate limiting, refresh tokens, OpenAPI): ~15–20 additional hours.
