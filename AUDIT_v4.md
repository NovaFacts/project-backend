# NovaFacts Backend — Independent Full-Codebase Audit (Fourth Pass)

*Date: 2026-07-02 | Baseline: working tree following the M-6→I-3 audit-remediation cycle (Sprint 6+ changes, Flyway V1–V15, Canal CRUD, JWT caching)*
*Auditor scope: completely independent re-verification pass. AUDIT_v3.md findings are treated as historical claims only — every one was re-checked directly against current source, not assumed valid.*
*Files reviewed: all Java source under `src/main`/`src/test` (auth, reservation, property, temporada, canal, politicacancelacion, factura, notacredito, anticipo, penalidad, devolucion, rol, dashboard, common, config packages), all 15 Flyway migrations, `pom.xml`, `Dockerfile`, `docker-compose.yml`, `.dockerignore`, `application.properties`, `application-test.properties`, all test classes, `.github/` contents.*

---

## 1. Findings

---

### CRITICAL

---

#### C-1 — Missing indexes on FK columns in financial tables — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Critical |
| **Status** | **Fixed** |
| **Files** | `db/migration/V12__fk_indexes.sql` |
| **Evidence of fix** | V12 adds every index the original finding required — `idx_anticipo_reserva_id`, `idx_anticipo_usuario_id`, `idx_penalidad_reserva_id`, `idx_penalidad_usuario_id`, `idx_devolucion_reserva_id`, `idx_devolucion_anticipo_id`, `idx_devolucion_usuario_id`, `idx_nota_credito_factura_id`, `idx_nota_credito_usuario_id` — plus six more not originally requested (`idx_politica_cancelacion_propiedad_id`, `idx_factura_usuario_id`, `idx_reserva_canal_id`, `idx_reserva_temporada_id`, `idx_reserva_politica_cancelacion_id`, `idx_reserva_usuario_creador_id`, `idx_usuario_rol_id`), all via `CREATE INDEX IF NOT EXISTS`. |
| **Verified** | Direct migration read; confirmed applied and exercised by the full Testcontainers-backed test suite this session. |

---

#### C-2 — `Anticipo.estado` check-then-act race allows the same advance payment to be simultaneously applied to a factura discount *and* refunded via a devolución — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Critical |
| **Status** | **Fixed** |
| **Files** | `anticipo/repository/AnticipoRepository.java`, `factura/service/FacturaService.java:167-182` (`applyAnticipo`), `devolucion/service/DevolucionService.java:72-77` (`create`) |
| **Evidence of fix** | Added `AnticipoRepository.findByIdForUpdate(Long id)` — a `@Lock(LockModeType.PESSIMISTIC_WRITE)` query (`SELECT a FROM Anticipo a WHERE a.id = :id`), mirroring the pre-existing `PropertyRepository.findByIdForUpdate()` pattern exactly. Both `FacturaService.applyAnticipo()` and `DevolucionService.create()` now call `findByIdForUpdate()` instead of the unlocked `findById()` before checking `estado == REGISTRADO`, so a concurrent transaction on the same `anticipoId` blocks at the row lock until the first transaction commits, and re-reads the now-changed `estado` instead of racing past the check. |
| **Verified** | New `AnticipoConcurrencyTest` (`concurrent_factura_and_devolucion_on_same_anticipo_only_one_succeeds`) drives two real, concurrent transactions against a Testcontainers PostgreSQL instance and asserts exactly one succeeds. The test's validity was confirmed both ways: temporarily reverted to the unlocked `findById()`, it reproduced the exact bug (`factura=true devolucion=true` — both operations succeeded on the same anticipo) on one of three runs, consistent with a genuine, timing-dependent race; restored to the fix, it passed deterministically across 8 consecutive runs. Full suite: 87/87 passing. |

---

### HIGH

---

#### H-1 — Property reassignment without financial-history guard — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | High |
| **Status** | **Fixed** |
| **Files** | `reservation/service/ReservationService.java:143-153` |
| **Evidence of fix** | `update()` now checks `if (!reservation.getPropertyId().equals(request.getPropertyId()))`, and if true, checks `anticipoRepository.existsByReservaId(id) \|\| penalidadRepository.existsByReservaId(id) \|\| facturaRepository.existsByReservaId(id) \|\| devolucionRepository.existsByReservaId(id)`, throwing `409 CONFLICT` if any exist. Covers all 4 financial tables (original finding only required checking 3). |
| **Verified** | Direct code read; covered by `ReservationControllerTest.update_reservation_property_with_anticipo_returns_409` / `update_reservation_property_without_financial_history_returns_200`. |

---

#### H-2 — `TemporadaService.eliminar()` uninformative delete error — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | High |
| **Status** | **Fixed** |
| **Files** | `temporada/service/TemporadaService.java:64-69` |
| **Evidence of fix** | `eliminar()` now checks `reservationRepository.existsByTemporadaId(id)` before deleting, throwing `409 CONFLICT` with `"No se puede eliminar la temporada porque existen reservas que la referencian."` — the exact fix originally recommended. |
| **Verified** | Direct code read; covered by `TemporadaControllerTest.delete_temporada_with_existing_reservation_returns_409`. |

---

#### H-3 — `GlobalExceptionHandler` returns only the first validation error — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | High |
| **Status** | **Fixed** (different response shape than originally suggested, but the core defect is gone) |
| **Files** | `common/GlobalExceptionHandler.java:38-46` |
| **Evidence of fix** | No longer uses `findFirst()`. Current implementation: `ex.getBindingResult().getFieldErrors().stream().map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).collect(Collectors.joining("; "))`, returned under a single `"error"` key as a semicolon-joined string — not the array/`"errors"` structure originally suggested, but all field errors are now surfaced in one response instead of being silently dropped. |
| **Note** | A frontend wanting to highlight individual fields would need to split the string on `"; "` rather than iterate a JSON array. Not a defect, but worth a follow-up if per-field highlighting becomes a UX requirement. |
| **Verified** | Direct code read. |

---

