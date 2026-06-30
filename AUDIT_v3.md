# NovaFacts Backend — Independent Full-Codebase Audit (Third Pass)

*Date: 2026-06-30 | Baseline: working-tree as of `b9b4565` + all sprint-6 changes applied*
*Auditor scope: completely independent pass — all previous audit reports treated as non-existent.*
*Files reviewed: 98 Java source files, 11 Flyway migrations, `application.properties`, `application-dev.properties`, `application-test.properties`, `pom.xml`, `Dockerfile`, `docker-compose.yml`, `.dockerignore`, all test classes.*

---

## 1. Findings

---

### CRITICAL

---

#### C-1 — Missing indexes on FK columns in financial tables cause sequential scans on every relational lookup

| Field | Detail |
|-------|--------|
| **Severity** | Critical |
| **Files** | `db/migration/V7__fix_bigint_fk_types.sql`, `anticipo/repository/AnticipoRepository.java`, `penalidad/repository/PenalidadRepository.java`, `devolucion/repository/DevolucionRepository.java`, `notacredito/repository/NotaCreditoRepository.java` |
| **Lines** | V7 entire file; repository `existsByReservaId` / `findByReservaId` methods |
| **Root cause** | PostgreSQL creates an index automatically only for PRIMARY KEY and UNIQUE constraints. V7 adds FK constraints on `anticipo.reserva_id`, `anticipo.usuario_id`, `penalidad.reserva_id`, `penalidad.usuario_id`, `devolucion.reserva_id`, `devolucion.anticipo_id`, `devolucion.usuario_id`, `nota_credito.factura_id`, `nota_credito.usuario_id` — none of these columns have an explicit `CREATE INDEX`. |
| **Why it is a problem** | Every `existsByReservaId`, `findByReservaId`, `findByFacturaId` call does a full sequential scan of the respective table. `ReservationService.delete()` fires four such calls in sequence. `PenalidadService.findByReservaId()`, `AnticipoService.findByReservaId()`, `DevolucionService.findByReservaId()`, `NotaCreditoService.findByFacturaId()` all hit unindexed FK columns. |
| **Production impact** | Response time for financial listing and deletion endpoints degrades linearly with row count. At 100,000 anticipo rows, `existsByReservaId` scans the entire table on each call. Reservation deletion (`ReservationService.delete()`) performs four sequential scans simultaneously. |
| **Probability** | Certain — already present in every environment using this schema. |
| **Recommended fix** | Add a Flyway migration (e.g., `V12__fk_indexes.sql`): `CREATE INDEX idx_anticipo_reserva_id ON anticipo (reserva_id); CREATE INDEX idx_anticipo_usuario_id ON anticipo (usuario_id); CREATE INDEX idx_penalidad_reserva_id ON penalidad (reserva_id); CREATE INDEX idx_penalidad_usuario_id ON penalidad (usuario_id); CREATE INDEX idx_devolucion_reserva_id ON devolucion (reserva_id); CREATE INDEX idx_devolucion_anticipo_id ON devolucion (anticipo_id); CREATE INDEX idx_devolucion_usuario_id ON devolucion (usuario_id); CREATE INDEX idx_nota_credito_factura_id ON nota_credito (factura_id); CREATE INDEX idx_nota_credito_usuario_id ON nota_credito (usuario_id);` Note: `factura.reserva_id` already has an implicit index from the `UNIQUE` constraint (V7 line: `reserva_id BIGINT NOT NULL UNIQUE`). |
| **Breaking?** | Non-breaking. Indexes are transparent to queries. |
| **Effort** | 15 minutes (migration only). |
| **Confidence** | 100% — PostgreSQL does not auto-index FK columns; verified against V7 DDL. |

---

### HIGH

---

#### H-1 — `ReservationService.update()` allows moving a reservation to a different property without validating or warning about existing financial records

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Files** | `reservation/service/ReservationService.java:143-175`, `reservation/dto/UpdateReservationRequest.java` |
| **Lines** | `update()` method; `setPropertyId(request.getPropertyId())` at line ~165 |
| **Root cause** | `UpdateReservationRequest` accepts a new `propertyId`. `ReservationService.update()` validates that `politicaCancelacion` belongs to the new `propertyId` (via `validatePoliticaMatchesProperty`), but never checks whether the original `propertyId` differs from the new one and never guards against existing anticipos, facturas, or penalidades referencing the original property context. |
| **Why it is a problem** | An anticipo of 700,000 COP is registered for reservation R linked to property A. A user updates reservation R to point to property B (which has a lower nightly rate). The anticipo still exists for "property A's context" but the reservation is now for property B. When a devolucion is processed, the accounting records are inconsistent. The factura (if issued) will reference property B's reserva but the anticipo was registered in the context of property A. |
| **Production impact** | Financial record inconsistency. Auditors cannot reconcile which property the original advance payment was for. |
| **Probability** | Low in practice (a user would have to intentionally change the property), but the system offers no warning or protection. |
| **Recommended fix** | In `update()`, after loading the reservation, check if `request.getPropertyId()` differs from `reservation.getPropertyId()`. If it does and the reservation has any financial history (`anticipoRepository.existsByReservaId`, `facturaRepository.existsByReservaId`, `penalidadRepository.existsByReservaId`), throw `CONFLICT` with a descriptive message: `"No se puede cambiar la propiedad de una reserva con historial financiero asociado"`. |
| **Breaking?** | Non-breaking for clean reservations; breaks the undocumented ability to move reservations with history (which is a bug, not a feature). |
| **Effort** | 20 minutes. |
| **Confidence** | 95% — verified path: `update()` calls `setPropertyId(request.getPropertyId())` with no prior financial-history check. |

