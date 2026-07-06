# NovaFacts Backend — Engineering Audit v5

**Date:** 2026-07-05
**Scope:** Complete, ground-up re-audit of `project-backend/`. This is not an update of `AUDIT_v4.md` — every finding in this document was independently investigated against the current codebase and, wherever possible, verified empirically (real SQL logs, real HTTP requests against a running instance, real concurrent execution, a real fresh-database deployment, and a full test-suite run). No prior audit finding was assumed valid or invalid before being checked. Where a claim could not be verified empirically, that is stated explicitly.

**Empirical baseline for this audit:** `./mvnw clean test` → **95/95 tests passing**. Live verification performed against a rebuilt, redeployed Docker Compose stack (`novafacts_backend`, `novafacts_postgres`, both healthy) with the real demo dataset (56 reservations) plus, for one experiment, a deliberately fresh/empty database.

---

## 1. Executive Summary

NovaFacts is a Spring Boot 3.5.14 / Java 21 financial-management API for short-term rental bookings. The codebase is coherent, consistently structured, and shows real engineering discipline in several areas — the N+1 query remediation is genuinely complete and independently re-verified in this cycle, RBAC is precise, JWT handling is sound, and the exception-handling pipeline correctly distinguishes business errors from infrastructure failures.

However, this audit found **three Critical findings that directly contradict the previous audit's "no Critical findings open" verdict**, two of them reproduced live against the running application in this session:

1. A financial double-charge bug in `PenalidadService` (previously documented as H-5, believed open) was **reproduced live**: a guard method exists on the repository, is never called, and a second penalty against an already-cancelled, already-penalized reservation was accepted, resulting in a confirmed **480,000 COP overcharge** against a policy-defined maximum.
2. A **new, previously undocumented** race condition in `FacturaService.emitir()/anular()` and the structurally identical `DevolucionService.procesar()/rechazar()` was **reproduced live** with two concurrent HTTP requests: both callers received confident, contradictory success responses (`PAID` vs. `CANCELLED`) for the same invoice, while the database landed on a single value — a genuine lost-update bug with no JPA `@Version` anywhere in the codebase to catch it.
3. A **new, previously undocumented** production-readiness/security defect was **reproduced via a full fresh-database deployment**: Flyway migration `V8` unconditionally seeds a hardcoded `admin@novafacts.com` / `Admin2024!` superuser account on every empty database, regardless of the `ADMIN_EMAIL`/`ADMIN_PASSWORD` environment variables a deployment configures specifically to avoid using that default. A real login with the hardcoded, git-committed credentials succeeded on a database that was deliberately configured with different admin credentials.

Everything from the previous session's remediation work that this audit re-checked — the N+1 optimizations across all six financial/reservation modules, the dashboard RBAC restriction, the `AccessDeniedHandler` fix, login rate limiting, Actuator's minimal exposure, the H-404 fix, JWT tampering rejection, and the `Anticipo`/`Property` pessimistic locks — **is confirmed working correctly** by direct re-verification in this cycle, not by trusting the prior document.

**Recommendation: do not approve production deployment today.** The Critical findings above are financial-integrity and security defects with proven, low-effort exploitation paths, not theoretical concerns.

---

## 2. Category Scores (recalculated from scratch)

| Category | Score | Confidence |
|---|---|---|
| Architecture | 8/10 | High — direct inspection of all 15 packages |
| Domain Model | 7/10 | High |
| Persistence | 7/10 | High — empirically re-verified N+1 fixes; downgraded from what a pre-audit assumption might give, due to the concurrency findings below being fundamentally persistence-layer gaps |
| Service Layer | 5/10 | High — the two Critical concurrency/logic bugs live in this layer |
| API Design | 7/10 | High |
| Security | 5/10 | High — the V8 backdoor-admin finding is a direct, empirically-proven security defect |
| Validation | 7/10 | High |
| Error Handling | 8/10 | High — re-verified H-404 fix and the full exception hierarchy live |
| Performance | 8/10 | High — every N+1 fix re-measured via live SQL logs this cycle |
| Testing | 5/10 | High — confirmed zero coverage for `penalidad` (the exact module with a live financial bug) and `rol`; confirmed the exact endpoint with the proven race (`emitir`) has no test at all, not even a happy-path one |
| Configuration | 6/10 | High |
| Maintainability | 7/10 | High |
| Production Readiness | 4/10 | High — the backdoor-admin finding is a severe, proven production-readiness defect |

