# NovaFacts Backend — Senior Code Audit (Second Pass)

*Scope: all working-tree source as of 2026-06-30, post-sprint-6. All findings from `AUDIT.md` (C-1 through M-10, plus previous fixes applied in this session) are assumed resolved. This document covers only new findings.*

*Files reviewed: all Java source under `com.novafacts.backend`, all Flyway migrations V1–V11, `docker-compose.yml`, `Dockerfile`, `.dockerignore`, `application.properties`. Two independent audit passes were cross-referenced; disagreements in severity are noted.*

---

## Executive Summary

| Severity | Count |
|----------|-------|
| Critical | 1 |
| High | 6 |
| Medium | 8 |
| Low | 7 |
| **Total** | **22** |

The most urgent finding is a **double-refund race condition (C-1)**: concurrent requests against the same anticipo can produce two Devolucion records or simultaneously create a Devolucion and apply the anticipo to a Factura. No pessimistic lock is taken before the state check. The fix mirrors the pattern already used in `ReservationService` for double-booking prevention and takes under 30 minutes.

After C-1, the next priority cluster is business-rule correctness: negative invoice totals (H-3), overcapped refunds (H-2), anticipo registration on closed reservations (H-4), and a missing DB UNIQUE constraint on `factura.reserva_id` that allows duplicate invoices under concurrency (H-5).

Two findings were missed by the primary pass and caught by the secondary review: the `/api/dashboard` endpoint has no role restriction (M-A — any authenticated user can read financial aggregates), and `FacturaService.applyAnticipo()` does not validate `descuentoAnticipo` against `anticipo.getMonto()`, allowing an anticipo to be permanently burned with an arbitrary deduction (M-B).

---

## Findings

### CRITICAL

---

**C-1 — Double-refund / double-apply race condition on `Anticipo` state**