#### H-4 — Unbounded `PropertyService.findAll()` — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | High |
| **Status** | **Fixed** |
| **Files** | `property/controller/PropertyController.java`, `property/service/PropertyService.java`, `property/repository/PropertyRepository.java` |
| **Evidence of fix** | `getAll(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size)`, capped via `Math.min(size, 100)`; `PropertyService.findAll(int, int)` builds a `Pageable` (`Sort.by("name")`) and returns `PageResponse<PropertyResponse>`; `PropertyRepository.findByActivaTrue(Pageable)` returns `Page<Property>`. Matches the originally recommended fix, plus adds a size cap not originally requested. |
| **Verified** | Direct code read; covered by `PropertyControllerTest`. |

---

#### H-5 — `PenalidadService.create()` allows multiple penalty charges against the same cancelled reservation, each independently validated against the full policy maximum

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Files** | `penalidad/service/PenalidadService.java:60-109`, `penalidad/repository/PenalidadRepository.java:15-16` |
| **Lines** | `create()` never calls the existing `existsByReservaId(Long)` method already declared on `PenalidadRepository.java:15-16` |
| **Root cause** | `create()` computes `maximoPenalidad` solely from `reserva.getMontoTotal()` and the policy's `porcentajeReembolso`, then checks `request.getMontoAprobado() <= maximoPenalidad`. It never checks whether a `Penalidad` already exists for this `reservaId`, and never subtracts previously-approved amounts. `penalidad.reserva_id` has no `UNIQUE` constraint (`V5__anticipos_penalidades_y_borrar_pagos.sql:20-32` — plain FK). The guard method needed (`existsByReservaId`) already exists on the repository — it is simply never called in this service. |
| **Why it is a problem** | Unlike C-2, this requires no concurrency at all — two *sequential* `POST /api/penalidades` calls against the same cancelled reservation each independently pass validation as long as each individual `montoAprobado` is within the full policy cap. Example: a 50%-refund policy on a 1,000,000 COP reservation caps the penalty at 500,000 COP; submitting the form twice (accidental double-submit, or two accounting staff unaware of each other — all of `ADMINISTRADOR`/`CONTADOR`/`AUXILIAR_CONTABLE` can write here per `SecurityConfig`) creates two 500,000 COP penalty rows — 1,000,000 COP charged against a 500,000 COP maximum. |
| **Production impact** | Direct financial overcharge to the guest, with no logic anywhere preventing it. |
| **Probability** | Medium — plausible via ordinary UI double-submit or duplicate manual entry, not just adversarial timing. |
| **Recommended fix** | In `create()`, reject if `penalidadRepository.existsByReservaId(request.getReservaId())` is already `true` — one penalty per cancelled reservation, consistent with how `Anticipo`'s state machine implicitly limits it to one active application. Nothing else in the domain model suggests multiple partial penalties per cancellation are intended. |
| **Breaking?** | Non-breaking for the intended single-penalty-per-cancellation use case. |
| **Effort** | 15 minutes. |
| **Confidence** | 100%. |

---

### MEDIUM

---

#### M-1 — `PropertyController` bare return types — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Medium |
| **Status** | **Fixed** |
| **Files** | `property/controller/PropertyController.java:24-25,31-32` |
| **Evidence of fix** | `getAll()` returns `ResponseEntity<PageResponse<PropertyResponse>>`, `getById()` returns `ResponseEntity<PropertyResponse>` — fully consistent with every other controller. |
| **Verified** | Direct code read. |

---

#### M-2 — `/api/reservations` English path inconsistent with the rest of the API

| Field | Detail |
|-------|--------|
| **Original severity** | Medium → **Downgraded to Low-equivalent** (kept under its original ID per audit-continuity convention) |
| **Status** | **Improved, not fully fixed** |
| **Files** | `reservation/controller/ReservationController.java:14` |
| **Lines** | `@RequestMapping({"/api/reservas", "/api/reservations"})` |
| **Root cause** | A consistent Spanish path (`/api/reservas`) was added, but the English path was deliberately kept as a backward-compatibility alias rather than removed — confirmed intentional via an explicit regression test, `ReservationControllerTest.legacy_reservations_path_returns_200`. |
| **Why it is a problem** | Two URLs now map to the same resource — minor API-surface duplication; any OpenAPI generation would show duplicate paths for the same operations. The original naming-inconsistency complaint is resolved (a correct Spanish path exists and is presumably what new/updated frontend code uses); what remains is a much smaller, intentional legacy-alias concern. |
| **Production impact** | None — purely cosmetic/documentation, not a runtime defect. |
| **Recommended fix** | Once frontend usage of `/api/reservas` is confirmed complete, deprecate and remove the `/api/reservations` alias in a future version. Not urgent. |
| **Breaking?** | Removing the alias would be breaking for any client still using it — verify frontend migration first. |
| **Effort** | 5 minutes once migration is confirmed. |
| **Confidence** | 100%. |

---

#### M-3 — `CORS_ALLOWED_ORIGINS` whitespace-around-commas bug

| Field | Detail |
|-------|--------|
| **Status** | **Still Present — unchanged from AUDIT_v3** |
| **Files** | `config/SecurityConfig.java:32-33,80` |
| **Lines** | `@Value("${cors.allowed-origins:http://localhost:5173}") private List<String> allowedOrigins;`, passed unmodified to `configuration.setAllowedOrigins(allowedOrigins)`. No `.strip()`/`.trim()` anywhere in the class. |
| **Root cause** | Unchanged — Spring's comma-split `@Value` binding to `List<String>` does not trim whitespace from individual elements. |
| **Why it is a problem** | Unchanged — an operator setting `CORS_ALLOWED_ORIGINS=https://app.example.com, https://admin.example.com` (space after comma) silently breaks CORS for the second origin with no error logged. |
| **Production impact** | Unchanged — silent CORS failure for any origin after the first, if the env var has spaces. |
| **Probability** | Medium — common human habit when setting comma-separated env vars. |
| **Recommended fix** | Unchanged: bind as a raw `String` and split+trim manually: `List<String> origins = Arrays.stream(allowedOriginsRaw.split(",")).map(String::strip).toList();` |
| **Breaking?** | Non-breaking — silently fixes malformed lists. |
| **Effort** | 15 minutes. |
| **Confidence** | 100%. |