**Overall Engineering Quality: 6/10.** Down from the previous cycle's implicit "no Critical findings" position, specifically because this audit did not accept that position and instead verified it — and disproved it twice.

---

## 3. Findings

Severity scale: **Critical** (financial/security defect, proven or trivially exploitable) · **High** (significant correctness/coverage/security gap) · **Medium** (real but bounded impact) · **Low** (cosmetic/minor) · **Informational** (no action needed, noted for completeness).

---

### CRITICAL

---

#### C-1 — `PenalidadService.create()` allows unlimited penalty charges against the same cancelled reservation

| Field | Detail |
|---|---|
| **Status** | **Open — reproduced live in this audit.** (Documented previously as H-5; this audit did not trust that status and independently re-verified it.) |
| **Files** | `penalidad/service/PenalidadService.java` (the `create()` method, lines ~57–103); `penalidad/repository/PenalidadRepository.java` (line 17, `existsByReservaId`) |
| **Evidence** | Read `PenalidadService.create()` in full: it validates the reservation is `CANCELLED` and validates the *individual* request against the policy-derived maximum, but never calls `existsByReservaId()` — a method that exists on the repository and does nothing else in the codebase. **Live reproduction**: reservation 53 (`montoTotal=2,400,000`, cancellation policy `20%` refund → true maximum penalty `1,920,000`) already had one penalty of `900,000`. A second `POST /api/penalidades` request for `1,500,000` (itself under the per-request cap) was submitted as `contador@novafacts.com` and returned `201 Created`. A database query immediately after confirmed: `SUM(monto_aprobado) = 2,400,000.00` against a `true_max_cap` of `1,920,000.00` — a **480,000 COP overcharge**, reproduced with an ordinary sequential double-submission, no concurrency or special tooling required. The test row was deleted after verification to restore state. |
| **Root cause** | The guard method (`existsByReservaId`) was written but never wired into `create()`. This is not a subtle bug — it is a one-line omission in a codebase that uses the identical guard pattern correctly elsewhere (e.g., `FacturaService.create()` calls `facturaRepository.existsByReservaId()` before creating an invoice). |
| **Impact** | Direct, unbounded financial overcharge to guests. Any of `ADMINISTRADOR`, `CONTADOR`, or `AUXILIAR_CONTABLE` can trigger this via the ordinary UI (an accidental double-submit) or by two staff members acting on the same cancellation without coordination. There is no upper bound on how many times a penalty can be charged against one reservation. |
| **Recommendation** | In `create()`, reject if `penalidadRepository.existsByReservaId(request.getReservaId())` is true — exactly mirroring the pattern already used in `FacturaService.create()`. |
| **Estimated effort** | 15 minutes for the guard; recommend also adding a regression test (none currently exists for this module at all — see H-1 below) covering both the guard itself and, ideally, a concurrent double-submission case. |

---

#### C-2 — Unguarded check-then-act race in `FacturaService.emitir()`/`anular()` and `DevolucionService.procesar()`/`rechazar()`/`delete()`