---

#### H-2 — `TemporadaService.eliminar()` provides no useful error when the temporada is in use by reservations

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Files** | `temporada/service/TemporadaService.java:42-44`, `common/GlobalExceptionHandler.java:23-25` |
| **Lines** | `eliminar()` calls `temporadaRepository.delete()` directly; FK violation produces `DataIntegrityViolationException` → `GlobalExceptionHandler` returns `{"error": "Conflicto de datos"}`. |
| **Root cause** | No pre-delete check for child FK references. The `reserva.temporada_id` FK constraint in PostgreSQL blocks the delete at the DB level, but the exception is caught generically. |
| **Why it is a problem** | An administrator trying to delete a booking season receives `HTTP 409 {"error": "Conflicto de datos"}` with no indication that existing reservations are blocking deletion. There is no way to know which reservations are blocking without querying the DB directly. |
| **Production impact** | Support tickets and confusion. Administrators cannot self-serve to identify blocking records. |
| **Probability** | Certain — any production database with confirmed reservations will hit this on any delete attempt. |
| **Recommended fix** | Add `existsByTemporadaId(Integer temporadaId)` to `ReservationRepository`, then in `eliminar()`: `if (reservationRepository.existsByTemporadaId(id)) { throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar la temporada: existen reservas que la referencian"); }` |
| **Breaking?** | Non-breaking. |
| **Effort** | 20 minutes. |
| **Confidence** | 100%. |

---

#### H-3 — `GlobalExceptionHandler.handleValidation()` returns only the first field error

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Files** | `common/GlobalExceptionHandler.java:28-34` |
| **Lines** | 31: `.findFirst().orElse(...)` |
| **Root cause** | `MethodArgumentNotValidException` can contain multiple `FieldError` objects (one per invalid field). The handler calls `findFirst()` and discards the rest. |
| **Why it is a problem** | A request with five invalid fields returns one error. The user corrects it and resubmits, getting the next error. A form with 5 invalid fields requires 5 round trips. |
| **Production impact** | Significantly degraded UX for form-heavy workflows (creating a reservation, creating a user, creating a factura). Frontend clients cannot highlight all invalid fields simultaneously. |
| **Probability** | Certain — triggered on every multi-field validation failure. |
| **Recommended fix** | Replace `findFirst()` with a `toList()` and return all errors: `List<String> errors = ex.getBindingResult().getFieldErrors().stream().map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).toList(); return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", String.join("; ", errors)));` Or change the response type to `Map<String, Object>` with an `errors` array. The change requires a DTO response format decision and frontend coordination. |
| **Breaking?** | Breaking — changes the response body shape from `{"error": "..."}` to a multi-error structure. Coordinate with frontend. |
| **Effort** | 30 minutes (service) + frontend update. |
| **Confidence** | 100%. |

---

#### H-4 — `PropertyService.findAll()` is unbounded (no pagination) — entire table returned on every call

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Files** | `property/service/PropertyService.java:18-21`, `property/controller/PropertyController.java:20` |
| **Lines** | `findAll()`: `propertyRepository.findByActivaTrue().stream().map(...).toList()` |
| **Root cause** | `findByActivaTrue()` returns `List<Property>` with no `Pageable`. The controller returns this list directly with no `page`/`size` parameters. |
| **Why it is a problem** | A property management company with 500 properties returns all 500 in one call. With EAGER loading, each property's associations are hydrated. If `PropertyResponse` is included in reservation responses (it is not, the response only carries `propertyId`), the fan-out would be severe. As-is, returning 500 records in one uncompressed JSON response is still wasteful. |
| **Production impact** | Increased memory usage per request. Longer response times as property count grows. No way for frontend to paginate the property list. |
| **Probability** | Becomes a problem when active property count exceeds ~200. At class-project scale it's invisible. |
| **Recommended fix** | Follow the pattern established for reservations, facturas, and users: add `page`/`size` parameters to `PropertyController.getAll()`, change `PropertyService.findAll()` to accept `Pageable`, return `PageResponse<PropertyResponse>`. Change `PropertyRepository` to `Page<Property> findByActivaTrue(Pageable pageable)`. |
| **Breaking?** | Breaking — changes response shape from `List<T>` to `PageResponse<T>`. Coordinate with frontend. |
| **Effort** | 30 minutes. |
| **Confidence** | 100%. |

---

### MEDIUM

---

#### M-1 — `PropertyController.getAll()` and `getById()` return bare types instead of `ResponseEntity`

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `property/controller/PropertyController.java:20,25` |
| **Lines** | `public List<PropertyResponse> getAll()` (line ~20), `public PropertyResponse getById(@PathVariable Long id)` (line ~25) |
| **Root cause** | Both methods return the response directly rather than via `ResponseEntity<T>`. Every other controller (`ReservationController`, `FacturaController`, `AnticipoController`, etc.) consistently wraps all responses in `ResponseEntity`. |
| **Why it is a problem** | No ability to set custom HTTP status codes at the controller layer. If Spring MVC generates a 200 for a non-`ResponseEntity` return, this is fine for GET — but it introduces inconsistency that makes the API contract harder to reason about. `getAll()` also bypasses the pattern for adding response headers (e.g., `X-Total-Count`). Swagger/OpenAPI codegen treats bare returns differently. |
| **Production impact** | Minor inconsistency. Not a runtime bug. |
| **Probability** | Certain — both methods are already deployed. |
| **Recommended fix** | `public ResponseEntity<List<PropertyResponse>> getAll() { return ResponseEntity.ok(propertyService.findAll()); }` and `public ResponseEntity<PropertyResponse> getById(...) { return ResponseEntity.ok(propertyService.findById(id)); }` |
| **Breaking?** | Non-breaking — HTTP status codes and body are unchanged. |
| **Effort** | 5 minutes. |
| **Confidence** | 100%. |