---

#### M-4 — Seeder zero-monto devolución / missing DB-level positivity constraint

| Field | Detail |
|-------|--------|
| **Original severity** | Medium → **Downgraded to Low-equivalent** (kept under its original ID) |
| **Status** | **Improved, not fully fixed** |
| **Files** | `config/DevelopmentDataSeeder.java:299-303`, `db/migration/V6__facturas_notas_y_devoluciones.sql:34,47` |
| **Root cause** | The dev-visible symptom is gone: `DevelopmentDataSeeder` now seeds two devoluciones with realistic amounts (875,000.00 and 360,000.00 COP) — no `0.00`-monto row exists anymore, and `DevolucionRequest` already enforces `@DecimalMin("0.01")`. However, the underlying DB-level gap remains: no `CHECK (monto > 0)` constraint exists on `devolucion.monto` (plain `DECIMAL(12,2) NOT NULL`) — the same class of gap already closed for `politica_cancelacion.porcentaje_reembolso` (V14) and `temporada`'s date range (V15) was never applied here. |
| **Why it is a problem** | A direct SQL insert or any future write path bypassing `DevolucionRequest`'s DTO validation could still create a zero/negative-amount devolución, with nothing at the DB level to stop it. |
| **Production impact** | Low — no known current write path bypasses the DTO. |
| **Recommended fix** | Add a migration analogous to V14/V15: `ALTER TABLE devolucion ADD CONSTRAINT chk_devolucion_monto_positivo CHECK (monto > 0);`. Worth checking whether `anticipo.monto` and `penalidad.monto_aprobado` have the same gap in the same pass (not verified in this audit cycle — flagged as a follow-up check, not a confirmed finding). |
| **Breaking?** | Non-breaking (additive), pending a duplicate-data check against any live database. |
| **Effort** | 10 minutes. |
| **Confidence** | 100% on the absence of the constraint. |

---

#### M-5 — Hardcoded role IDs in seeder — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Medium |
| **Status** | **Fixed** |
| **Files** | `config/DevelopmentDataSeeder.java:141-144` |
| **Evidence of fix** | All four roles now looked up via `rolRepository.findByNombre("Administrador").orElseThrow()` etc. — no `findById(1..4)` anywhere in the file. |
| **Verified** | Direct code read. |

---

#### M-6 — `Reservation.clienteEmail` nullable

| Field | Detail |
|-------|--------|
| **Status** | **Still Present — reviewed this cycle and explicitly accepted as intentional design.** |
| **Files** | `reservation/entity/Reservation.java:48`, `reservation/dto/CreateReservationRequest.java:28` |
| **Disposition** | This finding was explicitly re-analyzed during this remediation cycle (full 8-point technical analysis performed) and the decision was made to keep the field optional, on the basis that guest email is not always available for phone/walk-in bookings, and the alternative (mandatory email) would break that legitimate use case. No code changes were made — this is a deliberate acceptance, not an oversight. Confirmed the code is genuinely unchanged: no `@NotNull`, no `nullable=false`. |
| **Recommendation** | No action needed unless the business requirement changes. If revisited, harden null-safety in any future email-dependent feature (e.g., notifications) rather than making the field mandatory. |
| **Confidence** | 100%. |

---

#### M-7 — Temporada overlapping date ranges — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Medium |
| **Status** | **Fixed** |
| **Files** | `temporada/repository/TemporadaRepository.java`, `temporada/service/TemporadaService.java` |
| **Evidence of fix** | `TemporadaRepository.existsOverlap(LocalDate, LocalDate)` and `existsOverlapExcludingId(LocalDate, LocalDate, Integer)` — two explicit methods (not a single nullable-parameter method), using correct exclusive-interval semantics (`t.fechaInicio < :fin AND t.fechaFin > :inicio`; adjacent ranges do not count as overlapping). Wired into `TemporadaService.crear()`/`actualizar()`. |
| **Verified** | Direct code read; covered by 6 dedicated tests in `TemporadaControllerTest` (partial overlap, identical range, contained range, adjacent range, no-op update, update-causes-overlap). |

---

#### M-8 — Test suite uses H2 instead of PostgreSQL — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Medium |
| **Status** | **Fixed** |
| **Files** | `src/test/resources/application-test.properties` |
| **Evidence of fix** | `spring.datasource.url=jdbc:tc:postgresql:15:///novafacts_test`, `driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver`, `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate`. No H2 anywhere — real PostgreSQL 15 (currently resolving to 15.18) via Testcontainers, with all 15 Flyway migrations genuinely exercised on every test run. |
| **Verified** | Direct file read; this session's own test runs confirmed real Testcontainers PostgreSQL execution throughout (Flyway migration logs, `Database version: 15.18` in test output). |

---

#### M-9 — `montoTotal` accepts `0.00` — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Medium |
| **Status** | **Fixed** |
| **Files** | `reservation/dto/CreateReservationRequest.java:34`, `reservation/dto/UpdateReservationRequest.java:35` |
| **Evidence of fix** | `@DecimalMin(value = "0.01", message = "El monto total debe ser mayor a cero")` in both DTOs, replacing the old `@DecimalMin("0.00", inclusive=true)`. |
| **Verified** | Direct code read; covered by `create_reservation_with_zero_montoTotal_returns_400` / `create_reservation_with_minimum_montoTotal_returns_201` / `update_reservation_with_zero_montoTotal_returns_400`. |

---

#### M-10 — `FacturaService.emitir()` naming confusion