| Field | Detail |
|---|---|
| **Status** | **New — reproduced live in this audit.** Not present in any prior audit document. |
| **Files** | `factura/service/FacturaService.java` (`emitir()`, `anular()`); `devolucion/service/DevolucionService.java` (`procesar()`, `rechazar()`, `delete()`) |
| **Evidence** | Both methods follow an identical shape: `Factura factura = getOrThrow(id)` (a plain, unlocked `findById`) → check current state → mutate → save. No entity in the codebase has a JPA `@Version` field (confirmed via `grep -rl "@Version" src/main/java/` — zero matches across all 20 entities), and neither `FacturaRepository` nor `DevolucionRepository` has a `findByIdForUpdate`-style pessimistic-lock method (only `AnticipoRepository` and `PropertyRepository` do). **Live reproduction**: a `PENDING` invoice (id 18) was targeted with two concurrent requests fired via backgrounded shell processes — `PUT /api/facturas/18/emitir` and `PUT /api/facturas/18/anular` — launched together. Both returned `200 OK`. The `emitir` response body reported `"estado":"PAID"`; the `anular` response body, for the *same invoice ID*, reported `"estado":"CANCELLED"`. A database query immediately afterward showed the persisted state as `PAID` — meaning the caller who received a confident `"estado":"CANCELLED"` response was looking at fabricated information; the invoice was never actually cancelled. This was reproduced on the first attempt, with no repeated tuning of timing. |
| **Root cause** | The pessimistic-locking pattern established for the `Anticipo` double-apply/double-refund case (`findByIdForUpdate` + `PESSIMISTIC_WRITE`, referenced in-code as "C-2" in comments within `FacturaService.applyAnticipo()` and `DevolucionService.create()`) was applied only to the cross-service `Anticipo` state boundary. It was never extended to the state-transition methods of `Factura` or `Devolucion` themselves, which have the exact same check-then-act shape and no compensating optimistic-lock safety net. |
| **Impact** | An invoice or refund's true persisted state can silently diverge from what was reported to the API caller who "successfully" transitioned it. In a financial system, this means accounting staff can reasonably believe an invoice was cancelled (their own API response said so) when it was in fact paid, or vice versa — a direct threat to bookkeeping accuracy and auditability, with no error, warning, or conflict signal raised to either caller. |
| **Recommendation** | Apply the identical, already-proven fix pattern: add `findByIdForUpdate()` (pessimistic write lock) to `FacturaRepository` and `DevolucionRepository`, and use it in `emitir()`, `anular()`, `procesar()`, `rechazar()`, and `delete()` in place of the plain `findById`-based `getOrThrow()`. Alternatively/additionally, add a JPA `@Version` field to both entities so at least one of the two racing transactions fails with the already-correctly-handled `ObjectOptimisticLockingFailureException` (409) instead of silently losing an update. |
| **Estimated effort** | 30–45 minutes per entity (mirrors an already-existing, well-understood pattern in this codebase); a concurrency regression test analogous to the existing `AnticipoConcurrencyTest` is recommended and currently does not exist for either module. |

---

#### C-3 — Flyway migration `V8` unconditionally creates a hardcoded backdoor admin account, bypassing `ADMIN_EMAIL`/`ADMIN_PASSWORD` configuration