---

#### M-2 — `ReservationController` exposes `/api/reservations` (English) while all other endpoints are in Spanish

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `reservation/controller/ReservationController.java:14` |
| **Lines** | `@RequestMapping("/api/reservations")` |
| **Root cause** | All other controllers use Spanish path segments: `/api/propiedades`, `/api/facturas`, `/api/anticipos`, `/api/devoluciones`, `/api/penalidades`, `/api/notas-credito`, `/api/usuarios`, `/api/canales`, `/api/temporadas`, `/api/politicas`. The reservation controller uses the English path. |
| **Why it is a problem** | API path inconsistency. Any OpenAPI documentation would show a mixed-language URL scheme. Frontend developers must remember one English URL among ten Spanish ones. Any future API versioning or gateway routing must account for this outlier. |
| **Production impact** | Not a runtime issue. A maintenance and API design issue. The frontend already uses `/api/reservations` so changing it is a breaking change. |
| **Probability** | Certain — already deployed. |
| **Recommended fix** | Change `@RequestMapping("/api/reservations")` to `@RequestMapping("/api/reservas")` and update the frontend to use the new path. |
| **Breaking?** | Breaking — requires coordinated frontend update. |
| **Effort** | 10 minutes (backend) + frontend coordination. |
| **Confidence** | 100%. |

---

#### M-3 — `CORS_ALLOWED_ORIGINS` with spaces around commas produces malformed origin entries

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `config/SecurityConfig.java:28-30`, `application.properties:21`, `docker-compose.yml:29` |
| **Lines** | `SecurityConfig`: `@Value("${cors.allowed-origins:http://localhost:5173}") private List<String> allowedOrigins;` |
| **Root cause** | Spring `@Value` binding to `List<String>` uses a comma as the delimiter and does NOT trim whitespace from individual elements. If an operator sets `CORS_ALLOWED_ORIGINS=https://app.example.com, https://admin.example.com` (space after comma), the second element becomes `" https://admin.example.com"` (with leading space). The browser sends `Origin: https://admin.example.com` (no space), which does not match `" https://admin.example.com"`. CORS preflight fails. |
| **Why it is a problem** | Silent CORS failures. The frontend cannot reach the API from the second origin. No error is logged because CORS rejection is a security feature, not an exception. The operator sees no indication that the origin list was parsed incorrectly. |
| **Production impact** | Production CORS failure for any origin specified after the first when the env var has spaces. |
| **Probability** | Medium — any operator who formats the env var with spaces (common human habit) will hit this. |
| **Recommended fix** | Trim each origin after splitting: replace the `@Value`-bound `List<String>` with a `String` and manually split and trim: `@Value("${cors.allowed-origins:http://localhost:5173}") private String allowedOriginsRaw;` Then in `corsConfigurationSource()`: `List<String> origins = Arrays.stream(allowedOriginsRaw.split(",")).map(String::strip).toList(); configuration.setAllowedOrigins(origins);` |
| **Breaking?** | Non-breaking — silently fixes malformed origin lists. |
| **Effort** | 15 minutes. |
| **Confidence** | 95% — Spring's comma-split for `@Value` List binding is confirmed to not trim whitespace. |

---