| Field | Detail |
|-------|--------|
| **Status** | **Still Present — reviewed this cycle and explicitly accepted.** |
| **Files** | `factura/controller/FacturaController.java:45-47`, `factura/service/FacturaService.java:124-130` |
| **Disposition** | This finding was explicitly re-analyzed during this remediation cycle. The underlying claim was confirmed accurate and even extended (the "Emitida" label is duplicated across three separate frontend views' status mappings, and the entity's own `emitidaEn` timestamp is stamped at creation, not at the `emitir()` call — a second naming collision the original audit didn't catch). The decision was to accept the current naming as-is: renaming would require coordinated changes across the backend route, the frontend service call, and three separate Vue views' status-label mappings — a full-stack terminology correction disproportionate to a naming-only issue with no functional defect. No code changes were made. |
| **Recommendation** | No action needed unless a broader terminology cleanup is scheduled. |
| **Confidence** | 100%. |

---

#### M-11 — Inconsistent pagination across reference-data list endpoints

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `canal/controller/CanalController.java:24`, `politicacancelacion/controller/PoliticaCancelacionController.java:24,34`, `rol/controller/RolController.java:22`, `temporada/controller/TemporadaController.java:24` |
| **Root cause** | Pagination (`page`/`size` params, `PageResponse<T>`) was retrofitted onto the four higher-volume resources (`Property`, `Reservation`, `Factura`, `User`) but never applied to the reference-data resources, which all still return a bare `List<XResponse>`. |
| **Why it is a problem** | Inconsistent API contract — a client must special-case which endpoints paginate. `PoliticaCancelacionController` in particular has no inherent cardinality cap (one-or-more policies per property × property count), unlike `Canal`/`Rol`/`Temporada`, which are genuinely small, admin-curated sets with structurally bounded growth. |
| **Production impact** | Low today (small row counts across all four); `politica_cancelacion` specifically could grow in a way the others structurally cannot. |
| **Probability** | Low near-term; grows with the property portfolio. |
| **Recommended fix** | At minimum, paginate `PoliticaCancelacionController` following the exact `PropertyController` pattern already established. `Canal`/`Rol`/`Temporada` can reasonably remain unpaginated given their small, admin-curated nature — document this as an intentional exception rather than an oversight. |
| **Breaking?** | Breaking for `PoliticaCancelacionController` (response shape changes from `List<T>` to `PageResponse<T>`). |
| **Effort** | 20 minutes. |
| **Confidence** | 90%. |

---

#### M-12 — Blanket `FetchType.EAGER` on every `@ManyToOne` association produces unnecessarily wide joins on financial list/detail endpoints

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | Every entity with a relation: `Reservation.java` (4 EAGER associations), `Devolucion.java`, `Anticipo.java`, `Factura.java`, `NotaCredito.java`, `Penalidad.java`, `PoliticaCancelacion.java`, `User.java` — 17 `@ManyToOne` associations total, project-wide, all `FetchType.EAGER`, zero use of `LAZY` anywhere. |
| **Root cause** | Every to-one association was declared `EAGER` as an apparent blanket default rather than a per-relation decision. Since intermediate associations are also `EAGER`, loading one leaf entity can cascade into a wide join graph: `Devolucion` eagerly loads `reserva` and `anticipo`; `reserva` (a `Reservation`) itself eagerly loads `canal`, `temporada`, `politicaCancelacion`, `usuarioCreador`; `anticipo` (an `Anticipo`) itself eagerly loads its own `reserva` (a second, separate fetch of the same conceptual entity) and `usuario`. |
| **Why it is a problem** | `DevolucionService.toResponse()` only reads four scalar values (`d.getReserva().getId()`, `d.getAnticipo().getId()`, `d.getUsuario().getId()`, `d.getUsuario().getNombre()`) — yet Hibernate must hydrate the full nested `Reservation` graph (including its own 4 eager associations) to build this response, and does so *twice* for a single `Devolucion` row (once via `Devolucion.reserva`, once via `Devolucion.anticipo.reserva`). This is not a classic N+1 — Hibernate resolves single-valued `EAGER` associations via SQL `JOIN`, not per-row queries, and this does not break `Pageable` pagination the way an eager *collection* fetch would — but it is a substantially wider single query than necessary, on every list/detail call across all 5 financial modules, with query cost that grows with the depth of the eager chain rather than just row count. It also structurally forecloses using Spring Data projections or `@EntityGraph` to fetch leaner shapes for specific endpoints, since `EAGER` cannot be selectively disabled per-query without an explicit JPQL override. |
| **Production impact** | Measurable, avoidable I/O and object-instantiation cost at scale; currently invisible at class-project data volumes. Not an active correctness bug — a design/performance concern that compounds as data grows. |
| **Probability** | Certain — this is the current, deployed fetch strategy for every entity in the project. |
| **Recommended fix** | Default new relations to `LAZY`; use `@EntityGraph` or explicit `JOIN FETCH` JPQL (mirroring `ReservationRepository`'s existing precedent of explicit `@Query` methods) for the specific queries that genuinely need eager-loaded names in a response. Where only an ID is needed (the common case in every `toResponse()` reviewed), `entity.getReserva().getId()` works without triggering a fetch at all under `LAZY`, since the FK value doesn't require loading the associated row. Retrofitting existing `EAGER` to `LAZY` requires auditing every access path for lazy-initialization-outside-transaction risk (`open-in-view=false` is already set, so this is a real but tractable verification task, not a blind flip) — recommend an incremental approach, new code first. |
| **Breaking?** | Non-breaking if done incrementally; risky if done as a blanket retrofit (`LazyInitializationException` risk for any access outside a transaction). |
| **Effort** | 2-3 hours for a careful incremental pass. |
| **Confidence** | 95%. |

---

#### M-13 — Financial-history delete/reassignment guard duplicated verbatim in `ReservationService`

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `reservation/service/ReservationService.java:148-153` (in `update()`) and `201-206` (in `delete()`) |
| **Root cause** | Both blocks contain the identical 4-line OR-chain: `anticipoRepository.existsByReservaId(id) \|\| penalidadRepository.existsByReservaId(id) \|\| facturaRepository.existsByReservaId(id) \|\| devolucionRepository.existsByReservaId(id)`. The `update()` guard (added for H-1) was copy-pasted from the pre-existing `delete()` guard rather than extracted into a shared private method — the code's own comment acknowledges this directly: *"Same guard pattern and repositories as delete()"*. |
| **Why it is a problem** | Classic duplicated-logic maintainability risk: if a fifth financial-child repository is ever added, or the definition of "financial history" changes, a developer must remember to update both occurrences. Missing one silently reintroduces exactly the bug H-1 was fixed to prevent, in only one of the two code paths — a subtle, easy-to-miss-in-review regression vector. |
| **Production impact** | None today — both copies are currently identical and correct. The risk is entirely forward-looking, triggered by the next edit to this logic. |
| **Probability** | Low near-term, but the failure mode (silent partial fix) is exactly the kind of subtle bug that slips through review. |
| **Recommended fix** | Extract to a private method: `private boolean hasFinancialHistory(Long reservaId) { return anticipoRepository.existsByReservaId(reservaId) \|\| penalidadRepository.existsByReservaId(reservaId) \|\| facturaRepository.existsByReservaId(reservaId) \|\| devolucionRepository.existsByReservaId(reservaId); }`, call it from both `update()` and `delete()`. |
| **Breaking?** | Non-breaking — pure refactor, identical behavior. |
| **Effort** | 10 minutes. |
| **Confidence** | 100%. |

---

#### M-14 — `GET /api/dashboard` exposes aggregated financial data without the role restriction its underlying endpoints have

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Files** | `dashboard/controller/DashboardController.java:11`, `dashboard/service/DashboardService.java:33-47`, `config/SecurityConfig.java:45-67` |
| **Root cause** | `SecurityConfig` explicitly restricts `GET /api/anticipos/**` to `hasAnyRole("ADMINISTRADOR","CONTADOR","AUXILIAR_CONTABLE")` and `GET /api/facturas/**` to `hasAnyRole("ADMINISTRADOR","CONTADOR")`, but has no rule at all for `/api/dashboard/**` — it falls through to the generic `.anyRequest().authenticated()`. `DashboardService.getSummary()` returns `montoTotalAnticipos` (a `BigDecimal` sum of all advance payments) plus invoice/reservation status counts, sourced from exactly the data those role-restricted endpoints protect. |
| **Why it is a problem** | Any authenticated user of any role — including lower-privilege roles like `Recepcionista` (confirmed to exist as a real seeded role) — can call `GET /api/dashboard` and read aggregate financial figures (total anticipo amounts, invoice-status counts) that they are explicitly denied direct access to via `/api/anticipos` and `/api/facturas`. This is a genuine RBAC-boundary bypass via an aggregation endpoint, not a data-shape coincidence. |
| **Production impact** | Financial data exposure to staff roles not intended to see accounting aggregates; inconsistent with the RBAC boundaries already established for the same underlying data elsewhere in the API. |
| **Probability** | Certain — reproducible today by any authenticated non-accounting-role user. |
| **Recommended fix** | Add `.requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAnyRole("ADMINISTRADOR", "CONTADOR")` to `SecurityConfig`, matching the restriction already on `/api/facturas/**`. |
| **Breaking?** | Breaking for any non-accounting-role user currently relying on dashboard access — verify intended frontend usage (which roles see the dashboard today) before applying. |
| **Effort** | 5 minutes. |
| **Confidence** | 100%. |

---

### LOW

---

All nine LOW findings from the previous audit are resolved.

#### L-1 — Empty `BackendApplicationTests` — ✅ RESOLVED
Now a genuine Testcontainers-backed `contextLoads()` smoke test (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`), exercising datasource/Flyway/Security/JPA wiring against real PostgreSQL — not the old "skipped, requires PostgreSQL" stub.

#### L-2 — `booking` package `double`-based monetary math — ✅ RESOLVED (package deleted)
The entire `booking` package (`src/main/java/com/novafacts/backend/booking/` and its test package) was confirmed dead code via full dependency analysis (zero references anywhere outside the package, no Spring wiring, no persistence) and deleted in its entirety, along with its 31 tests. `find` for the package returns nothing.

#### L-3 — `Temporada.nombre` no UNIQUE constraint — ✅ RESOLVED
`V13__unique_index_temporada_nombre_ci.sql`: `CREATE UNIQUE INDEX idx_temporada_nombre_ci ON temporada (LOWER(nombre));`, paired with `TemporadaService`/`TemporadaRepository` service-level `existsByNombreIgnoreCase`/`existsByNombreIgnoreCaseAndIdNot` checks.

#### L-4 — No `CHECK` on `porcentaje_reembolso` — ✅ RESOLVED
`V14__check_politica_cancelacion_porcentaje_reembolso.sql`: `CHECK (porcentaje_reembolso >= 0 AND porcentaje_reembolso <= 100)`.

#### L-5 — `postgres:15` not pinned — ✅ RESOLVED
`docker-compose.yml:5`: `image: postgres:15.18-alpine` — pinned to an exact current patch, Alpine variant.

#### L-6 — JDK image instead of JRE — ✅ RESOLVED
`Dockerfile:1`: `FROM eclipse-temurin:21-jre-alpine`. Measured image size reduction: 665MB → 399MB (~40%).

#### L-7 — `Canal` has no write operations — ✅ RESOLVED
`CanalController` now has `GET/{id}`, `POST`, `PUT/{id}`, `DELETE/{id}` in addition to the original list endpoint. `CanalService` has `crear()`/`actualizar()` with case-insensitive uniqueness checks and `eliminar()` with a `reservationRepository.existsByCanalId(id)` delete-guard. A `CanalRequest` DTO and a 14-test `CanalControllerTest` both exist.

#### L-8 — No `CHECK` on Temporada date range — ✅ RESOLVED
`V15__check_temporada_fecha_rango.sql`: `CHECK (fecha_inicio < fecha_fin)` — strict inequality, correctly matching the service-layer `validarFechas()` semantics (also rejects same-day ranges, stricter than the originally-suggested `<=`).

#### L-9 — Empty `pom.xml` metadata — ✅ RESOLVED
`pom.xml:14-15`: `<name>NovaFacts Backend</name>`, `<description>Spring Boot API for the NovaFacts short-term rental financial management system</description>`. The empty `<url/>`, `<licenses>`, `<developers>`, `<scm>` blocks were removed entirely rather than filled with placeholder data (no `LICENSE` file exists in the repo, so fabricating license/SCM metadata would have been actively misleading).

---

### INFORMATIONAL

---

#### I-1 — JWT filter DB lookup on every request — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Informational |
| **Status** | **Fixed** |
| **Files** | `config/CacheConfig.java` (new), `auth/service/UserDetailsServiceImpl.java`, `pom.xml`, `application.properties` |
| **Evidence of fix** | New `CacheConfig` (`@EnableCaching`, Caffeine-backed `CacheManager`, `expireAfterWrite` TTL from `app.security.user-cache-ttl-seconds`, default 30s). `UserDetailsServiceImpl.loadUserByUsername()` now carries `@Cacheable(CacheConfig.USER_DETAILS_CACHE)`. `pom.xml` has `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`. Negative results (`UsernameNotFoundException`) are correctly never cached, per `@Cacheable` semantics — failed logins always hit the DB fresh. |
| **Verified** | Direct code read plus 4 dedicated tests (`UserDetailsCacheTest`) proving first-call-hits-DB, repeated-calls-within-TTL-reuse-cache, and genuine TTL expiration against the real Caffeine clock (not mocked time). |

---

#### I-2 — `open-in-view` documentation note

| Field | Detail |
|-------|--------|
| **Status** | **Unchanged — no action was ever required.** |
| **Files** | `application.properties:9` |
| **Note** | `spring.jpa.open-in-view=false` is set once in `application.properties` and correctly inherited by both `application-test.properties` and `application-dev.properties` (neither overrides it). Situation identical to the original audit's description. Purely a documentation clarification, not an actionable finding. |
| **Confidence** | 100%. |

---

#### I-3 — Dead `findByClienteEmail()` repository method — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Original severity** | Informational |
| **Status** | **Fixed** |
| **Files** | `reservation/repository/ReservationRepository.java` |
| **Evidence of fix** | `findByClienteEmail(String)` and its now-unused `import java.util.List;` were both removed, following a full dependency analysis confirming zero callers anywhere in the codebase. The distinct, actively-used `findByClienteEmailAndCheckInDate(String, LocalDate)` (called from `DevelopmentDataSeeder`) was correctly left untouched. |
| **Verified** | `grep -rn "findByClienteEmail\b"` across the entire repository returns zero matches; full test suite (86 tests) unaffected. |

---

#### I-4 — Delete-guard pattern repeated near-identically across `CanalService`, `TemporadaService`, `PoliticaCancelacionService`

| Field | Detail |
|-------|--------|
| **Severity** | Informational |
| **Files** | `canal/service/CanalService.java`, `temporada/service/TemporadaService.java`, `politicacancelacion/service/PoliticaCancelacionService.java` |
| **Note** | Each `eliminar(Integer id)` method follows the identical shape — `getOrThrow(id)` → single `existsByXId(id)` check on a dependent repository → `throw ResponseStatusException(CONFLICT, "...")` if referenced → `repository.delete(entity)` — because each service was implemented independently across different remediation tasks, faithfully copying the same guard shape rather than extracting a shared helper. This is **not a bug**; behavior is correct and consistent across all three. The duplication is small (5-7 lines each) and tied to different FK relationships per service, so extracting a shared abstraction would likely reduce readability more than it saves. No fix recommended unless a fourth or fifth occurrence appears, at which point a shared template method would become worthwhile. |
| **Confidence** | 100%. |

---

## 2. Scores

### Overall Architecture Score: 90 / 100 (was 72)

Strengths carried forward: clean, consistent feature-package structure; constructor injection throughout; well-implemented pessimistic-locking pattern for double-booking prevention (`PropertyRepository.findByIdForUpdate`). New strengths: dead `booking` package removed entirely; full CRUD now available for all reference-data resources (`Canal`, `Temporada`, `PoliticaCancelacion`, `Property`); season-overlap and unique-name validation now enforced at both service and DB layers with a consistent, repeatable pattern. Deductions: blanket `EAGER` fetch strategy across all 17 relations, M-12 (−4); inconsistent pagination across reference-data endpoints, M-11 (−3); duplicated financial-history guard logic, M-13 (−2); duplicated delete-guard pattern across 3 services, I-4 (−1, informational-level only).

---

### Security Score: 78 / 100 (was 74)

Strengths carried forward: stateless JWT with issuer/audience validation, BCrypt password hashing with timing-equalization on login, granular URL-based RBAC (`@EnableMethodSecurity`), soft-delete with disabled-account enforcement in the JWT filter (now cache-backed with a bounded 30s staleness window rather than removed). New strengths: JDK→JRE swap reduces attack surface (deduction removed, +5); confirmed no hardcoded JWT secret fallback exists at any layer (fails fast if `JWT_SECRET` is genuinely unset); confirmed the new Canal write endpoints are correctly covered by existing RBAC rules; confirmed every `@Query` across the entire codebase uses parameterized binding, zero SQL/JPQL injection surface found. Deductions carried forward: no rate limiting on `/api/auth/login`, confirmed still absent project-wide (−12); CORS origin whitespace-trimming bug, M-3, still present (−6); admin password default (`Admin2024!`) still ships as a fallback, though a startup warning banner is logged when in use (−3). New deduction: `/api/dashboard` exposes RBAC-protected financial aggregates to any authenticated role, M-14 (−4).

---

### Maintainability Score: 89 / 100 (was 70)

Strengths carried forward: consistent service/repository/controller pattern, clear exception-handling hierarchy, `PageResponse` used consistently across paginated services, idiomatic Spring Data JPQL where derived names would conflict. New strengths: `PropertyController` bare-type inconsistency fixed; `GlobalExceptionHandler` now surfaces all validation errors, not just the first; hardcoded role IDs replaced with name-based lookups; `BackendApplicationTests` now a genuine smoke test; FK indexes eliminate the need for undocumented tribal knowledge about query performance; `pom.xml` metadata filled in honestly (no fabricated license/SCM data). Deductions: `emitir` naming confusion remains (accepted, not fixed) (−4); English/Spanish dual-path on reservations remains as an intentional legacy alias (−2); blanket EAGER fetch strategy limits query-shaping flexibility (−3); duplicated guard logic (M-13, I-4) (−2).

---

### Production Readiness Score: 83 / 100 (was 61)

Strengths carried forward: Flyway migrations with proper versioning (now 15, up from 11, including 4 new DB-level `CHECK`/`UNIQUE` constraints), pessimistic locking for financial concurrency on the property/reservation path, `healthcheck` on the Postgres container, no demo data seeded in prod profile, admin password override via env var, `.dockerignore` preventing secret leaks. New strengths: FK indexes present and verified (C-1 fully resolved); test suite now runs against real, pinned PostgreSQL 15.18 via Testcontainers with Flyway genuinely exercised on every run (M-8 fully resolved); Docker image size reduced ~40% via JRE swap; Postgres image pinned to an exact patch version; **C-2 (Anticipo double-apply/double-refund race) fixed and verified** via a pessimistic-lock repository method plus a dedicated concurrency regression test that was confirmed to fail against the unfixed code and pass deterministically against the fix (deduction removed, +15) — no Critical findings remain open. Deductions: H-5 (Penalidad double-charge via sequential double-submit, no concurrency needed) remains open (−8); no DB-level positivity constraint on `devolucion.monto` (M-4 residual) (−3); no rate limiting on login (−6).

---

## 3. Summary by Severity

| Severity | Count (new/updated, action needed) | Resolved this cycle |
|----------|-------------------------------------|----------------------|
| Critical | 0 | C-1, **C-2** (Anticipo double-apply/double-refund race) |
| High | 1 — **H-5** (Penalidad double-charge) | H-1, H-2, H-3, H-4 |
| Medium | 6 — **M-3** (unchanged), **M-11, M-12, M-13, M-14** (new); M-2 and M-4 improved/downgraded but retain residual low-priority action items | M-1, M-5, M-7, M-8, M-9 fixed; M-6, M-10 reviewed and explicitly accepted (no action) |
| Low | 0 | L-1 through L-9 — **all resolved** |
| Informational | 1 — **I-4** (new, no action needed) | I-1, I-3 fixed; I-2 unchanged/no action ever needed |

---

## 4. Top 10 Recommended Next Improvements

Ordered by impact-to-effort ratio:

| # | Finding | Impact | Effort | Why First |
|---|---------|--------|--------|-----------|
| 1 | **H-5** — Guard `PenalidadService.create()` against a second penalty on an already-penalized reservation | High financial integrity | 15 min | Highest-severity open finding now that C-2 is resolved. No concurrency needed to trigger — a plain double-submit currently succeeds; the guard method already exists and is simply unused |
| 2 | **M-14** — Restrict `GET /api/dashboard` to `ADMINISTRADOR`/`CONTADOR` | Medium security | 5 min | One-line `SecurityConfig` addition closes a real RBAC-boundary bypass |
| 3 | **M-13** — Extract the duplicated financial-history guard in `ReservationService` into a shared method | Medium maintainability | 10 min | Removes a silent-partial-fix risk before the next edit to this logic |
| 4 | **M-3** — Strip whitespace from CORS origin list | Medium reliability | 15 min | Still the same silent production-failure risk identified in the previous pass |
| 5 | **M-4** — Add `CHECK (monto > 0)` on `devolucion.monto`; check `anticipo`/`penalidad` for the same gap | Low-medium data integrity | 10-30 min | Closes the last DB-level positivity gap in the financial schema |
| 6 | **M-11** — Paginate `PoliticaCancelacionController` | Medium consistency | 20 min | The one reference-data endpoint with genuinely unbounded growth potential |
| 7 | **M-12** — Begin migrating `@ManyToOne` associations to `LAZY` + `@EntityGraph`, starting with the financial modules | Medium performance | 2-3 h (incremental) | No active bug today, but the fix gets more expensive to retrofit the longer blanket EAGER persists |
| 8 | Rate limiting on `/api/auth/login` | High security | Half day+ | Still entirely unaddressed — no library, no filter, no gateway-level protection found anywhere |
| 9 | **M-2** — Deprecate the `/api/reservations` legacy alias once frontend migration to `/api/reservas` is confirmed | Low cleanup | 5 min | Low priority, but a clean close-out once safe |

*C-2 (Anticipo double-apply/double-refund race) — resolved this cycle; see Critical findings above.*

---

## 5. Production Readiness Verdict

**The application is conditionally ready for production — no Critical findings remain open, but one High-severity financial-integrity gap should be closed first.**

Both Critical findings identified across this and the previous audit pass are now resolved. **C-1** (missing FK indexes) was fixed prior to this pass. **C-2** — a check-then-act race in `Anticipo` state transitions that allowed the same advance payment to be simultaneously deducted from an invoice *and* refunded via a devolución — has now also been fixed and independently verified: a pessimistic-lock repository method (`AnticipoRepository.findByIdForUpdate()`, mirroring the pattern already used for reservation-overlap protection) was added and wired into both call sites, and a dedicated concurrency test was confirmed to reproduce the original bug against the unfixed code before passing deterministically against the fix. This closes the last blocking issue of the previous verdict.

The most serious remaining open finding is **H-5**: `PenalidadService` currently allows a guest to be charged more than a cancellation policy's maximum penalty via nothing more than an accidental double form submission — no race condition or adversarial timing required, and no concurrency-safety concern (unlike C-2) — just a missing guard check using a repository method (`existsByReservaId`) that already exists but is never called. This is a small, well-scoped fix (~15 minutes) and is the single highest-priority item before launch, given its direct, easily-triggered financial impact.

Every other category shows substantial, verified improvement since the previous pass: all 9 LOW findings resolved, 5 of 10 MEDIUM findings resolved outright (2 more explicitly reviewed and accepted as intentional, not oversights), all 4 original HIGH findings resolved, the test suite now runs against real, pinned PostgreSQL with Flyway genuinely exercised, and the Docker image is ~40% smaller with a pinned, current PostgreSQL patch version.

Recommended before a V1 launch:

- **Must-fix**: H-5 (Penalidad double-charge)
- **Must-track**: M-14 (dashboard RBAC gap), M-3 (CORS whitespace trimming — carried over unresolved from the previous audit), rate limiting on login (never addressed across either audit pass)
- **Should-track**: M-13, M-4, M-11, M-12

**Architecture score: 90/100 | Security: 78/100 | Maintainability: 89/100 | Production readiness: 83/100 → projected 88+/100 once H-5 is closed**

---

## 6. Post-Audit Addendum (2026-07-04)

Findings below were resolved or discovered in implementation work after this audit pass was written. They are recorded here rather than in a new full audit cycle, since neither changes the scope of the pass above.

**Resolved since this audit:**
- **M-14** (dashboard RBAC gap) — fixed. `GET /api/dashboard/**` now requires `ADMINISTRADOR`, `CONTADOR`, or `AUXILIAR_CONTABLE`; `Recepcionista` correctly receives 403. A separate, systemic gap discovered in the process — every `hasRole`/`hasAnyRole` denial across the whole app was returning 401 instead of 403, because no `AccessDeniedHandler` was configured — was fixed at the same time.
- **Rate limiting on login** (Top 10 item #8) — fixed. `POST /api/auth/login` is now protected by a per-client-IP token-bucket filter (Bucket4j, in-memory), configurable via `application.properties`, returning 429 with a `Retry-After` header once exhausted. See implementation report for full design rationale and verification.
- Spring Boot Actuator added, with only `health`/`health/liveness`/`health/readiness` exposed (`show-details=never`) — closes the "no health endpoint" production-readiness gap noted implicitly by the absence of any Actuator dependency in `pom.xml` at the time of this audit.

**Discovered during Actuator implementation, since fully investigated and fixed:**

#### H-404 — Unmapped routes incorrectly return HTTP 500 instead of HTTP 404 — ✅ RESOLVED

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Status** | **Fixed and verified** |
| **Files** | `common/GlobalExceptionHandler.java` — added one new `@ExceptionHandler(NoResourceFoundException.class)` |
| **Evidence** | `GET /actuator/env` (unexposed) and `GET /api/totally-fake-endpoint-12345` (never-existed path) both returned `500 {"error":"Error interno del servidor"}` instead of `404` — reproduced live, with an *authenticated* request (an unauthenticated request to the same path correctly returns 401 from `AuthorizationFilter`, since it never reaches Spring MVC at all — the 500 only occurs once a request has passed Spring Security and reached `DispatcherServlet`). |
| **Audit hypothesis: DISPROVEN.** | The original hypothesis named `NoHandlerFoundException`. The actual exception, confirmed via a captured stack trace from the running app, is **`org.springframework.web.servlet.resource.NoResourceFoundException`** — a different class, thrown by Spring's auto-registered catch-all static-resource handler (`ResourceHttpRequestHandler`, mapped as a fallback for any path with no matching `@RequestMapping`), not by `DispatcherServlet`'s handler-mapping resolution itself. `NoHandlerFoundException` is only thrown when `spring.mvc.throw-exception-if-no-handler-found=true` is explicitly set, which it is not in this project. |
| **Confirmed root cause** | `NoResourceFoundException` (verified via `javap` against the actual `spring-webmvc-6.2.18.jar` in use) `extends jakarta.servlet.ServletException implements org.springframework.web.ErrorResponse`, and self-reports `getStatusCode() → 404`. Spring's own default exception machinery would honor that and return a correct 404 automatically — except `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` intercepts it first (no more specific handler existed for it), discards its self-reported status, and hardcodes 500. |
| **Complete flow** | Authenticated request → passes `JwtAuthenticationFilter`/`LoginRateLimitFilter`/`AuthorizationFilter` (satisfies `.anyRequest().authenticated()`) → `DispatcherServlet` finds no `@RequestMapping` match → falls back to the static-resource handler → no file found → `NoResourceFoundException` thrown → `ExceptionHandlerExceptionResolver` matches only `GlobalExceptionHandler`'s generic `Exception.class` handler → hardcoded 500. |
| **Is `GlobalExceptionHandler` responsible?** | Yes, confirmed directly — it is the sole intercepting component (no `WebMvcConfigurer`, no custom `ErrorController`, no other `@ControllerAdvice` exist anywhere in the codebase). `SecurityConfig` and the two filters are not involved in this specific bug. |
| **Fix applied** | One new, specific `@ExceptionHandler(NoResourceFoundException.class)` in `GlobalExceptionHandler`, returning `404 {"error":"Recurso no encontrado"}` — mirrors the exact existing one-method-per-exception-type pattern already used for the other five handlers in this class. The generic `Exception.class` catch-all was left completely untouched, so every other currently-500'd exception type is unaffected. |
| **Verification performed** | Full backend suite `95/95` passing (91 baseline + 4 new tests in `GlobalExceptionHandlerTest`, covering: authenticated unknown route → 404; unauthenticated unknown route → 401, unchanged, never reaches MVC; unexposed Actuator endpoint → 404; a real business 404 via `ResponseStatusException` keeps its own specific message, proving the fix doesn't swallow existing 404s). Live-verified against the rebuilt/redeployed app: both original evidence paths now return `404`; unauthenticated requests still `401`; `/actuator/health` still `200` with no auth; validation errors still `400` with unchanged messages; M-14 RBAC (`Recepcionista` → 403 on dashboard) unaffected; login rate limiting unaffected. |
| **Breaking?** | Non-breaking — 404 is the semantically correct status for this case in every client; no API contract changed. |
| **Effort** | ~15 minutes once the exact exception was identified. |
| **Confidence** | 100% — root cause confirmed via a real captured stack trace and `javap` class-hierarchy inspection, not inferred. |