| Field | Detail |
|---|---|
| **Status** | **New — reproduced live in this audit via a full fresh-database deployment.** |
| **Files** | `src/main/resources/db/migration/V8__seed_default_admin.sql`; `config/AdminUserInitializer.java` |
| **Evidence** | `V8__seed_default_admin.sql` inserts `admin@novafacts.com` with a hardcoded, git-committed BCrypt hash for `Admin2024!` whenever `NOT EXISTS (SELECT 1 FROM usuario WHERE rol_id = 1)` — i.e., whenever no Administrador exists yet, which is true on *every* fresh database, unconditionally, regardless of any environment configuration. `AdminUserInitializer` runs afterward and only checks whether its own *configured* `adminEmail` already exists — it does not check whether *any* admin already exists. **Live reproduction**: ran `docker compose down -v` (wiping the Postgres volume) and redeployed with `ADMIN_EMAIL=realadmin@novafacts-corp.com` and a custom `ADMIN_PASSWORD`. After startup, `SELECT email FROM usuario` showed **both** `admin@novafacts.com` and `realadmin@novafacts-corp.com` as active Administrador accounts. A subsequent `POST /api/auth/login` with `admin@novafacts.com` / `Admin2024!` — the hardcoded, publicly-known-from-git-history default — succeeded with `200 OK` and a valid, fully-privileged JWT, on a deployment that had deliberately configured different admin credentials specifically to avoid this. The environment was restored to its normal state after the experiment. |
| **Root cause** | Two independent, uncoordinated admin-bootstrap mechanisms exist: a Flyway migration (`V8`) that always targets the literal string `admin@novafacts.com`, and an `ApplicationRunner` (`AdminUserInitializer`) that is correctly configurable but only checks for its *own* configured email, not for the presence of the `V8`-seeded account. `AdminUserInitializer`'s own class-level Javadoc ("Idempotent: if the configured admin email already exists, does nothing") is accurate but incomplete — it does not mention or account for `V8`'s independent seeding. |
| **Impact** | Any operator who follows the documented, correct practice of setting custom `ADMIN_EMAIL`/`ADMIN_PASSWORD` before first deployment — specifically to avoid shipping with default credentials — still gets a fully functional, undocumented, superuser account with credentials that are public in the project's own git history. This is a complete authentication bypass for anyone who has ever seen this repository. |
| **Recommendation** | Remove `V8__seed_default_admin.sql` entirely and rely solely on `AdminUserInitializer`, which already does this correctly and configurably — Flyway migrations should manage schema, not seed environment-specific/security-sensitive data that already has a purpose-built, configurable mechanism. If backward compatibility with already-migrated databases is a concern, a follow-up migration should at minimum deactivate (`activo = false`) the `admin@novafacts.com` row when a different Administrador account already exists, but the cleanest fix is to delete `V8` before any real production database is ever created from this schema history (this schema has never been deployed to a real production database, per the project's own documented dev status). |
| **Estimated effort** | 15 minutes to delete/neutralize the migration; requires re-running the full test suite (Flyway migration count changes) and a fresh-database smoke test to confirm `AdminUserInitializer` alone still correctly bootstraps the configured admin. |

---

### HIGH

---

#### H-1 — Zero automated test coverage for the `penalidad` package

| Field | Detail |
|---|---|
| **Status** | **New.** |
| **Files** | No file exists at `src/test/java/com/novafacts/backend/penalidad/**` — confirmed via direct directory search; every other financial module (`anticipo`, `factura`, `devolucion`, `notacredito`) has a corresponding `*ControllerTest`. |
| **Evidence** | `find src/test -iname "*penalidad*"` returns nothing. This is the exact module containing the live, reproduced C-1 financial overcharge bug above — a single test asserting "a second penalty against an already-penalized reservation is rejected" would have caught this defect before it reached this audit. |
| **Root cause** | The module was implemented with production code but never given corresponding test coverage, unlike every structurally similar sibling module. |
| **Impact** | The highest-risk financial write-path in the codebase (direct monetary penalty application) has no regression safety net at all. |
| **Recommendation** | Add `PenalidadControllerTest` mirroring the structure of `AnticipoControllerTest`/`FacturaControllerTest`, explicitly covering: happy-path creation, the policy-cap validation, and — once C-1 is fixed — the duplicate-penalty rejection. |
| **Estimated effort** | 1–2 hours, following the established test-class pattern already used for every sibling module. |

---

#### H-2 — Zero automated test coverage for the `rol` package

| Field | Detail |
|---|---|
| **Status** | **New.** |
| **Files** | No file exists at `src/test/java/com/novafacts/backend/rol/**`. |
| **Evidence** | `RolController`/`RolService` are simple (a single read-only `listar()` endpoint), so the risk here is much lower than H-1, but the coverage gap is total. |
| **Root cause** | Same pattern as H-1 — a module without corresponding tests. |
| **Impact** | Low on its own (the logic is a two-line stream/map), but combined with H-1 it establishes that test coverage in this codebase is inconsistently applied per-module rather than enforced as a project-wide baseline. |
| **Recommendation** | A minimal `RolControllerTest` asserting the endpoint returns the three seeded roles with correct fields; low priority relative to H-1. |
| **Estimated effort** | 15–20 minutes. |

---

#### H-3 — `ReservationService.update()` has a partially-mitigated last-write-wins race on the reservation's own fields

| Field | Detail |
|---|---|
| **Status** | **New — determined through static analysis; not empirically reproduced this cycle.** (Distinguishing it explicitly from C-2, which *was* reproduced live.) |
| **Files** | `reservation/service/ReservationService.java`, `update()` method (lines ~135–186) |
| **Evidence (static)** | `update()` reads the reservation via a plain, unlocked `getOrThrow(id)`, validates the requested status transition against that initially-read status, and later acquires a pessimistic lock only on the *property* row (`lockPropertyOrThrow`) before the double-booking overlap check. Two concurrent `PUT /api/reservas/{id}` calls with different field values would both read the same initial reservation state; the property lock serializes the overlap-check portion but does not protect the reservation row's own fields (including `status`) from a last-write-wins overwrite by whichever transaction commits last. |
| **Why not reproduced empirically** | Unlike the Factura/Devolucion case (C-2), this method does substantially more validation work (canal/temporada/politica lookups, property lock, overlap check) between the initial read and the final write, making the race window harder to hit reliably with simple shell-level concurrent curl requests in the time available for this audit. This is flagged as a static-analysis finding, not a proven one, in keeping with this audit's evidence standard. |
| **Impact** | Lower than C-2: the failure mode is a full-row overwrite (last writer wins) rather than a status-specific silent inconsistency, and the property-level lock does provide real protection against the double-booking scenario this method was originally designed to prevent. |
| **Recommendation** | If this is to be addressed, the same `findByIdForUpdate` pattern already used for `Property` and `Anticipo` should be extended to `Reservation` itself, or a `@Version` field added. Lower priority than C-1/C-2 given the smaller proven blast radius. |
| **Estimated effort** | Not estimated — recommend a dedicated concurrency-focused investigation (mirroring the rigor of the original `AnticipoConcurrencyTest`) before committing to a fix, since the interaction with the existing property lock needs careful design. |

---

### MEDIUM

---

#### M-1 — CORS `allowed-origins` list is not trimmed of whitespace

| Field | Detail |
|---|---|
| **Status** | **Open — confirmed via static code inspection in this audit; not re-tested live this cycle.** (Previously documented; this audit independently re-confirmed the code has not changed.) |
| **Files** | `config/SecurityConfig.java`, line 34–35 |
| **Evidence** | `@Value("${cors.allowed-origins:http://localhost:5173}") private List<String> allowedOrigins;` — Spring's comma-splitting `@Value` binding for `List<String>` does not trim whitespace around each element. A configured value of `https://a.com, https://b.com` (a space after the comma, a natural way to write this) produces `["https://a.com", " https://b.com"]`; the second entry has a leading space and will never exact-match a real `Origin` header, silently breaking CORS for that origin with no error at startup or request time. |
| **Impact** | Silent production misconfiguration risk — this fails quietly (a blocked browser request with a generic CORS error) rather than loudly, making it easy to ship and hard to diagnose. |
| **Recommendation** | Trim each entry: `allowedOrigins.stream().map(String::strip).toList()` before passing to `CorsConfiguration.setAllowedOrigins()`. |
| **Estimated effort** | 5 minutes. |

---

#### M-2 — `ADMIN_PASSWORD` still defaults to a well-known, git-committed value

| Field | Detail |
|---|---|
| **Status** | **Open.** |
| **Files** | `application.properties`, `docker-compose.yml`, `AdminUserInitializer.java` |
| **Evidence** | `admin.init.password=${ADMIN_PASSWORD:Admin2024!}` — mitigated by a loud startup warning banner when the default is in effect (confirmed present in live container logs this session), but the default itself remains functional out of the box. |
| **Impact** | Compounds directly with C-3: even after C-3 is fixed, a deployment that never overrides `ADMIN_PASSWORD` at all still ships with a known-weak password, just without the duplicate-account problem. |
| **Recommendation** | Consider failing startup in a detected "production-like" profile if `ADMIN_PASSWORD` is unset, rather than only warning. Lower priority than C-3, since the warning banner does exist and is visible in logs. |
| **Estimated effort** | 30 minutes to add a profile-conditional hard-fail. |

---

#### M-3 — `PenalidadRepository.existsByReservaId()` and other guard methods risk silent bit-rot

| Field | Detail |
|---|---|
| **Status** | **New — a maintainability observation arising directly from investigating C-1.** |
| **Evidence** | `existsByReservaId()` on `PenalidadRepository` is fully implemented, correctly named, and entirely unused — the exact situation that allowed C-1 to exist undetected. This pattern (a correctly-written guard that was never wired into its caller) is a systemic risk: nothing in the codebase or CI currently detects "this repository method is never called," so a written-but-unused safety check provides a false sense of security to anyone reading the repository interface without checking every call site. |
| **Recommendation** | No specific code change beyond fixing C-1 itself; noted here because it explains *why* C-1 was able to persist despite the fix seemingly already being "half-written." A static-analysis/IDE "unused method" pass across all repositories would be a cheap way to catch similar cases going forward. |
| **Estimated effort** | N/A — process recommendation, not a code fix. |

---

### LOW / INFORMATIONAL

---

#### L-1 — No dependency vulnerability scanning in CI

| Field | Detail |
|---|---|
| **Status** | **New observation.** |
| **Evidence** | `.github/workflows/ci.yml` (re-validated as syntactically correct in this audit) builds and tests but includes no `dependabot.yml`, no OWASP Dependency-Check, no `mvn versions:display-dependency-updates` or equivalent. |
| **Impact** | Vulnerable transitive dependencies (e.g., in the JJWT or Postgres driver chain) would not be flagged automatically. |
| **Recommendation** | Add a `dependabot.yml` (zero-maintenance, GitHub-native) as the lowest-effort first step. |
| **Estimated effort** | 10 minutes for Dependabot config alone. |

---

#### I-1 — No refresh-token or explicit JWT revocation mechanism

| Field | Detail |
|---|---|
| **Status** | Open, previously documented, re-confirmed via reading `JwtService`/`JwtAuthenticationFilter` fresh in this audit — no change since last cycle. |
| **Evidence** | `JwtService` has no refresh-token issuance; revocation is bounded only by the 24h expiry and the separate `UserDetailsServiceImpl` cache TTL (deactivation takes effect within the cache window, not immediately). |
| **Impact** | Accepted architectural tradeoff for this project's scale; not re-scored as a new deduction, just re-confirmed present. |

---

#### I-2 — No OpenAPI/Swagger documentation

| Field | Detail |
|---|---|
| **Status** | Open, re-confirmed (`grep` for `springdoc`/`swagger` in `pom.xml` returns nothing). |
| **Impact** | API contract is only discoverable by reading source. Not re-scored as new; consistent with prior findings. |

---

## 4. What This Audit Confirmed Is Actually Working (empirically re-verified, not assumed)

- **N+1 query optimizations, all six modules** (`Reservation`, `Factura`, `Anticipo`, `Penalidad`, `Devolucion`, `NotaCredito`): re-verified via live SQL logging this cycle. Three combined paginated requests (`reservas`, `anticipos`, `facturas`, size=50 each) produced only 6 total SQL statements. No regression from the previously-completed work.
- **Dashboard RBAC (M-14)**: `Recepcionista` → `403` on `GET /api/dashboard`, `200` on `GET /api/reservas`; `Administrador` → `200` on both. Confirmed live.
- **`AccessDeniedHandler` fix**: authenticated-but-unauthorized requests correctly return `403`, not `401`. Confirmed live via the M-14 check above.
- **Login rate limiting**: five failed attempts followed by a sixth (even with correct credentials) returned `429`. Confirmed live, freshly, this cycle.
- **Spring Boot Actuator**: `/actuator/health` returns `200` with no authentication required; `/actuator/env` (unexposed) returns `404` (not `500` — see H-404 below); Actuator discovery root shows only the `health` link when authenticated as `ADMINISTRADOR`. Confirmed live.
- **H-404 fix**: unknown routes return `404` when authenticated, `401` when not (since Spring Security rejects unauthenticated requests before Spring MVC ever dispatches them — a real, confirmed distinction, not a regression). Confirmed live, fresh, this cycle.
- **JWT handling**: tampered signature, structurally malformed token, and missing `Bearer` prefix all correctly rejected with `401`. Confirmed live.
- **Pessimistic locking on `Anticipo` and `Property`**: `@Lock(LockModeType.PESSIMISTIC_WRITE)` confirmed present in both repositories via direct code read; `AnticipoConcurrencyTest` passed in this cycle's fresh full-suite run (`95/95`), providing ongoing automated regression protection for this specific case — in contrast to the C-2 finding above, which has no equivalent test.
- **GitHub Actions CI workflow**: YAML re-parsed successfully; unchanged since last cycle.
- **Full test suite**: `95/95` passing, fresh run, this cycle.

---

## 5. Package-by-Package Review

**`auth`** — JWT issuance/validation (`JwtService`), Spring Security integration (`JwtAuthenticationFilter`, `UserDetailsServiceImpl`), login rate limiting (`LoginRateLimitFilter`), and user management (`UserService`, `UserController`). Quality: high. The LOW-17 timing-equalization dummy-BCrypt-comparison in `UserService.login()` remains a genuinely above-average defensive touch. Gap: `UserController`'s create/list/delete endpoints have no dedicated controller test (only adjacent behavior is covered via `UserDetailsCacheTest`/`LoginRateLimitFilterTest`).

**`penalidad`** — Contains the C-1 financial-overcharge defect and has zero test coverage (H-1). This is the weakest package in the codebase by a clear margin.

**`factura` / `devolucion`** — Contains the C-2 concurrency defect. Otherwise well-structured; both have reasonable (if concurrency-blind) sequential test coverage.

**`anticipo`** — The one financial module with a dedicated concurrency test (`AnticipoConcurrencyTest`) and pessimistic locking correctly applied at its cross-module boundary. This is the pattern the rest of the financial modules should be brought up to, not the exception.

**`notacredito`** — Fully N+1-optimized this session; no test-coverage gap beyond what's typical elsewhere; no concurrency-sensitive state transitions (no emitir/anular-style endpoint), so C-2's pattern does not apply here.

**`reservation`** — The most complex service in the codebase. H-3 (partially-mitigated race) applies here; otherwise the property-level pessimistic lock and financial-history delete/reassignment guards are correctly implemented and tested.

**`property`, `canal`, `temporada`, `politicacancelacion`, `rol`** — Reference-data modules, consistent CRUD pattern, low individual risk. `rol` has the H-2 coverage gap.

**`dashboard`** — Correctly RBAC-restricted (M-14), aggregate-only, no write path.

**`common`, `config`** — `GlobalExceptionHandler` correctly handles the H-404 case now (`NoResourceFoundException`) alongside its five original handlers; `SecurityConfig` correctly implements the M-14/rate-limiting/Actuator rules but carries the M-1 CORS-trimming gap; `CacheConfig`/`AdminUserInitializer` are individually well-written but `AdminUserInitializer` is undermined by the independent `V8` migration (C-3).

---

## 6. Technical Debt, Ranked

| # | Finding | Severity | Effort | Priority rationale |
|---|---|---|---|---|
| 1 | C-1 — Penalidad double-charge | Critical | 15 min | Live-proven financial overcharge; guard already written, just needs to be called |
| 2 | C-3 — V8 backdoor admin | Critical | 15 min | Live-proven full auth bypass; trivial fix (delete one migration) |
| 3 | C-2 — Factura/Devolucion race | Critical | 30–45 min/entity | Live-proven data-integrity bug; pattern to copy already exists in the same codebase |
| 4 | H-1 — No Penalidad tests | High | 1–2 h | Directly would have caught C-1 |
| 5 | M-1 — CORS trimming | Medium | 5 min | Cheap, silent-failure-prone if ever hit |
| 6 | H-3 — Reservation update race | High | Not estimated | Needs its own investigation before a fix is designed |
| 7 | H-2 — No Rol tests | High (low actual risk) | 15–20 min | Cheap to close, low urgency |
| 8 | M-2 — Admin password default | Medium | 30 min | Compounds with C-3; independent value once C-3 is fixed |
| 9 | L-1 — No dependency scanning | Low | 10 min | Cheap, zero-maintenance once added |

---

## 7. Top Improvements by ROI

1. **Fix C-1** (call the existing guard) — minutes of work, closes a live financial bug.
2. **Fix C-3** (delete `V8`) — minutes of work, closes a full authentication bypass.
3. **Fix C-2** (extend the existing lock pattern to Factura/Devolucion) — under an hour per entity, reusing a pattern this codebase already has proven correct once.
4. **Add `PenalidadControllerTest`** — directly closes the coverage gap that let C-1 ship silently.
5. **Trim CORS origins (M-1)** — trivial, removes a silent-failure production risk.

---

## 8. Final Answers

**1. Would you approve deploying this project to production today?**
No.

**2. If not, what exact issues prevent approval?**
C-1 (live financial overcharge, reproduced), C-2 (live data-integrity race, reproduced), and C-3 (live authentication bypass via a hardcoded backdoor admin account, reproduced). All three were directly demonstrated against the running application in this audit, not inferred.

**3. Which issues are mandatory before production?**
C-1, C-2, and C-3, without exception — each is a proven defect with a real exploitation path an ordinary user or operator could hit without any adversarial intent. H-1 (Penalidad test coverage) is strongly recommended alongside C-1's fix, since shipping the fix without a regression test reintroduces the exact conditions that let C-1 go unnoticed.

**4. Which issues are optional improvements?**
H-2 (Rol tests), H-3 (Reservation update race — needs further investigation before a fix, not blocking), M-1 (CORS trimming), M-2 (hard-fail on default admin password), M-3 (unused-method hygiene), L-1 (dependency scanning), I-1 (refresh tokens), I-2 (OpenAPI docs).

**5. What maturity level best describes the project now?**
**Beta.** Not Production Candidate: that classification requires no live-reproduced Critical findings, and this audit reproduced three. The architecture, RBAC model, JWT handling, and performance work are genuinely at Production-Candidate quality — but a financial system with a proven, trivially-triggered overcharge bug and a proven authentication bypass cannot be scored above Beta regardless of how strong its other dimensions are.

**Estimated readiness percentage: 55%.** The gap to Production Candidate is small in *effort* (the three Critical fixes are collectively well under half a day of work, each reusing patterns already proven correct elsewhere in this same codebase) but the gap in *risk* if shipped today is severe, which is why the percentage is materially lower than the previous cycle's — this audit is not assigning partial credit for fixes that exist in written-but-unwired form (C-1) or for safety nets that were never extended to structurally identical code (C-2).