- **Evidence:** `DevolucionService.create()` reads `anticipo.getEstado()`, checks it equals `REGISTRADO`, then writes `DEVUELTO`. `FacturaService.applyAnticipo()` does the same check before writing `APLICADO`. Neither method acquires a row lock before the read.
- **Root cause:** The pessimistic locking pattern used in `ReservationService.lockPropertyOrThrow()` (`@Lock(PESSIMISTIC_WRITE)` via `findByIdForUpdate`) was never applied to `Anticipo`.
- **Failure scenario:** Two concurrent `POST /api/devoluciones` with the same `anticipoId` arrive simultaneously. Both threads read `REGISTRADO`, both pass the state check, both write `DEVUELTO`, both insert a `Devolucion` row. The company disburses the same advance payment twice. The same race exists between a devolucion and a factura creation that references the same anticipo.
- **Files:** `devolucion/service/DevolucionService.java:62`, `factura/service/FacturaService.java` (`applyAnticipo`), `anticipo/repository/AnticipoRepository.java`
- **Fix:** Add `findByIdForUpdate` to `AnticipoRepository`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Anticipo a WHERE a.id = :id")
  Optional<Anticipo> findByIdForUpdate(@Param("id") Long id);
  ```
  Replace every `anticipoRepository.findById(anticipoId)` in `DevolucionService.create()` and `FacturaService.applyAnticipo()` with `findByIdForUpdate(anticipoId)`. Both callers are already inside `@Transactional` methods; the lock is held until commit.
- **Why safe:** Identical pattern to the existing pessimistic lock in `PropertyRepository`. Lock scope is one transaction; tests use rollback isolation and are unaffected.
- **Side effects:** Slightly increased latency under concurrent anticipo operations; serializes writes on a single anticipo row. Acceptable — an anticipo is a one-time-use instrument.
- **Effort:** 30 minutes.
- **Confidence:** CONFIRMED.

---

### HIGH

---

**H-1 — Multiple `NotaCredito` records can cumulatively exceed the invoice total**

- **Evidence:** `NotaCreditoService.create()` line 73 compares the *new* note's amount against `factura.getTotal()` alone. No sum of already-issued credit notes is consulted.
- **Root cause:** Per-note validation; no accumulated total query.
- **Failure scenario:** Invoice total = 1,000,000 COP. Two sequential (or concurrent) POST requests each with `monto = 900,000` both pass the check. Combined credit notes = 1,800,000 COP issued against a 1,000,000 COP invoice.
- **Files:** `notacredito/service/NotaCreditoService.java:73`, `notacredito/repository/NotaCreditoRepository.java`
- **Fix:** Add a sum query to `NotaCreditoRepository`:
  ```java
  @Query("SELECT COALESCE(SUM(nc.monto), 0) FROM NotaCredito nc WHERE nc.factura.id = :facturaId")
  BigDecimal sumMontoByFacturaId(@Param("facturaId") Long facturaId);
  ```
  Before saving, replace the existing check with:
  ```java
  BigDecimal yaEmitido = notaCreditoRepository.sumMontoByFacturaId(factura.getId());
  if (yaEmitido.add(request.getMonto()).compareTo(factura.getTotal()) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Las notas de crédito acumuladas superarían el total de la factura");
  }
  ```
- **Why safe:** Read-only sum query inside the same `@Transactional` boundary as the insert. Existing tests create one note per factura — unaffected.
- **Effort:** 20 minutes.
- **Confidence:** CONFIRMED.

---

**H-2 — `DevolucionService.create()` does not cap refund amount at `anticipo.getMonto()`**

- **Evidence:** `DevolucionRequest.monto` is validated only by `@DecimalMin("0.01")`. Inside `DevolucionService.create()`, the monto is stored verbatim with no comparison against `anticipo.getMonto()`.
- **Root cause:** Business rule missing from the service layer.
- **Failure scenario:** Anticipo for 500,000 COP. POST `/api/devoluciones` with `monto=5,000,000`. Passes all validation; a `Devolucion` row storing 10× the original advance payment is persisted. If processed, 10× is disbursed.
- **Files:** `devolucion/service/DevolucionService.java:88`
- **Fix:** After loading the anticipo, before saving:
  ```java
  if (request.getMonto().compareTo(anticipo.getMonto()) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "El monto de la devolución no puede superar el monto del anticipo");
  }
  ```
- **Why safe:** Pure guard; no state change. Existing test uses `monto == anticipo.monto` — passes.
- **Effort:** 5 minutes.
- **Confidence:** CONFIRMED.

---

**H-3 — `FacturaService.create()` can persist a negative invoice total**

- **Evidence:** `FacturaService.java` computes `total = subtotal - descuentoAnticipo + recargoPenalidad + impuestos`. No floor check exists.
- **Root cause:** Client can submit `descuentoAnticipo` larger than the rest of the addends. The DTO validates each field individually but not the combined result.
- **Failure scenario:** POST `/api/facturas` with `subtotal=100`, `descuentoAnticipo=200`, `recargoPenalidad=0`, `impuestos=0` → `total = -100`. A negative-total invoice is stored as `PENDING` and can be transitioned to `PAID` with no further validation.
- **Files:** `factura/service/FacturaService.java` (total computation)
- **Fix:** After computing `total`, add:
  ```java
  if (total.compareTo(BigDecimal.ZERO) < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "El total de la factura no puede ser negativo. Revise el descuento por anticipo.");
  }
  ```
- **Why safe:** Guard only; no existing test constructs a negative total.
- **Effort:** 5 minutes.
- **Confidence:** CONFIRMED.

---

**H-4 — `AnticipoService.create()` allows anticipos on CANCELLED / COMPLETED reservations**

- **Evidence:** `AnticipoService.create()` loads the `Reservation` but does not check `reserva.getStatus()`. The M-10 fix added this guard to `PenalidadService` only.
- **Root cause:** Business rule missing from `AnticipoService`; oversight during M-10 implementation.
- **Failure scenario:** Reservation is CANCELLED. POST `/api/anticipos` succeeds — anticipo enters system as `REGISTRADO`. `FacturaService` rejects invoicing a CANCELLED reservation, so the anticipo can never be `APLICADO`. It can be `DEVUELTO` via `DevolucionService` even though no money was ever received for the cancelled booking, creating a spurious refund.
- **Files:** `anticipo/service/AnticipoService.java:50-60`
- **Fix:** After loading reserva:
  ```java
  if (reserva.getStatus() == ReservationStatus.CANCELLED
          || reserva.getStatus() == ReservationStatus.COMPLETED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "No se puede registrar un anticipo sobre una reserva cancelada o completada");
  }
  ```
  `ReservationStatus` is already imported in `PenalidadService`; same import needed here.
- **Why safe:** Guard only; existing tests create anticipos on CONFIRMED reservations.
- **Effort:** 5 minutes.
- **Confidence:** CONFIRMED.

---

**H-5 — TOCTOU race allows duplicate `Factura` per reservation; no DB-level UNIQUE constraint on `factura.reserva_id`**

- **Evidence:** `FacturaService.create()` uses `facturaRepository.existsByReservaId(request.getReservaId())` — application-level uniqueness. No `UNIQUE` constraint on `factura.reserva_id` exists in any Flyway migration (V1–V11).
- **Root cause:** Same pattern as M-6 for `propiedad.nombre`: concurrent requests can both pass `existsByReservaId → false` before either inserts.
- **Failure scenario:** Two concurrent `POST /api/facturas` for the same `reservaId` → both pass the check → both insert → two PENDING invoices for one reservation. Billing reconciliation fails.
- **Files:** `factura/service/FacturaService.java`, `src/main/resources/db/migration/` (missing constraint)
- **Fix:** Add Flyway migration `V12__unique_factura_reserva_id.sql`:
  ```sql
  -- Ensures at most one invoice per reservation.
  ALTER TABLE factura
      ADD CONSTRAINT uk_factura_reserva_id UNIQUE (reserva_id);
  ```
  If orphan duplicates exist, clean them before applying: `DELETE FROM factura WHERE id NOT IN (SELECT MIN(id) FROM factura GROUP BY reserva_id);`
- **Why safe:** `DevelopmentDataSeeder` creates at most one factura per reservation. Tests reset data between runs.
- **Side effects:** Any future duplicate-insert attempt produces `DataIntegrityViolationException → GlobalExceptionHandler → 409`.
- **Effort:** 10 minutes.
- **Confidence:** CONFIRMED.

---

**H-6 — `DashboardResponse.totalGuests` contains total reservation count, not guest count**

- **Evidence:** `DashboardService.java`: `reservationRepository.count()` is passed as the first constructor argument. `DashboardResponse`: that parameter is named `totalGuests` and exposed as `getTotalGuests()`. `reservationRepository.count()` returns the number of booking rows, not the sum of `guest_count` across them.
- **Root cause:** Naming mismatch introduced when the dashboard was scaffolded.
- **Failure scenario:** A frontend widget labelled "Huéspedes totales" displays 42 where 42 is the number of reservations. Actual guests may be 87 (if each booking averages 2+ guests). Management makes capacity or marketing decisions from incorrect data.
- **Files:** `dashboard/service/DashboardService.java:27`, `dashboard/dto/DashboardResponse.java:13`
- **Fix — Option A (rename, no query change):** Rename `totalGuests` → `totalReservations` / `totalReservas` in both `DashboardResponse` and `DashboardService`. Coordinate with frontend.
- **Fix — Option B (real guest sum):** Add to `ReservationRepository`:
  ```java
  @Query("SELECT COALESCE(SUM(r.guestCount), 0) FROM Reservation r")
  Long sumGuestCount();
  ```
  Use it in `DashboardService` instead of `count()`.
- **Effort:** 10 minutes (Option A).
- **Confidence:** CONFIRMED.

---

### MEDIUM

---

**M-A — `GET /api/dashboard` has no role restriction (any authenticated user can access financial aggregates)**

- **Evidence:** `SecurityConfig` maps explicit role requirements to every `/api/anticipos/**`, `/api/facturas/**`, etc. endpoint. `/api/dashboard` falls through to `.anyRequest().authenticated()`. A user with any role can call it.
- **Root cause:** Dashboard endpoint was added after the role mappings were written and was not added to the explicit rules.
- **Failure scenario:** A `RECEPCIONISTA` (or any future low-privilege role) calls `GET /api/dashboard` and reads total invoice amounts, anticipo balance, and invoice status distribution — financial data they should not see.
- **Files:** `config/SecurityConfig.java`
- **Fix:** Before the catch-all:
  ```java
  .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAnyRole("ADMINISTRADOR", "CONTADOR")
  ```
  Also update `DashboardControllerTest` to `@WithMockUser(roles = {"ADMINISTRADOR"})`.
- **Effort:** 5 minutes.
- **Confidence:** CONFIRMED.

---

**M-B — `FacturaService.applyAnticipo()` does not validate `descuentoAnticipo` against `anticipo.getMonto()`**

- **Evidence:** `FacturaService.applyAnticipo()` marks the anticipo `APLICADO` (permanently preventing refund) but never verifies that `descuentoAnticipo` matches `anticipo.getMonto()`. Any positive value is accepted.
- **Root cause:** Missing cross-field validation between request and loaded entity.
- **Failure scenario:** POST `/api/facturas` with `anticipoId=5` (monto=1,000,000 COP) and `descuentoAnticipo=1.00`. The anticipo is burned (`APLICADO`); only 1 COP is deducted from the invoice. The remaining 999,999 COP is stranded permanently: the anticipo cannot be applied to another invoice (already APLICADO), and DevolucionService rejects it (not REGISTRADO).
- **Files:** `factura/service/FacturaService.java` (`applyAnticipo` private method)
- **Fix:** After loading anticipo in `applyAnticipo()`:
  ```java
  if (descuentoAnticipo.compareTo(anticipo.getMonto()) != 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "El descuento por anticipo debe coincidir exactamente con el monto del anticipo");
  }
  ```
  (Relax to `>` if partial application is intentional — confirm with stakeholders.)
- **Why safe:** `anticipo.getMonto()` is loaded in the same transaction. Seeded facturas all have matching amounts.
- **Effort:** 5 minutes.
- **Confidence:** CONFIRMED.

---

**M-C — `AnticipoRepository.sumTotalMonto()` aggregates all estados, overstating on-deposit balance**

- **Evidence:** `@Query("SELECT SUM(a.monto) FROM Anticipo a")` — no `WHERE` clause. `DashboardService` exposes this as `montoTotalAnticipos`.
- **Root cause:** Sum was written without a status filter; REGISTRADO + APLICADO + DEVUELTO are all included.
- **Failure scenario:** Dashboard shows 3,575,000 COP as "total anticipos." Management interprets this as the outstanding on-deposit balance. In reality, 2,225,000 COP has been applied to invoices (APLICADO) and another portion returned (DEVUELTO). The actual uncommitted balance is a fraction of what is displayed. Financial reporting based on this KPI is incorrect.
- **Files:** `anticipo/repository/AnticipoRepository.java`, `dashboard/service/DashboardService.java:35`
- **Fix:** Add a filtered query to `AnticipoRepository`:
  ```java
  @Query("SELECT COALESCE(SUM(a.monto), 0) FROM Anticipo a WHERE a.estado = :estado")
  BigDecimal sumMontoByEstado(@Param("estado") AnticipoEstado estado);
  ```
  In `DashboardService`:
  ```java
  BigDecimal montoTotalAnticipos = anticipoRepository.sumMontoByEstado(AnticipoEstado.REGISTRADO);
  ```
- **Why safe:** The original result is null-safe-guarded in the service; `COALESCE(SUM(...), 0)` eliminates the null in the query itself.
- **Side effects:** Dashboard value changes (decreases) in any environment with APLICADO/DEVUELTO anticipos — this is the correct change.
- **Effort:** 10 minutes.
- **Confidence:** CONFIRMED.

---

**M-D — No rate limiting on `POST /api/auth/login`**

- **Evidence:** `AuthController.login()` has no throttling, lockout, or CAPTCHA. The BCrypt timing equalization (~100ms per attempt) acts as a per-thread speed bump but does not limit concurrent requests.
- **Root cause:** No rate-limiting middleware configured in Spring Security or at the proxy layer.
- **Failure scenario:** An attacker opens 50 parallel connections and sends 50 login attempts per ~100ms batch, probing common passwords. Against accounts using weak or default passwords ("Admin2024!" seeded in V8), credential stuffing succeeds within minutes.
- **Files:** `auth/controller/AuthController.java`
- **Fix (minimal):** Add Bucket4j or a simple `@Component` tracking failed attempts per IP in a `ConcurrentHashMap<String, AtomicInteger>` with a scheduled reset. Account lockout after N failures (set `activo=false` temporarily) is more robust but risks locking out legitimate users on shared-NAT IPs.
- **Effort:** 2–4 hours.
- **Confidence:** CONFIRMED.

---

**M-E — `AuthController.login()` returns bare `LoginResponse` (REST consistency)**

- **Evidence:** `AuthController.login()` return type is `LoginResponse`. Every other controller method in the codebase wraps responses in `ResponseEntity<T>`.
- **Root cause:** Original scaffold left the auth controller unwrapped.
- **Files:** `auth/controller/AuthController.java`
- **Fix:** Change return type to `ResponseEntity<LoginResponse>` and return `ResponseEntity.ok(userService.login(request))`.
- **Effort:** 2 minutes.
- **Confidence:** CONFIRMED.

---

**M-F — `PenalidadService.create()` accepts client-submitted `montoSegunPolitica` without server verification**

- **Evidence:** `PenalidadRequest.montoSegunPolitica` is a client field. The service computes `maximoPenalidad` server-side to validate `montoAprobado` (correct), but stores whatever the client sent for `montoSegunPolitica` with no cross-check.
- **Root cause:** `montoSegunPolitica` should be derived server-side from `reserva.getMontoTotal() × (1 - política.getPorcentajeReembolso() / 100)`, not accepted from the caller.
- **Failure scenario:** Frontend developer accidentally sends `montoSegunPolitica = 0`. Auditor runs a compliance report; all penalty records show `montoSegunPolitica = 0`. The derived maximum was correct but the stored "according to policy" figure is garbage.
- **Files:** `penalidad/service/PenalidadService.java`, `penalidad/dto/PenalidadRequest.java`
- **Fix:** Remove `montoSegunPolitica` from `PenalidadRequest`. In the service, set it from the already-computed `maximoPenalidad`:
  ```java
  penalidad.setMontoSegunPolitica(maximoPenalidad);
  ```
  Breaking API change — coordinate with frontend before applying.
- **Effort:** 15 minutes (service) + frontend update.
- **Confidence:** CONFIRMED.

---

**M-G — `AdminUserInitializer` resolves the ADMINISTRADOR role by hardcoded ID `1`**

- **Evidence:** `config/AdminUserInitializer.java`: `ROL_ADMINISTRADOR_ID = 1`. Used in `rolRepository.findById(1)`.
- **Root cause:** Relies on PostgreSQL identity sequence starting at 1 and roles being seeded in a fixed order.
- **Failure scenario:** On a cluster where a previous partial migration left the sequence at a non-1 starting value, `rolRepository.findById(1)` returns `Optional.empty()`. Initializer logs "ADMINISTRADOR role not found — skipping" and exits silently. No admin account is created. The application starts with zero admin users.
- **Files:** `config/AdminUserInitializer.java`
- **Fix:** Look up by name instead:
  ```java
  // Add to RolRepository:
  Optional<Rol> findByNombreIgnoreCase(String nombre);

  // In AdminUserInitializer:
  Rol adminRol = rolRepository.findByNombreIgnoreCase("ADMINISTRADOR")
      .orElse(null);
  if (adminRol == null) { log.warn("..."); return; }
  ```
- **Effort:** 15 minutes.
- **Confidence:** CONFIRMED.

---

**M-H — `DashboardService.getSummary()` fires 10 separate SQL COUNT queries per request**

- **Evidence:** The method calls: `propertyRepository.count()`, `reservationRepository.count()`, `countByStatus(CONFIRMED)`, `countByStatus(CANCELLED)`, `countByStatus(COMPLETED)`, `facturaRepository.countByEstado(PENDING)`, `countByEstado(PAID)`, `countByEstado(CANCELLED)`, `anticipoRepository.count()`, `sumTotalMonto()` — 10 sequential queries in a single transaction.
- **Root cause:** Each Spring Data method issues one query; no aggregation optimization.
- **Files:** `dashboard/service/DashboardService.java`
- **Fix:** A single native query with conditional aggregates (`COUNT(CASE WHEN ... THEN 1 END)`) eliminates 9 round trips. Address after critical/high items.
- **Effort:** 1 hour.
- **Confidence:** CONFIRMED (query count); PLAUSIBLE (impact at current scale).

---

### LOW

---

**L-1 — `PoliticaCancelacion` delete returns generic "Conflicto de datos" with no user-actionable detail**

- **Evidence:** `PoliticaCancelacionService.eliminar()` relies on FK violation (`reserva.politica_cancelacion_id`) being caught by `GlobalExceptionHandler`, which returns `"Conflicto de datos"` for all `DataIntegrityViolationException`s.
- **Fix:** Check explicitly before deleting:
  ```java
  if (reservationRepository.existsByPoliticaCancelacionId(id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "No se puede eliminar la política: existen reservas que la referencian");
  }
  ```
  Add `existsByPoliticaCancelacionId(Integer id)` to `ReservationRepository`.
- **Effort:** 15 minutes.

---

**L-2 — `PoliticaCancelacionRepository.findByNombre()` is never called (dead method)**

- **Evidence:** No service or controller calls `findByNombre(String nombre)`. It creates API surface with no consumer.
- **Fix:** Remove the method. If nombre uniqueness is desired, add a service-layer check and a DB constraint.
- **Effort:** 2 minutes.

---

**L-3 — `temporada.nombre` has no DB-level UNIQUE constraint**

- **Evidence:** `DevelopmentDataSeeder.findOrCreateTemporada()` calls `temporadaRepository.findByNombre(nombre)` which returns `Optional<Temporada>`. If two temporadas share the same nombre (via a race or data corruption), Spring Data throws `IncorrectResultSizeDataAccessException` at the next seeder run. No Flyway migration adds a UNIQUE index on `temporada.nombre`.
- **Fix:** Add a Flyway migration:
  ```sql
  CREATE UNIQUE INDEX idx_temporada_nombre ON temporada (nombre);
  ```
- **Effort:** 5 minutes.

---

**L-4 — Dashboard counts all properties including soft-deleted ones (`activa=false`)**

- **Evidence:** `DashboardService.java`: `propertyRepository.count()` — no predicate; includes `activa=false` rows.
- **Fix:** Add `countByActivaTrue()` to `PropertyRepository` and use it.
- **Effort:** 5 minutes.

---

**L-5 — No test coverage for recently applied business-rule guards**

- **Evidence:** No test asserts that:
  - `POST /api/penalidades` for a CONFIRMED reservation returns 409 (M-10 fix)
  - `DELETE /api/notas-credito/{id}` for a PAID-factura-linked note returns 409 (M-1 fix)
  - Paginated responses include the `PageResponse` wrapper with `totalElements` / `totalPages` fields (M-3 fix)
  - `POST /api/devoluciones` with `monto > anticipo.monto` returns 400 (H-2 in this audit)
- **Risk:** Silent regression if any guard is accidentally removed. CI suite would not catch it.
- **Fix:** Add one negative-path integration test per guard. Each requires only a new `@Test` method within the existing `*ControllerTest` class, following the established `@WithMockUser` + `MockMvc` pattern.
- **Effort:** 1–2 hours total.

---

**L-6 — Broad `FetchType.EAGER` on all financial entities (latent N+1 risk)**

- **Evidence:** `Anticipo`, `Devolucion`, `Penalidad`, `NotaCredito`, `Factura`, and `Reservation` all declare `@ManyToOne(fetch = FetchType.EAGER)` for every association. A paginated `GET /api/anticipos?page=0&size=20` hydrates 20 anticipos → 20 reservations → each reservation eagerly loads Canal, Temporada, PoliticaCancelacion, and User → up to ~100 queries for a single page request.
- **Risk:** Invisible at class-project scale (<1,000 rows). In production with 50,000+ reservations, concurrent page requests will create DB connection pool pressure.
- **Fix:** Convert associations to `LAZY` and add `@EntityGraph` or `JOIN FETCH` in repository queries for endpoints that need the associations. Defer until critical/high items are resolved.
- **Effort:** 4–6 hours (systematic refactor across all entities).

---

**L-7 — `UserService.deleteUser()` does not guard against deleting users with active financial records**

- **Evidence:** `deleteUser()` sets `activo=false` with one guard: preventing self-deletion. No check against `anticipo`, `factura`, `nota_credito`, or `devolucion` records where `usuario_id` matches the target user.
- **Risk:** Low — soft-delete preserves referential integrity (rows remain, FK still resolves). Financial attribution is maintained via the JOIN. The main risk is auditing: records created during an active session by a user who is concurrently soft-deleted remain attributable. Not a data-integrity issue.
- **Fix:** Optional warning if the user has financial records created in the last 30 days. Not a blocker.

---

## Priority Fix Order

```
C-1  (double-refund race — lock anticipo row)
H-5  (V12 migration — UNIQUE on factura.reserva_id)
H-2  (cap devolucion.monto at anticipo.monto)
H-3  (guard negative factura total)
H-4  (anticipo status guard)
H-1  (NotaCredito cumulative cap)
H-6  (rename totalGuests or compute real sum)
M-A  (dashboard RBAC — one SecurityConfig line)
M-B  (validate descuentoAnticipo against anticipo.monto)
M-C  (sumTotalMonto filter by REGISTRADO)
M-F  (montoSegunPolitica server-side)
M-G  (AdminUserInitializer role lookup by name)
M-D  (rate limiting on login)
M-E  (AuthController ResponseEntity)
M-H  (dashboard query consolidation)
L-5  (test coverage for guards)
L-1  (PoliticaCancelacion delete error message)
L-3  (temporada UNIQUE index)
L-4  (dashboard active-only property count)
L-2  (dead findByNombre method)
L-7  (deleteUser financial guard, optional)
L-6  (LAZY loading refactor, deferred)
```

**Estimated effort to clear C-1 + all HIGH findings:** ~90 minutes.
**Estimated effort to clear all MEDIUM findings:** ~5 additional hours.