#### M-4 — `DevelopmentDataSeeder` creates a `Devolucion` with `monto = 0.00`, bypassing DTO-level business rule

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `config/DevelopmentDataSeeder.java:~262`, `devolucion/dto/DevolucionRequest.java:15` |
| **Lines** | `seedRefunds()` → `createDevolucionIfAbsent(reservations.get("r4"), ..., new BigDecimal("0.00"), ...)` |
| **Root cause** | The seeder bypasses the service layer and calls `devolucionRepository.save()` directly via `buildDevolucion()`. `DevolucionRequest` enforces `@DecimalMin("0.01")` at the API level, but the repository has no corresponding constraint. The DB column `devolucion.monto DECIMAL(12,2) NOT NULL` has no `CHECK` constraint. |
| **Why it is a problem** | A zero-monto devolucion can exist in the database that violates the DTO-level business rule. Any reporting, reconciliation, or financial audit that relies on `devolucion.monto > 0` for all records will encounter this record. The seeder is the only way to inject it, but it means the dev environment does not accurately represent the production data constraints. |
| **Production impact** | Low directly — seeder only runs in dev. But it demonstrates a gap: the DB lacks a `CHECK (monto > 0)` constraint that would prevent this even in production via direct SQL inserts. |
| **Probability** | Certain in dev environments — the seeder always creates this record. |
| **Recommended fix** | Two changes: (1) In the seeder, use `new BigDecimal("1.00")` as the minimum rejected-devolucion monto (semantically, a rejected refund doesn't mean zero amount — it means the request was denied); (2) Add a DB-level check constraint: `ALTER TABLE devolucion ADD CONSTRAINT chk_devolucion_monto_positivo CHECK (monto > 0);` in a new migration. |
| **Breaking?** | Non-breaking. |
| **Effort** | 15 minutes. |
| **Confidence** | 100%. |

---

#### M-5 — `DevelopmentDataSeeder.seedUsers()` hardcodes role IDs 1–4 with `orElseThrow()` — silent startup crash

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `config/DevelopmentDataSeeder.java:~155-162` |
| **Lines** | `rolRepository.findById(1).orElseThrow()`, `findById(2).orElseThrow()`, `findById(3).orElseThrow()`, `findById(4).orElseThrow()` |
| **Root cause** | The seeder assumes role IDs 1, 2, 3, 4 in the order they were inserted by V2. `GENERATED BY DEFAULT AS IDENTITY` in PostgreSQL preserves insert order when starting from 1, but the sequence can diverge if the DB was partially reset, migrated from a backup, or if roles were inserted in a different order. |
| **Why it is a problem** | `orElseThrow()` throws `NoSuchElementException` at startup if any of the four roles is not found with the expected ID. Since `DevelopmentDataSeeder.run()` is `@Transactional`, this rolls back all seeding and the application continues with an empty dataset and no log message explaining why data is missing. |
| **Production impact** | Dev-only (profile guard). Startup silently leaves the dev DB empty, causing 404/500 errors on all financial endpoints. |
| **Probability** | Low on fresh installs; medium when the dev DB was manually modified or partially truncated. |
| **Recommended fix** | Use `rolRepository.findByNombreIgnoreCase("Administrador")` etc. instead of hardcoded IDs. Add `Optional.findByNombreIgnoreCase(String nombre)` to `RolRepository`. |
| **Breaking?** | Non-breaking. |
| **Effort** | 20 minutes. |
| **Confidence** | 100%. |

---

#### M-6 — `Reservation.clienteEmail` is nullable with no `@NotNull` — reservations without guest email allowed

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `reservation/entity/Reservation.java:37`, `reservation/dto/CreateReservationRequest.java:26-27`, `db/migration/V4__reserva_rebuild_y_politica.sql:~32` |
| **Lines** | Entity: `@Column(name = "cliente_email", length = 150)` (no `nullable = false`). DTO: `@Email @Size(max=150)` with no `@NotNull`. Migration V4: `cliente_email VARCHAR(150)` (nullable). |
| **Root cause** | Intentional design decision to allow reservations without client email. However, `ReservationRepository.findByClienteEmail(String)` and `findByClienteEmailAndCheckInDate(String, LocalDate)` (used in `DevelopmentDataSeeder`) both have email as a required search key. A null email makes these lookups unreliable. |
| **Why it is a problem** | A reservation without an email cannot be: (1) found via the email-based seeder lookup (causing duplicate seed records if `findByClienteEmailAndCheckInDate` returns empty instead of the existing record), (2) used for any guest communication feature. More critically, the `@Email` annotation validates format only when non-null — `null` passes `@Email` validation. A client can submit a reservation with `"clienteEmail": null` and it is accepted. |
| **Production impact** | Operational: customer service cannot contact guests without email. Data quality: email-based reporting is unreliable. |
| **Probability** | Medium — any client that omits the `clienteEmail` field. |
| **Recommended fix** | Decide whether email is mandatory. If yes: add `@NotNull(message="El email del cliente es obligatorio")` to `CreateReservationRequest.clienteEmail` and `UpdateReservationRequest.clienteEmail`, and set `@Column(name="cliente_email", nullable=false)` in the entity + a migration `ALTER TABLE reserva ALTER COLUMN cliente_email SET NOT NULL;`. If no: document and accept the current behavior, but fix the seeder's `findByClienteEmailAndCheckInDate` to handle null safely. |
| **Breaking?** | Making it non-null is a breaking API change (existing null values fail schema migration). |
| **Effort** | 30 minutes. |
| **Confidence** | 100%. |

---

#### M-7 — `TemporadaService` allows overlapping season date ranges — no business constraint enforced

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `temporada/service/TemporadaService.java:25-29`, `temporada/repository/TemporadaRepository.java` |
| **Lines** | `crear()` and `actualizar()` only call `validarFechas()` (checks `inicio < fin`) with no overlap check. |
| **Root cause** | No query checks whether the new season date range intersects with an existing one. `TemporadaRepository` has no overlap query. |
| **Why it is a problem** | Two seasons can overlap in time. When a reservation is created, `temporadaId` is caller-supplied — the system does not validate that the reservation's check-in falls within the referenced season. Overlapping seasons make financial calculations and seasonal pricing ambiguous. |
| **Production impact** | Data quality issue. Reports grouped by season will contain reservations from ambiguous overlapping periods. |
| **Probability** | Medium — a careless admin can create overlapping seasons. |
| **Recommended fix** | Add an overlap query to `TemporadaRepository`: `@Query("SELECT COUNT(t) > 0 FROM Temporada t WHERE t.id <> :excludeId AND t.fechaInicio < :fin AND t.fechaFin > :inicio") boolean existsOverlap(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin, @Param("excludeId") Integer excludeId);` Check this in `crear()` and `actualizar()` before saving. |
| **Breaking?** | Non-breaking. |
| **Effort** | 30 minutes. |
| **Confidence** | 95%. |

---

#### M-8 — Test suite uses H2 in-memory database — behavioral differences with PostgreSQL are never validated

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `src/test/resources/application-test.properties:1-11` |
| **Lines** | `spring.datasource.driver-class-name=org.h2.Driver`, `spring.jpa.hibernate.ddl-auto=create-drop`, `spring.flyway.enabled=false` |
| **Root cause** | Tests use H2 with Hibernate DDL auto-generation instead of Flyway + PostgreSQL. This means: (1) Flyway migrations are never executed in CI; (2) PostgreSQL-specific behaviors (pessimistic locks under high concurrency, `SELECT FOR UPDATE` semantics, `LOWER()` function behavior, identity column behavior) are tested against H2's different implementation. |
| **Why it is a problem** | A migration syntax error (e.g., invalid `ALTER TABLE` syntax) will not be caught by the test suite. A migration that passes but produces incorrect data will not be caught. PostgreSQL-specific query behavior (e.g., H2 may silently succeed where PostgreSQL fails on `GENERATED BY DEFAULT AS IDENTITY` with conflicts) is untested. The `idx_propiedad_nombre_ci` (V11) uses `LOWER(nombre)` which is a function-based expression index — H2's auto-DDL will not create this, so the duplicate-name uniqueness enforced by the index is not tested. |
| **Production impact** | A migration could fail on the production PostgreSQL instance and succeed in all CI tests, causing a deployment failure. |
| **Probability** | Low for simple migrations; higher for DDL with PostgreSQL-specific syntax. |
| **Recommended fix** | Use Testcontainers with a real PostgreSQL image for integration tests. Replace H2 with `org.testcontainers:postgresql` and enable Flyway in tests. This is the industry standard for Spring Boot + PostgreSQL projects. |
| **Breaking?** | Non-breaking to existing code. Tests will run against real PostgreSQL behavior. |
| **Effort** | 2–3 hours. |
| **Confidence** | 100%. |

---

#### M-9 — `CreateReservationRequest.montoTotal` accepts `0.00` — zero-value reservation is allowed

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `reservation/dto/CreateReservationRequest.java:32-33`, `reservation/dto/UpdateReservationRequest.java:32-33` |
| **Lines** | `@DecimalMin(value = "0.00", inclusive = true, message = "El monto total no puede ser negativo")` |
| **Root cause** | `inclusive = true` allows exactly 0.00. A reservation with `montoTotal=0.00` is accepted by both validation and service layers. |
| **Why it is a problem** | In a financial management system for short-term rentals, a zero-value reservation is almost certainly a data entry error. It will silently create a factura with `subtotal=0` and `total=0`. Financial reporting will include these zero-value records, skewing averages. |
| **Production impact** | Data quality and financial reporting accuracy. |
| **Probability** | Low — requires intentional or accidental submission of zero. |
| **Recommended fix** | Change to `@DecimalMin(value = "0.01", message = "El monto total debe ser mayor a cero")` in both DTOs. |
| **Breaking?** | Non-breaking for valid business cases. |
| **Effort** | 2 minutes. |
| **Confidence** | 100%. |

---

#### M-10 — `FacturaService.emitir()` naming confusion: "emitir" (emit/issue) maps to `PENDING → PAID` transition

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `factura/service/FacturaService.java:107-115`, `factura/controller/FacturaController.java:40-42` |
| **Lines** | `emitir()` method: `factura.setEstado(InvoiceStatus.PAID)` |
| **Root cause** | In Spanish accounting, "emitir una factura" means "to issue an invoice" — creating it and making it active. `PAID` means the invoice has been fully settled. The method name `emitir` is therefore misleading: it suggests the invoice is being issued/finalized but actually marks it as paid (`PAID`). The correct Spanish verb for "mark as paid" would be `registrarPago()` or `marcarComoPagada()`. |
| **Why it is a problem** | Domain language confusion. A developer or accountant reading the API documentation would expect `PUT /api/facturas/{id}/emitir` to transition from draft to issued, not from issued to paid. This may cause incorrect API usage by frontend developers or external integrators. |
| **Production impact** | Low — the endpoint functions correctly; only the naming is wrong. |
| **Probability** | Certain — the naming mismatch exists in the deployed API. |
| **Recommended fix** | Rename the endpoint from `/emitir` to `/pagar` and rename the service method from `emitir()` to `registrarPago()`. Coordinate with the frontend. |
| **Breaking?** | Breaking — URL changes from `/api/facturas/{id}/emitir` to `/api/facturas/{id}/pagar`. |
| **Effort** | 20 minutes + frontend update. |
| **Confidence** | 100%. |

---

### LOW

---

#### L-1 — `BackendApplicationTests` has an empty test body providing zero test value

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `src/test/java/com/novafacts/backend/BackendApplicationTests.java:9-13` |
| **Lines** | 11: comment "Skipped: requires a running PostgreSQL instance." |
| **Root cause** | The test was never implemented after the profile-aware H2 test setup was introduced. The comment says it requires PostgreSQL, but `@ActiveProfiles("test")` with H2 would allow the context to load. |
| **Why it is a problem** | The test reports as passing but asserts nothing. It gives false confidence in the test suite. A broken `@SpringBootApplication` setup would still show 1 passing test. |
| **Production impact** | None. |
| **Recommended fix** | Either remove the test class, or convert it to a proper Spring context load test: `@SpringBootTest @ActiveProfiles("test") class BackendApplicationTests { @Test void contextLoads() {} }` — the default `contextLoads()` test is sufficient to validate that all Spring beans wire up correctly. |
| **Breaking?** | Non-breaking. |
| **Effort** | 5 minutes. |
| **Confidence** | 100%. |

---

#### L-2 — `booking` package uses `double` for monetary arithmetic (floating-point for money)

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `booking/service/InvoiceCalculator.java:4-7,10-12,19-21`, `booking/model/Booking.java:16,42` |
| **Lines** | `double pricePerNight`, `double calculateSubtotal(...)`, `double calculateTax(...)`, `double calculateDiscount(...)`, `double calculateTotal(...)` |
| **Root cause** | `double` is a floating-point type. Financial arithmetic requires exact decimal representation. `InvoiceCalculator` uses `double` throughout. |
| **Why it is a problem** | `19% of 1_000_000.0` as double: `190000.00000000003` (possible representation error). The production `FacturaService` correctly uses `BigDecimal` — but `InvoiceCalculator` is wired into 31 unit tests that give false confidence. If any future developer connects `InvoiceCalculator` to the production flow, financial rounding errors occur. |
| **Production impact** | None currently (dead code). Risk if ever connected to production. |
| **Recommended fix** | Either delete the `booking` package entirely (removes 3 classes and 31 tests), or migrate `InvoiceCalculator` to `BigDecimal` and wire it into `FacturaService.create()` to replace its duplicated tax/discount logic. |
| **Breaking?** | Non-breaking (dead code change). |
| **Effort** | Delete: 5 minutes. Integrate with BigDecimal: 2 hours. |
| **Confidence** | 100%. |

---

#### L-3 — `Temporada` entity has no DB-level UNIQUE constraint on `nombre`

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `db/migration/V3__reference_data_y_correccion_propiedad.sql:42-47`, `temporada/repository/TemporadaRepository.java:9` |
| **Lines** | V3: `CREATE TABLE temporada (nombre VARCHAR(100) NOT NULL, ...)` — no UNIQUE on `nombre`. Repository: `Optional<Temporada> findByNombre(String nombre)`. |
| **Root cause** | No UNIQUE constraint was added on `temporada.nombre`. `TemporadaRepository.findByNombre()` returns `Optional<Temporada>`, which throws `IncorrectResultSizeDataAccessException` if multiple temporadas share the same name. |
| **Why it is a problem** | Two temporadas can be created with the same name via the service (no duplicate check in `TemporadaService.crear()`). The seeder's `findOrCreateTemporada()` then throws at startup. |
| **Production impact** | If duplicate-named seasons exist, the seeder or any `findByNombre` call crashes the application. |
| **Recommended fix** | (1) Add uniqueness check in `TemporadaService.crear()` and `actualizar()`; (2) Add a Flyway migration: `ALTER TABLE temporada ADD CONSTRAINT uk_temporada_nombre UNIQUE (nombre);` |
| **Breaking?** | Non-breaking (additive constraint; fails only if duplicates already exist). |
| **Effort** | 15 minutes. |
| **Confidence** | 100%. |

---

#### L-4 — No DB-level `CHECK` constraint on `politica_cancelacion.porcentaje_reembolso` — value can exceed 100

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `db/migration/V4__reserva_rebuild_y_politica.sql:7`, `politicacancelacion/dto/PoliticaCancelacionRequest.java:19-20` |
| **Lines** | V4: `porcentaje_reembolso DECIMAL(5,2) NOT NULL` — `DECIMAL(5,2)` allows up to 999.99. No CHECK constraint. DTO enforces `@DecimalMax("100.00")`. |
| **Root cause** | DTO validation enforces 0–100 at the API layer, but the DB column allows 0–999.99. A direct SQL insert can store an invalid percentage. |
| **Why it is a problem** | `PenalidadService.create()` computes `maximoPenalidad = reserva.getMontoTotal().multiply(BigDecimal.ONE.subtract(politica.getPorcentajeReembolso().divide(BigDecimal.valueOf(100), ...)))`. If `porcentajeReembolso = 200`, the formula produces a negative `maximoPenalidad`, and all penalidades would be rejected with a confusing error. |
| **Production impact** | Low — only accessible via direct DB write. |
| **Recommended fix** | Add migration: `ALTER TABLE politica_cancelacion ADD CONSTRAINT chk_politica_porcentaje CHECK (porcentaje_reembolso BETWEEN 0 AND 100);` |
| **Breaking?** | Non-breaking (additive constraint; fails only if invalid rows exist). |
| **Effort** | 5 minutes. |
| **Confidence** | 100%. |

---

#### L-5 — `postgres:15` Docker image is not pinned to a patch version — image can silently change

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `docker-compose.yml:4` |
| **Lines** | `image: postgres:15` |
| **Root cause** | The `postgres:15` tag tracks the latest 15.x patch release. When a new 15.x version is released, `docker pull` or `docker compose pull` will silently update the image. |
| **Why it is a problem** | PostgreSQL patch releases are generally safe but can change behavior. An automatic update during deployment could cause unexpected behavior without a code change in the repository. |
| **Production impact** | Low — PostgreSQL minor releases within 15.x are stable. But reproducibility requires pinning. |
| **Recommended fix** | Pin to a specific patch version: `image: postgres:15.10-alpine` (use Alpine for smaller footprint). Alpine also reduces the image size by ~150MB. |
| **Breaking?** | Non-breaking. |
| **Effort** | 2 minutes. |
| **Confidence** | 90%. |

---

#### L-6 — `Dockerfile` uses JDK image instead of JRE — unnecessarily large production container

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `Dockerfile:1` |
| **Lines** | `FROM eclipse-temurin:21-jdk-alpine` |
| **Root cause** | `eclipse-temurin:21-jdk-alpine` includes the full Java Development Kit (compiler, tools). A production Spring Boot fat JAR only needs the JRE. |
| **Why it is a problem** | The JDK image is approximately 200–250MB larger than the equivalent JRE image (`eclipse-temurin:21-jre-alpine`). The extra development tools (javac, jar, jmod, jlink) are never used at runtime and increase the attack surface. |
| **Production impact** | Larger container image increases: pull time, registry storage costs, deployment time. Security: fewer tools in the image means fewer exploitable binaries if the container is compromised. |
| **Recommended fix** | Change `FROM eclipse-temurin:21-jdk-alpine` to `FROM eclipse-temurin:21-jre-alpine`. Alternatively, use a multi-stage build: first stage builds with JDK, second stage runs with JRE. |
| **Breaking?** | Non-breaking. |
| **Effort** | 2 minutes. |
| **Confidence** | 95%. |

---

#### L-7 — `TemporadaService` and `CanalController/Service` have no write operations — canales are immutable after seeding

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `canal/service/CanalService.java`, `canal/controller/CanalController.java` |
| **Lines** | `CanalService` only has `listar()`. `CanalController` only exposes `GET /api/canales`. |
| **Root cause** | The booking channels (Airbnb, Booking, Web propia, Teléfono, WhatsApp) are seeded by Flyway V3 and are read-only from the API. No CRUD for canales exists. |
| **Why it is a problem** | Adding a new booking channel (e.g., VRBO, Despegar) requires a new Flyway migration and a deployment. There is no admin UI or API endpoint to manage channels at runtime. |
| **Production impact** | Operational constraint — adding a channel requires a code deployment. |
| **Recommended fix** | Add POST/PUT/DELETE endpoints to `CanalController` with ADMINISTRADOR role restriction. Short-term, document the limitation. |
| **Breaking?** | Non-breaking addition. |
| **Effort** | 45 minutes. |
| **Confidence** | 100%. |

---

#### L-8 — `Temporada` entity `fechaInicio` and `fechaFin` have no DB-level `CHECK` constraint

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `db/migration/V3__reference_data_y_correccion_propiedad.sql:42-47` |
| **Lines** | `CREATE TABLE temporada (fecha_inicio DATE NOT NULL, fecha_fin DATE NOT NULL)` — no CHECK |
| **Root cause** | The service validates `fechaInicio < fechaFin` at the application layer, but the DB column has no `CHECK (fecha_fin > fecha_inicio)`. A direct SQL insert can create an invalid season. |
| **Recommended fix** | Add migration: `ALTER TABLE temporada ADD CONSTRAINT chk_temporada_fechas CHECK (fecha_fin > fecha_inicio);` |
| **Breaking?** | Non-breaking. |
| **Effort** | 5 minutes. |
| **Confidence** | 100%. |

---

#### L-9 — `pom.xml` artifact metadata fields are empty (name, description, url, licenses, developers, scm)

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Files** | `pom.xml:12-27` |
| **Lines** | `<name/>`, `<description/>`, `<url/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>`, `<scm>...</scm>` — all empty. |
| **Root cause** | Spring Initializr scaffold left unfilled. |
| **Why it is a problem** | Maven produces warnings on every build. When generating Spring Boot Actuator info endpoint or artifact metadata, these appear as empty strings. |
| **Recommended fix** | Fill in the project name, description, and optionally license/SCM. Remove the empty `<licenses>`, `<developers>`, and `<scm>` blocks if unused. |
| **Breaking?** | Non-breaking. |
| **Effort** | 5 minutes. |
| **Confidence** | 100%. |

---

### INFORMATIONAL

---

#### I-1 — `JwtAuthenticationFilter` reloads `UserDetails` from DB on every authenticated request

| Field | Detail |
|-------|--------|
| **Severity** | Informational |
| **Files** | `auth/filter/JwtAuthenticationFilter.java:47`, `auth/service/UserDetailsServiceImpl.java:22` |
| **Lines** | Filter line ~47: `UserDetails userDetails = userDetailsService.loadUserByUsername(username)` |
| **Root cause** | The filter validates the JWT signature and claims, then unconditionally loads the user record from the DB to check `activo` and build the `Authentication` object. This is 1 extra SQL query per authenticated HTTP request. |
| **Why it is a problem** | Not a bug — this is a deliberate security choice (ensures deactivated users are blocked even with valid tokens). But at 100 requests/second, this is 100 additional SELECT queries/second against the `usuario` table beyond the application's business queries. |
| **Note** | The `User` entity's `@ManyToOne(fetch = FetchType.EAGER)` on `Rol` means each user load also loads the role in a JOIN or second query. |
| **Recommendation** | Consider adding a Spring Cache (`@Cacheable`) on `UserDetailsServiceImpl.loadUserByUsername()` with a short TTL (30–60s) to reduce DB load while keeping the deactivation window bounded. |
| **Confidence** | 100%. |

---

#### I-2 — No `spring.jpa.open-in-view` explicitly set to `false` in `application-test.properties`

| Field | Detail |
|-------|--------|
| **Severity** | Informational |
| **Files** | `application.properties:8`, `application-test.properties` |
| **Lines** | `application.properties:8`: `spring.jpa.open-in-view=false` |
| **Root cause** | `spring.jpa.open-in-view=false` is correctly set in `application.properties`. Tests use `@ActiveProfiles("test")` which loads `application-test.properties`, which does not explicitly set `spring.jpa.open-in-view`. In Spring Boot, the base `application.properties` is loaded first, so `false` is inherited. This is fine, but worth documenting. |
| **Note** | No action needed — this is a clarification. |
| **Confidence** | 100%. |

---

#### I-3 — `ReservationRepository.findByClienteEmail()` returns `List<Reservation>` but is never called from a service

| Field | Detail |
|-------|--------|
| **Severity** | Informational |
| **Files** | `reservation/repository/ReservationRepository.java:13` |
| **Lines** | `List<Reservation> findByClienteEmail(String clienteEmail)` |
| **Root cause** | This method was likely added for a future "search by email" feature. No service or controller currently calls it. |
| **Note** | Dead repository method. Not harmful, but adds API surface. Remove if not needed, or expose via a `GET /api/reservations?email=...` controller endpoint. |
| **Confidence** | 100%. |

---

## 2. Scores

### Overall Architecture Score: 72 / 100

The feature-package structure is clean, consistent, and navigable. Constructor injection is used throughout. The pessimistic-locking pattern for double-booking prevention is well-implemented. Deductions: dead `booking` package with double-based monetary math (−6), URL path language inconsistency (−5), unbounded property listing (−5), no pagination on the property endpoint (−4), no season overlap validation (−4), no canal management endpoints (−4).

---

### Security Score: 74 / 100

Strengths: stateless JWT with issuer/audience validation, BCrypt with timing equalization, granular URL-based RBAC (`@EnableMethodSecurity` declared), soft-delete with disabled-account enforcement in the JWT filter, SQL TRACE logging kept out of base config. Deductions: no rate limiting on `/api/auth/login` (−12), CORS origin space-trimming bug that can silently break multi-origin configs (−6), admin password defaults visible in `docker-compose.yml` without runtime enforcement to change it (−3), JDK image in production container increasing attack surface (−5).

---

### Maintainability Score: 70 / 100

Strengths: consistent service/repository/controller pattern, clear exception handling hierarchy, `PageResponse` utility used consistently across paginated services, idiomatic Spring Data JPQL where derived names would conflict, comments document non-obvious decisions. Deductions: `PropertyController` returns bare types inconsistently (−4), `/api/reservations` English vs Spanish (−4), `GlobalExceptionHandler` returns only first validation error (−6), hardcoded role IDs in seeder (−4), empty `BackendApplicationTests` (−3), `emitir` naming confusion (−4), no FK indexes requiring manual schema knowledge (−5).

---

### Production Readiness Score: 61 / 100

Strengths: Flyway migrations with proper versioning, pessimistic locking for financial concurrency, `healthcheck` on the Postgres container, `SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}` in compose (no demo data in prod), admin password override via env var, `.dockerignore` preventing secret leaks in build context. Deductions: missing FK indexes (critical for query performance at scale, −15), H2 test environment means migrations are never CI-tested (−10), JDK image in production (−5), postgres:15 not pinned (−4), no DB CHECK constraints on monetary amounts (−5).

---

## 3. Summary by Severity

| Severity | Count | Items |
|----------|-------|-------|
| Critical | 1 | C-1 (missing FK indexes) |
| High | 4 | H-1 (property reassignment), H-2 (Temporada delete), H-3 (single validation error), H-4 (unbounded property list) |
| Medium | 10 | M-1 through M-10 |
| Low | 9 | L-1 through L-9 |
| Informational | 3 | I-1 through I-3 |

---

## 4. Top 10 Recommended Next Improvements

Ordered by impact-to-effort ratio:

| # | Finding | Impact | Effort | Why First |
|---|---------|--------|--------|-----------|
| 1 | **C-1** — Add FK indexes via Flyway V12 | Critical performance | 15 min | Highest ROI: zero risk, immediate query performance improvement |
| 2 | **M-8** — Replace H2 with Testcontainers PostgreSQL | High quality | 2–3 h | Migrations never tested in CI; a broken migration would cause prod outage |
| 3 | **H-3** — Return all validation errors in `GlobalExceptionHandler` | High UX | 30 min | Low effort, high UX impact on all form workflows |
| 4 | **M-3** — Strip whitespace from CORS origin list | High reliability | 15 min | Silent production failure for multi-origin setups |
| 5 | **H-2** — Add pre-check in `TemporadaService.eliminar()` | Medium UX | 20 min | Actionable error message vs. cryptic "Conflicto de datos" |
| 6 | **L-6** — Switch to JRE Docker image | Low security / cost | 2 min | Smaller container, smaller attack surface |
| 7 | **H-1** — Guard property reassignment on reservations with financial history | High data integrity | 20 min | Prevents financial record inconsistency |
| 8 | **M-7** — Add overlapping season validation | Medium data quality | 30 min | Prevents ambiguous financial reporting |
| 9 | **M-4** — Fix seeder zero-monto devolucion + add DB CHECK constraint | Medium data integrity | 15 min | Dev environment data should match production constraints |
| 10 | **L-3 + L-4 + L-8** — Add missing DB CHECK / UNIQUE constraints | Low data quality | 15 min | Single migration fixes three data-integrity gaps |

---

## 5. Production Readiness Verdict

**The application is NOT ready for production in its current state.**

The single blocking reason is **C-1 (missing FK indexes)**. In a fresh production database with typical financial data volumes (tens of thousands of anticipo, penalidad, devolucion, nota_credito rows), every endpoint that calls `existsByReservaId`, `findByReservaId`, or `findByFacturaId` performs a full sequential table scan. Reservation deletion triggers four such scans simultaneously. Under real transaction volumes, this will cause DB connection pool exhaustion and slow response times.

The fix is a 15-minute Flyway migration that adds nine `CREATE INDEX` statements — no code changes, no downtime for the table itself (PostgreSQL can create indexes `CONCURRENTLY` if needed). This is the only blocker to a first production deployment.

After C-1 is resolved, the application is structurally sound for a V1 launch with the following tracked follow-ups within the first sprint:

- **Must-track**: M-8 (Testcontainers), M-3 (CORS space trimming), H-3 (full validation errors)
- **Should-track**: H-2, H-1, M-7, L-6, L-3, L-4, L-8

**Architecture score: 72/100 | Security: 74/100 | Maintainability: 70/100 | Production readiness: 61/100 → 76/100 after C-1 fix**
