package com.novafacts.backend.config;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.entity.AnticipoEstado;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.entity.Devolucion;
import com.novafacts.backend.devolucion.entity.DevolucionEstado;
import com.novafacts.backend.devolucion.repository.DevolucionRepository;
import com.novafacts.backend.factura.entity.Factura;
import com.novafacts.backend.factura.repository.FacturaRepository;
import com.novafacts.backend.invoice.entity.InvoiceStatus;
import com.novafacts.backend.penalidad.entity.Penalidad;
import com.novafacts.backend.penalidad.repository.PenalidadRepository;
import com.novafacts.backend.politicacancelacion.entity.PoliticaCancelacion;
import com.novafacts.backend.politicacancelacion.repository.PoliticaCancelacionRepository;
import com.novafacts.backend.property.entity.Property;
import com.novafacts.backend.property.repository.PropertyRepository;
import com.novafacts.backend.reservation.entity.Reservation;
import com.novafacts.backend.reservation.entity.ReservationStatus;
import com.novafacts.backend.reservation.repository.ReservationRepository;
import com.novafacts.backend.rol.entity.Rol;
import com.novafacts.backend.rol.repository.RolRepository;
import com.novafacts.backend.temporada.entity.Temporada;
import com.novafacts.backend.temporada.repository.TemporadaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Populates the database with a coherent demo dataset when the "dev" Spring profile is
 * active and app.seed-demo-data=true (the default in application-dev.properties).
 *
 * This dataset represents NovaFacts as a fictional company that has been managing
 * short-term rental properties across Colombia for about a year and a half: 16
 * properties in 9 cities, a full year-plus of seasons, per-property cancellation
 * policies, and 56 reservations with a rich, internally-consistent financial history
 * (advances, invoices, penalties, refunds) — built for the project's final demo, not
 * as randomized filler data.
 *
 * Design rules:
 *  - Each entity group is seeded independently via a find-or-create pattern.
 *  - Restarting the application never creates duplicates — all seed operations are idempotent.
 *  - All data is hand-authored and deterministic — no Random, no UUID-based content, no
 *    system-clock-relative values, so re-running always produces the exact same dataset.
 *  - Reservation + financial records are grouped by BUSINESS SCENARIO (not by entity type),
 *    so each scenario method creates a reservation together with the exact financial trail
 *    that scenario implies — this keeps AnticipoEstado/DevolucionEstado/InvoiceStatus
 *    mutually consistent by construction instead of relying on separate phases staying in sync.
 *  - This class is strictly limited to demo data. It does NOT bootstrap the admin account;
 *    that responsibility belongs to AdminUserInitializer.
 *  - Runs after AdminUserInitializer (which has order LOWEST_PRECEDENCE - 10).
 */
@Component
@Profile("dev")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DevelopmentDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentDataSeeder.class);

    @Value("${app.seed-demo-data:true}")
    private boolean seedDemoData;

    private final UserRepository                 userRepository;
    private final RolRepository                  rolRepository;
    private final CanalRepository                canalRepository;
    private final TemporadaRepository            temporadaRepository;
    private final PropertyRepository             propertyRepository;
    private final PoliticaCancelacionRepository  politicaRepository;
    private final ReservationRepository          reservationRepository;
    private final AnticipoRepository             anticipoRepository;
    private final PenalidadRepository            penalidadRepository;
    private final FacturaRepository              facturaRepository;
    private final DevolucionRepository           devolucionRepository;
    private final PasswordEncoder                passwordEncoder;

    public DevelopmentDataSeeder(
            UserRepository userRepository,
            RolRepository rolRepository,
            CanalRepository canalRepository,
            TemporadaRepository temporadaRepository,
            PropertyRepository propertyRepository,
            PoliticaCancelacionRepository politicaRepository,
            ReservationRepository reservationRepository,
            AnticipoRepository anticipoRepository,
            PenalidadRepository penalidadRepository,
            FacturaRepository facturaRepository,
            DevolucionRepository devolucionRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository       = userRepository;
        this.rolRepository        = rolRepository;
        this.canalRepository      = canalRepository;
        this.temporadaRepository  = temporadaRepository;
        this.propertyRepository   = propertyRepository;
        this.politicaRepository   = politicaRepository;
        this.reservationRepository = reservationRepository;
        this.anticipoRepository   = anticipoRepository;
        this.penalidadRepository  = penalidadRepository;
        this.facturaRepository    = facturaRepository;
        this.devolucionRepository = devolucionRepository;
        this.passwordEncoder      = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedDemoData) {
            log.info("DevelopmentDataSeeder: deshabilitado (app.seed-demo-data=false).");
            return;
        }

        // Canales are seeded by Flyway V3 and are not owned by this seeder.
        List<Canal> canales = canalRepository.findAll();
        if (canales.size() < 5) {
            log.warn("DevelopmentDataSeeder: se esperaban 5 canales de Flyway V3 pero se encontraron {}. Saltando siembra.", canales.size());
            return;
        }

        log.info("DevelopmentDataSeeder: verificando y sembrando el dataset de demostración...");

        Map<String, User>                users      = seedUsers();
        Map<String, Temporada>           seasons    = seedSeasons();
        Map<String, Property>            properties = seedProperties();
        Map<String, PoliticaCancelacion> policies   = seedPolicies(properties);

        ScenarioContext ctx = new ScenarioContext(canales, seasons, policies, users, properties);

        int completedPaid       = seedCompletedPaidReservations(ctx);
        int completedPending    = seedCompletedPendingInvoiceReservations(ctx);
        int completedNoRecords  = seedCompletedWithoutFinancialRecords(ctx);
        int confirmedWithDeposit = seedConfirmedWithDepositReservations(ctx);
        int confirmedNoDeposit  = seedConfirmedWithoutDepositReservations(ctx);
        int cancelledOnTime     = seedCancelledOnTimeReservations(ctx);
        int cancelledLateFull   = seedCancelledLateFullyPenalizedReservations(ctx);
        int cancelledLatePartial = seedCancelledLatePartialPenaltyReservations(ctx);
        int cancelledNeverPaid  = seedCancelledNeverPaidReservations(ctx);

        int totalReservations = completedPaid + completedPending + completedNoRecords
                + confirmedWithDeposit + confirmedNoDeposit
                + cancelledOnTime + cancelledLateFull + cancelledLatePartial + cancelledNeverPaid;

        log.info("DevelopmentDataSeeder: siembra completada — {} usuarios, {} propiedades, {} temporadas, " +
                        "{} políticas, {} reservas ({} completadas, {} confirmadas, {} canceladas), " +
                        "{} anticipos, {} facturas, {} penalidades, {} devoluciones.",
                userRepository.count(), propertyRepository.count(), temporadaRepository.count(),
                politicaRepository.count(), totalReservations,
                completedPaid + completedPending + completedNoRecords,
                confirmedWithDeposit + confirmedNoDeposit,
                cancelledOnTime + cancelledLateFull + cancelledLatePartial + cancelledNeverPaid,
                anticipoRepository.count(), facturaRepository.count(),
                penalidadRepository.count(), devolucionRepository.count());
    }

    // ── Users ──────────────────────────────────────────────────────────────────
    // One user per role, so every role in the RBAC model actually shows up as the
    // creator/processor of records below (reservations by Recepción, advances and
    // penalties by accounting staff, invoices and refunds by Contador/Administrador).

    private Map<String, User> seedUsers() {
        Rol rolContador = rolRepository.findByNombre("Contador").orElseThrow();
        Rol rolAuxiliar = rolRepository.findByNombre("Auxiliar contable").orElseThrow();
        Rol rolRecep    = rolRepository.findByNombre("Recepcionista").orElseThrow();

        // C-3 (AUDIT_v5): the administrator is created solely by AdminUserInitializer
        // (@Order(LOWEST_PRECEDENCE - 10), which runs before this seeder's
        // @Order(LOWEST_PRECEDENCE)), using whatever ADMIN_EMAIL/ADMIN_PASSWORD is
        // configured. This seeder must not assume or hardcode a specific admin
        // email/password — doing so previously recreated admin@novafacts.com/Admin2024!
        // itself whenever a deployment configured a different ADMIN_EMAIL, reintroducing
        // the exact hardcoded-admin vulnerability this fix closes. Look up whichever
        // admin already exists instead.
        User admin = userRepository.findAllByRolNombreOrderById("Administrador").stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró ningún Administrador — AdminUserInitializer debería haber creado uno antes de este seeder."));

        return Map.of(
            "admin",    admin,
            "contador", getOrCreateUser("contador@novafacts.com",    "Conta2024!",  "María Fernanda García", rolContador, true),
            "auxiliar", getOrCreateUser("auxiliar@novafacts.com",    "Aux2024!",    "Carlos Eduardo López",  rolAuxiliar, true),
            "recep",    getOrCreateUser("recepcion@novafacts.com",   "Recep2024!",  "Ana Sofía Martínez",    rolRecep,    true)
        );
    }

    // ── Seasons ────────────────────────────────────────────────────────────────
    // Nine seasons spanning February 2025 through January 2027 — seven already elapsed
    // (used for COMPLETED and resolved CANCELLED reservations) and two still ahead
    // (used for CONFIRMED reservations). Ranges are strictly non-overlapping.

    private Map<String, Temporada> seedSeasons() {
        return Map.ofEntries(
            Map.entry("media2025",     findOrCreateTemporada("Temporada Media 2025",
                    LocalDate.of(2025, 2, 1),  LocalDate.of(2025, 4, 12))),
            Map.entry("semanaSanta25", findOrCreateTemporada("Semana Santa 2025",
                    LocalDate.of(2025, 4, 13), LocalDate.of(2025, 4, 20))),
            Map.entry("altaMedio25",   findOrCreateTemporada("Temporada Alta - Mitad de Año 2025",
                    LocalDate.of(2025, 6, 15), LocalDate.of(2025, 7, 31))),
            Map.entry("baja25",        findOrCreateTemporada("Temporada Baja 2025",
                    LocalDate.of(2025, 8, 1),  LocalDate.of(2025, 9, 30))),
            Map.entry("puenteOct25",   findOrCreateTemporada("Puente Festivo Octubre 2025",
                    LocalDate.of(2025, 10, 10), LocalDate.of(2025, 10, 19))),
            Map.entry("finDeAno2526",  findOrCreateTemporada("Temporada Fin de Año 2025-2026",
                    LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 15))),
            Map.entry("media2026",     findOrCreateTemporada("Temporada Media 2026",
                    LocalDate.of(2026, 2, 1),  LocalDate.of(2026, 6, 30))),
            Map.entry("altaMedio26",   findOrCreateTemporada("Temporada Alta - Mitad de Año 2026",
                    LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 31))),
            Map.entry("finDeAno2627",  findOrCreateTemporada("Temporada Fin de Año 2026-2027",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 15)))
        );
    }

    // ── Properties ─────────────────────────────────────────────────────────────
    // 16 properties across 9 Colombian cities, spanning the styles requested for the
    // demo: luxury villas, apartments, cabins, country houses, beach houses, mountain
    // lodges and executive apartments.

    private Map<String, Property> seedProperties() {
        return Map.ofEntries(
            Map.entry("villaEsmeralda",  findOrCreateProperty("Villa Esmeralda",
                    "Km 3 vía Chía - Cajicá, Cundinamarca", "Villa de lujo con piscina climatizada, jardín privado y zona BBQ")),
            Map.entry("apartaSanFdo",    findOrCreateProperty("Apartaestudio San Fernando",
                    "Cra 15 #93-47, Chapinero, Bogotá",     "Apartaestudio ejecutivo amoblado, cerca a la zona financiera")),
            Map.entry("casaLosAlisos",   findOrCreateProperty("Casa Campestre Los Alisos",
                    "Vereda El Vergel, Envigado, Antioquia", "Casa campestre de dos pisos rodeada de bosque nativo")),
            Map.entry("cabanaElRoble",   findOrCreateProperty("Cabaña El Roble",
                    "Vía Guatapé - El Peñol Km 2, Antioquia", "Cabaña de madera con vista al embalse, chimenea y muelle propio")),
            Map.entry("loftPoblado",     findOrCreateProperty("Loft Poblado 47",
                    "Cra 43A #16-38, El Poblado, Medellín",  "Loft moderno de un ambiente, balcón y vista a la ciudad")),
            Map.entry("casaBahiaAzul",   findOrCreateProperty("Casa de Playa Bahía Azul",
                    "Anillo Vial Km 8, Cartagena",           "Casa frente al mar con acceso directo a la playa")),
            Map.entry("aptoBocagrande",  findOrCreateProperty("Apartamento Bocagrande Vista Mar",
                    "Cra 1 #8-129, Bocagrande, Cartagena",   "Apartamento en torre con vista panorámica al mar Caribe")),
            Map.entry("villaLosCocos",   findOrCreateProperty("Villa Los Cocos",
                    "Carrera 1 #14-22, El Rodadero, Santa Marta", "Villa de lujo a dos cuadras de la playa, con piscina infinita")),
            Map.entry("cabanaSierra",    findOrCreateProperty("Cabaña Sierra Nevada",
                    "Vereda Minca, Santa Marta, Magdalena",  "Cabaña ecológica en la montaña, ideal para avistamiento de aves")),
            Map.entry("casaGetsemani",   findOrCreateProperty("Casa Colonial Getsemaní",
                    "Calle del Guerrero #29-84, Getsemaní, Cartagena", "Casa colonial restaurada con patio interior y terraza")),
            Map.entry("aptoChico",       findOrCreateProperty("Apartamento Ejecutivo Chicó",
                    "Calle 94 #11-30, Chicó, Bogotá",        "Apartamento ejecutivo de dos alcobas cerca a centros empresariales")),
            Map.entry("fincaEsperanza",  findOrCreateProperty("Finca La Esperanza",
                    "Vereda Sáquima, Villa de Leyva, Boyacá", "Finca de descanso con establos, huerta y vista a la sabana")),
            Map.entry("refugioNevado",   findOrCreateProperty("Refugio Nevado del Ruiz",
                    "Km 12 vía al Parque Los Nevados, Manizales", "Refugio de montaña con estufa a leña y senderos privados")),
            Map.entry("haciendaParaiso", findOrCreateProperty("Casa Hacienda El Paraíso",
                    "Corregimiento de Pance, Cali, Valle del Cauca", "Hacienda tradicional vallecaucana con caballerizas y piscina")),
            Map.entry("aptoCiudadJardin",findOrCreateProperty("Apartamento Ciudad Jardín",
                    "Cra 100 #14-56, Ciudad Jardín, Cali",   "Apartamento familiar en conjunto cerrado con zonas verdes")),
            Map.entry("villaMarina",     findOrCreateProperty("Villa Marina",
                    "Sector Sarie Bay, San Andrés Isla",     "Villa isleña de lujo con acceso privado a playa de aguas cristalinas"))
        );
    }

    // ── Cancellation policies ──────────────────────────────────────────────────
    // Six distinct policy "profiles" (by refund %/notice days) assigned across the
    // 16 properties so that several properties genuinely share the same terms, while
    // others are deliberately stricter or more flexible. Each policy row still needs
    // its own unique name (politica_cancelacion.nombre has no property-scoped lookup),
    // so property-qualified names are used even where the underlying terms repeat.

    private Map<String, PoliticaCancelacion> seedPolicies(Map<String, Property> p) {
        return Map.ofEntries(
            // Profile F — Sin reembolso (0% / 3 días): high-demand luxury villas
            Map.entry("villaEsmeralda",  findOrCreatePolitica("Sin Reembolso - Villa Esmeralda",
                    p.get("villaEsmeralda"), new BigDecimal("0.00"), 3, "Sin reembolso; alta demanda en temporada")),
            Map.entry("villaLosCocos",   findOrCreatePolitica("Sin Reembolso - Villa Los Cocos",
                    p.get("villaLosCocos"), new BigDecimal("0.00"), 3, "Sin reembolso; alta demanda en temporada")),
            // Profile E — Muy estricta (20% / 15 días): exclusive rural retreats
            Map.entry("fincaEsperanza",  findOrCreatePolitica("Muy Estricta - Finca La Esperanza",
                    p.get("fincaEsperanza"), new BigDecimal("20.00"), 15, "20% de reembolso con 15 días de aviso previo")),
            Map.entry("villaMarina",     findOrCreatePolitica("Muy Estricta - Villa Marina",
                    p.get("villaMarina"), new BigDecimal("20.00"), 15, "20% de reembolso con 15 días de aviso previo")),
            // Profile D — Estricta (30% / 10 días)
            Map.entry("casaBahiaAzul",   findOrCreatePolitica("Estricta - Casa Bahía Azul",
                    p.get("casaBahiaAzul"), new BigDecimal("30.00"), 10, "30% de reembolso con 10 días de aviso previo")),
            Map.entry("haciendaParaiso", findOrCreatePolitica("Estricta - Hacienda El Paraíso",
                    p.get("haciendaParaiso"), new BigDecimal("30.00"), 10, "30% de reembolso con 10 días de aviso previo")),
            // Profile C — Moderada (60% / 7 días): mid-range family properties
            Map.entry("casaLosAlisos",   findOrCreatePolitica("Moderada - Casa Los Alisos",
                    p.get("casaLosAlisos"), new BigDecimal("60.00"), 7, "60% de reembolso con 7 días de aviso previo")),
            Map.entry("aptoBocagrande",  findOrCreatePolitica("Moderada - Apartamento Bocagrande",
                    p.get("aptoBocagrande"), new BigDecimal("60.00"), 7, "60% de reembolso con 7 días de aviso previo")),
            Map.entry("aptoChico",       findOrCreatePolitica("Moderada - Apartamento Chicó",
                    p.get("aptoChico"), new BigDecimal("60.00"), 7, "60% de reembolso con 7 días de aviso previo")),
            // Profile B — Estándar (50% / 5 días): the most common profile, urban apartments
            Map.entry("apartaSanFdo",    findOrCreatePolitica("Estándar - Apartaestudio San Fernando",
                    p.get("apartaSanFdo"), new BigDecimal("50.00"), 5, "50% de reembolso con 5 días de aviso previo")),
            Map.entry("loftPoblado",     findOrCreatePolitica("Estándar - Loft Poblado",
                    p.get("loftPoblado"), new BigDecimal("50.00"), 5, "50% de reembolso con 5 días de aviso previo")),
            Map.entry("casaGetsemani",   findOrCreatePolitica("Estándar - Casa Getsemaní",
                    p.get("casaGetsemani"), new BigDecimal("50.00"), 5, "50% de reembolso con 5 días de aviso previo")),
            Map.entry("aptoCiudadJardin",findOrCreatePolitica("Estándar - Apartamento Ciudad Jardín",
                    p.get("aptoCiudadJardin"), new BigDecimal("50.00"), 5, "50% de reembolso con 5 días de aviso previo")),
            // Profile A — Flexible (80% / 2 días): mountain cabins and lodges (lower turnover risk)
            Map.entry("cabanaElRoble",   findOrCreatePolitica("Flexible - Cabaña El Roble",
                    p.get("cabanaElRoble"), new BigDecimal("80.00"), 2, "80% de reembolso con 2 días de aviso previo")),
            Map.entry("cabanaSierra",    findOrCreatePolitica("Flexible - Cabaña Sierra Nevada",
                    p.get("cabanaSierra"), new BigDecimal("80.00"), 2, "80% de reembolso con 2 días de aviso previo")),
            Map.entry("refugioNevado",   findOrCreatePolitica("Flexible - Refugio Nevado del Ruiz",
                    p.get("refugioNevado"), new BigDecimal("80.00"), 2, "80% de reembolso con 2 días de aviso previo"))
        );
    }

    /** Bundles the shared lookup tables so each scenario method only needs one parameter. */
    private record ScenarioContext(
            List<Canal> canales,
            Map<String, Temporada> seasons,
            Map<String, PoliticaCancelacion> policies,
            Map<String, User> users,
            Map<String, Property> properties) {

        // Canal order from Flyway V3: 0=Airbnb, 1=Booking, 2=Web propia, 3=Teléfono, 4=WhatsApp
        Canal canal(int i) { return canales.get(i); }
        Temporada season(String key) { return seasons.get(key); }
        PoliticaCancelacion policy(String propertyKey) { return policies.get(propertyKey); }
        User user(String key) { return users.get(key); }
        Long propertyId(String key) { return properties.get(key).getId(); }
    }

    // ── Scenario 1: Completed stay, advance applied, invoice paid ─────────────────
    // The canonical happy path: guest paid a deposit, stayed, and the invoice was
    // fully settled. 17 reservations across past seasons.

    private int seedCompletedPaidReservations(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto,
                 String anticipoMonto, LocalDate fechaAnticipo, String metodoAnticipo,
                 String facturaNum, String impuestos) {}

        List<R> rows = List.of(
            new R("laura.rodriguez@gmail.com", "Laura Fernanda Rodríguez", "3001122334", "villaEsmeralda", "altaMedio25", 0,
                    "recep", LocalDate.of(2025,6,20), LocalDate.of(2025,6,27), 6, "4200000.00",
                    "1700000.00", LocalDate.of(2025,6,2), "Transferencia bancaria", "FAC-2025-0001", "660000.00"),
            new R("pedro.alvarez@hotmail.com", "Pedro Antonio Álvarez", "3102233445", "casaLosAlisos", "altaMedio25", 1,
                    "recep", LocalDate.of(2025,6,18), LocalDate.of(2025,6,23), 4, "1850000.00",
                    "800000.00", LocalDate.of(2025,5,30), "Tarjeta de crédito", "FAC-2025-0002", "324000.00"),
            new R("mariana.salazar@gmail.com", "Mariana Isabel Salazar", "3011223345", "aptoBocagrande", "altaMedio25", 2,
                    "admin", LocalDate.of(2025,7,1), LocalDate.of(2025,7,6), 3, "2100000.00",
                    "950000.00", LocalDate.of(2025,6,10), "PSE", "FAC-2025-0003", "365000.00"),
            new R("santiago.mora@outlook.com", "Santiago Mora Jiménez", "3008899002", "casaBahiaAzul", "altaMedio25", 3,
                    "recep", LocalDate.of(2025,7,8), LocalDate.of(2025,7,15), 5, "3100000.00",
                    "1400000.00", LocalDate.of(2025,6,18), "Transferencia bancaria", "FAC-2025-0004", "540000.00"),
            new R("juliana.restrepo@gmail.com", "Juliana Restrepo Ochoa", "3119900113", "cabanaElRoble", "media2025", 4,
                    "recep", LocalDate.of(2025,3,10), LocalDate.of(2025,3,14), 2, "980000.00",
                    "400000.00", LocalDate.of(2025,2,20), "WhatsApp Pay", "FAC-2025-0005", "171000.00"),
            new R("daniel.gomez@hotmail.com", "Daniel Esteban Gómez", "3210011224", "loftPoblado", "media2025", 0,
                    "contador", LocalDate.of(2025,3,20), LocalDate.of(2025,3,23), 1, "620000.00",
                    "250000.00", LocalDate.of(2025,3,1), "Efectivo", "FAC-2025-0006", "108000.00"),
            new R("gabriela.ortiz@yahoo.com", "Gabriela Ximena Ortiz", "3223344557", "villaLosCocos", "semanaSanta25", 1,
                    "recep", LocalDate.of(2025,4,13), LocalDate.of(2025,4,19), 7, "5100000.00",
                    "2200000.00", LocalDate.of(2025,3,25), "Transferencia bancaria", "FAC-2025-0007", "893000.00"),
            new R("sebastian.rojas@gmail.com", "Sebastián David Rojas", "3154456679", "cabanaSierra", "semanaSanta25", 2,
                    "auxiliar", LocalDate.of(2025,4,14), LocalDate.of(2025,4,18), 3, "1450000.00",
                    "600000.00", LocalDate.of(2025,4,1), "Tarjeta de crédito", "FAC-2025-0008", "252000.00"),
            new R("isabella.moreno@hotmail.com", "Isabella Moreno Vélez", "3305566779", "casaGetsemani", "baja25", 3,
                    "recep", LocalDate.of(2025,8,5), LocalDate.of(2025,8,9), 4, "1600000.00",
                    "650000.00", LocalDate.of(2025,7,15), "PSE", "FAC-2025-0009", "277000.00"),
            new R("felipe.duarte@gmail.com", "Felipe Duarte Naranjo", "3167788991", "aptoChico", "baja25", 4,
                    "recep", LocalDate.of(2025,8,12), LocalDate.of(2025,8,15), 2, "1080000.00",
                    "450000.00", LocalDate.of(2025,7,25), "Transferencia bancaria", "FAC-2025-0010", "187000.00"),
            new R("natalia.pardo@outlook.com", "Natalia Pardo Beltrán", "3018899003", "fincaEsperanza", "baja25", 0,
                    "admin", LocalDate.of(2025,8,20), LocalDate.of(2025,8,25), 5, "2900000.00",
                    "1300000.00", LocalDate.of(2025,8,1), "Transferencia bancaria", "FAC-2025-0011", "494000.00"),
            new R("david.espitia@gmail.com", "David Espitia Lozano", "3129900114", "refugioNevado", "baja25", 1,
                    "recep", LocalDate.of(2025,9,3), LocalDate.of(2025,9,7), 2, "1150000.00",
                    "500000.00", LocalDate.of(2025,8,14), "Efectivo", "FAC-2025-0012", "199000.00"),
            new R("paula.beltran@yahoo.com", "Paula Andrea Beltrán", "3230011225", "haciendaParaiso", "puenteOct25", 2,
                    "recep", LocalDate.of(2025,10,10), LocalDate.of(2025,10,14), 6, "2600000.00",
                    "1100000.00", LocalDate.of(2025,9,22), "Tarjeta de crédito", "FAC-2025-0013", "445000.00"),
            new R("alejandro.nino@gmail.com", "Alejandro Niño Cuéllar", "3161122336", "aptoCiudadJardin", "puenteOct25", 3,
                    "auxiliar", LocalDate.of(2025,10,12), LocalDate.of(2025,10,15), 3, "930000.00",
                    "400000.00", LocalDate.of(2025,9,28), "PSE", "FAC-2025-0014", "162000.00"),
            new R("manuela.arango@hotmail.com", "Manuela Arango Vásquez", "3012233446", "villaMarina", "finDeAno2526", 4,
                    "recep", LocalDate.of(2025,12,20), LocalDate.of(2025,12,28), 8, "6300000.00",
                    "2800000.00", LocalDate.of(2025,12,1), "Transferencia bancaria", "FAC-2025-0015", "1085000.00"),
            new R("tomas.escobar@gmail.com", "Tomás Escobar Quintero", "3123344557", "apartaSanFdo", "finDeAno2526", 0,
                    "recep", LocalDate.of(2025,12,27), LocalDate.of(2026,1,2), 2, "1450000.00",
                    "650000.00", LocalDate.of(2025,12,8), "Tarjeta de crédito", "FAC-2025-0016", "253000.00"),
            new R("valeria.cardenas@outlook.com", "Valeria Cárdenas Mesa", "3234455668", "villaEsmeralda", "media2026", 1,
                    "contador", LocalDate.of(2026,3,5), LocalDate.of(2026,3,9), 4, "2650000.00",
                    "1150000.00", LocalDate.of(2026,2,15), "Transferencia bancaria", "FAC-2026-0001", "465000.00")
        );

        int count = 0;
        for (R r : rows) {
            Reservation res = findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.COMPLETED);

            createAnticipoIfAbsent(res, c.user("contador"), new BigDecimal(r.anticipoMonto()),
                    r.fechaAnticipo(), r.metodoAnticipo(), AnticipoEstado.APLICADO);

            BigDecimal subtotal   = new BigDecimal(r.monto());
            BigDecimal descuento  = new BigDecimal(r.anticipoMonto());
            BigDecimal impuestos  = new BigDecimal(r.impuestos());
            BigDecimal total      = subtotal.subtract(descuento).add(impuestos);
            createFacturaIfAbsent(res, c.user("contador"), r.facturaNum(),
                    subtotal, descuento, BigDecimal.ZERO, impuestos, total, InvoiceStatus.PAID);
            count++;
        }
        return count;
    }

    // ── Scenario 2: Completed stay, invoice issued but still unpaid ───────────────
    // The stay already happened and the invoice was generated, but payment hasn't
    // been collected yet — a realistic accounts-receivable snapshot for the dashboard.

    private int seedCompletedPendingInvoiceReservations(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto,
                 String anticipoMonto, LocalDate fechaAnticipo, String metodoAnticipo,
                 String facturaNum, String impuestos) {}

        List<R> rows = List.of(
            new R("ricardo.fajardo@gmail.com", "Ricardo Fajardo Peláez", "3145566780", "loftPoblado", "baja25", 2,
                    "recep", LocalDate.of(2025,9,10), LocalDate.of(2025,9,13), 2, "870000.00",
                    "350000.00", LocalDate.of(2025,8,22), "Efectivo", "FAC-2025-0017", "148000.00"),
            new R("catalina.gil@hotmail.com", "Catalina Gil Restrepo", "3256677891", "casaGetsemani", "puenteOct25", 3,
                    "recep", LocalDate.of(2025,10,11), LocalDate.of(2025,10,15), 4, "1520000.00",
                    "650000.00", LocalDate.of(2025,9,20), "Transferencia bancaria", "FAC-2025-0018", "261000.00"),
            new R("ivan.bermudez@outlook.com", "Iván Bermúdez Rincón", "3067788992", "fincaEsperanza", "finDeAno2526", 4,
                    "admin", LocalDate.of(2025,12,15), LocalDate.of(2025,12,19), 5, "2450000.00",
                    "1050000.00", LocalDate.of(2025,11,25), "Tarjeta de crédito", "FAC-2025-0019", "420000.00"),
            new R("angelica.suarez@gmail.com", "Angélica Suárez Prieto", "3178899003", "aptoChico", "media2026", 0,
                    "recep", LocalDate.of(2026,4,2), LocalDate.of(2026,4,5), 2, "1020000.00",
                    "420000.00", LocalDate.of(2026,3,14), "PSE", "FAC-2026-0002", "178000.00")
        );

        int count = 0;
        for (R r : rows) {
            Reservation res = findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.COMPLETED);

            createAnticipoIfAbsent(res, c.user("contador"), new BigDecimal(r.anticipoMonto()),
                    r.fechaAnticipo(), r.metodoAnticipo(), AnticipoEstado.APLICADO);

            BigDecimal subtotal  = new BigDecimal(r.monto());
            BigDecimal descuento = new BigDecimal(r.anticipoMonto());
            BigDecimal impuestos = new BigDecimal(r.impuestos());
            BigDecimal total     = subtotal.subtract(descuento).add(impuestos);
            createFacturaIfAbsent(res, c.user("contador"), r.facturaNum(),
                    subtotal, descuento, BigDecimal.ZERO, impuestos, total, InvoiceStatus.PENDING);
            count++;
        }
        return count;
    }

    // ── Scenario 3: Completed stay, no financial records in the system ────────────
    // Legacy or manually-settled bookings — the stay happened, but no advance or
    // invoice was ever registered in NovaFacts (paid directly at the property, outside
    // the system). Demonstrates the dashboard doesn't assume every reservation has a
    // full financial trail.

    private int seedCompletedWithoutFinancialRecords(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto) {}

        List<R> rows = List.of(
            new R("hernan.trujillo@gmail.com", "Hernán Trujillo Vanegas", "3189900224", "cabanaSierra", "media2025", 3,
                    "recep", LocalDate.of(2025,2,14), LocalDate.of(2025,2,17), 2, "720000.00"),
            new R("diana.zapata@hotmail.com", "Diana Zapata Cifuentes", "3221122337", "aptoCiudadJardin", "media2025", 4,
                    "recep", LocalDate.of(2025,3,1), LocalDate.of(2025,3,3), 2, "480000.00"),
            new R("jorge.villamil@outlook.com", "Jorge Villamil Cortés", "3092233448", "haciendaParaiso", "altaMedio25", 0,
                    "recep", LocalDate.of(2025,6,25), LocalDate.of(2025,6,29), 4, "1900000.00"),
            new R("viviana.castano@gmail.com", "Viviana Castaño Rendón", "3133344559", "casaLosAlisos", "puenteOct25", 1,
                    "recep", LocalDate.of(2025,10,16), LocalDate.of(2025,10,19), 3, "890000.00"),
            new R("oscar.leon@yahoo.com", "Óscar León Buitrago", "3204455670", "refugioNevado", "finDeAno2526", 2,
                    "recep", LocalDate.of(2026,1,2), LocalDate.of(2026,1,5), 2, "1050000.00")
        );

        int count = 0;
        for (R r : rows) {
            findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.COMPLETED);
            count++;
        }
        return count;
    }

    // ── Scenario 4: Confirmed upcoming stay, deposit already paid ─────────────────
    // The guest has paid an advance and the stay is booked for the future — no
    // invoice yet, since the invoice is only issued once the stay is completed.

    private int seedConfirmedWithDepositReservations(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto,
                 String anticipoMonto, LocalDate fechaAnticipo, String metodoAnticipo) {}

        List<R> rows = List.of(
            new R("camilo.arbelaez@gmail.com", "Camilo Arbeláez Toro", "3145566781", "villaEsmeralda", "altaMedio26", 0,
                    "recep", LocalDate.of(2026,7,18), LocalDate.of(2026,7,25), 6, "4350000.00",
                    "1800000.00", LocalDate.of(2026,6,20), "Transferencia bancaria"),
            new R("estefania.cuellar@hotmail.com", "Estefanía Cuéllar Rico", "3256677892", "casaBahiaAzul", "altaMedio26", 1,
                    "recep", LocalDate.of(2026,7,20), LocalDate.of(2026,7,27), 5, "3200000.00",
                    "1400000.00", LocalDate.of(2026,6,25), "Tarjeta de crédito"),
            new R("rodrigo.pinzon@outlook.com", "Rodrigo Pinzón Duque", "3067788993", "villaLosCocos", "altaMedio26", 2,
                    "admin", LocalDate.of(2026,7,25), LocalDate.of(2026,8,1), 7, "5250000.00",
                    "2300000.00", LocalDate.of(2026,7,2), "PSE"),
            new R("adriana.montoya@gmail.com", "Adriana Montoya Ibáñez", "3178899004", "aptoBocagrande", "altaMedio26", 3,
                    "recep", LocalDate.of(2026,8,2), LocalDate.of(2026,8,6), 3, "2050000.00",
                    "900000.00", LocalDate.of(2026,7,10), "Transferencia bancaria"),
            new R("fernando.valbuena@gmail.com", "Fernando Valbuena Soto", "3018899005", "cabanaElRoble", "altaMedio26", 4,
                    "auxiliar", LocalDate.of(2026,8,5), LocalDate.of(2026,8,9), 2, "1120000.00",
                    "480000.00", LocalDate.of(2026,7,12), "Efectivo"),
            new R("carolina.henao@hotmail.com", "Carolina Henao Zapata", "3129900115", "loftPoblado", "altaMedio26", 0,
                    "recep", LocalDate.of(2026,8,10), LocalDate.of(2026,8,13), 2, "680000.00",
                    "300000.00", LocalDate.of(2026,7,15), "Tarjeta de crédito"),
            new R("marcela.ariza@outlook.com", "Marcela Ariza Neira", "3230011226", "casaGetsemani", "altaMedio26", 1,
                    "recep", LocalDate.of(2026,8,14), LocalDate.of(2026,8,18), 4, "1780000.00",
                    "780000.00", LocalDate.of(2026,7,20), "PSE"),
            new R("rafael.combita@gmail.com", "Rafael Combita Rangel", "3161122338", "aptoChico", "altaMedio26", 2,
                    "recep", LocalDate.of(2026,8,20), LocalDate.of(2026,8,23), 2, "1010000.00",
                    "450000.00", LocalDate.of(2026,7,28), "Transferencia bancaria"),
            new R("patricia.nova@yahoo.com", "Patricia Nova Guzmán", "3012233447", "fincaEsperanza", "altaMedio26", 3,
                    "admin", LocalDate.of(2026,8,24), LocalDate.of(2026,8,29), 6, "3050000.00",
                    "1350000.00", LocalDate.of(2026,8,1), "Transferencia bancaria"),
            new R("william.pabon@gmail.com", "William Pabón Achury", "3123344558", "villaMarina", "finDeAno2627", 4,
                    "recep", LocalDate.of(2026,12,18), LocalDate.of(2026,12,26), 8, "6800000.00",
                    "3000000.00", LocalDate.of(2026,11,28), "Transferencia bancaria"),
            new R("liliana.parra@hotmail.com", "Liliana Parra Cifuentes", "3234455669", "villaEsmeralda", "finDeAno2627", 0,
                    "recep", LocalDate.of(2026,12,22), LocalDate.of(2026,12,29), 6, "4900000.00",
                    "2100000.00", LocalDate.of(2026,12,1), "Tarjeta de crédito"),
            new R("giovanni.rueda@outlook.com", "Giovanni Rueda Cepeda", "3045566782", "casaBahiaAzul", "finDeAno2627", 1,
                    "recep", LocalDate.of(2026,12,27), LocalDate.of(2027,1,3), 5, "3650000.00",
                    "1600000.00", LocalDate.of(2026,12,5), "PSE"),
            new R("yolanda.buitrago@gmail.com", "Yolanda Buitrago Mora", "3256677893", "refugioNevado", "finDeAno2627", 2,
                    "auxiliar", LocalDate.of(2026,12,29), LocalDate.of(2027,1,2), 3, "1450000.00",
                    "620000.00", LocalDate.of(2026,12,8), "Efectivo"),
            new R("henry.solano@gmail.com", "Henry Solano Quiroga", "3067788994", "haciendaParaiso", "finDeAno2627", 3,
                    "recep", LocalDate.of(2027,1,1), LocalDate.of(2027,1,6), 6, "2950000.00",
                    "1300000.00", LocalDate.of(2026,12,10), "Transferencia bancaria")
        );

        int count = 0;
        for (R r : rows) {
            Reservation res = findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.CONFIRMED);

            createAnticipoIfAbsent(res, c.user("contador"), new BigDecimal(r.anticipoMonto()),
                    r.fechaAnticipo(), r.metodoAnticipo(), AnticipoEstado.REGISTRADO);
            count++;
        }
        return count;
    }

    // ── Scenario 5: Confirmed upcoming stay, deposit not yet collected ────────────
    // Reservation is booked and confirmed, but the guest hasn't paid a deposit yet
    // (common right after a same-day or next-day booking) — no financial records at all.

    private int seedConfirmedWithoutDepositReservations(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto) {}

        List<R> rows = List.of(
            new R("cristian.moreno@gmail.com", "Cristian Moreno Salcedo", "3178899005", "aptoCiudadJardin", "altaMedio26", 4,
                    "recep", LocalDate.of(2026,7,16), LocalDate.of(2026,7,19), 3, "870000.00"),
            new R("melissa.acosta@hotmail.com", "Melissa Acosta Rivas", "3189900225", "apartaSanFdo", "altaMedio26", 0,
                    "recep", LocalDate.of(2026,7,22), LocalDate.of(2026,7,24), 2, "560000.00"),
            new R("alvaro.paez@outlook.com", "Álvaro Páez Gaviria", "3221122338", "cabanaSierra", "altaMedio26", 1,
                    "recep", LocalDate.of(2026,8,3), LocalDate.of(2026,8,7), 3, "1180000.00"),
            new R("diana.uribe@gmail.com", "Diana Uribe Franco", "3092233449", "casaLosAlisos", "altaMedio26", 2,
                    "recep", LocalDate.of(2026,8,12), LocalDate.of(2026,8,16), 4, "1640000.00"),
            new R("javier.moncada@yahoo.com", "Javier Moncada Cely", "3204455671", "aptoBocagrande", "finDeAno2627", 3,
                    "recep", LocalDate.of(2026,12,30), LocalDate.of(2027,1,4), 3, "2100000.00"),
            new R("sara.higuera@gmail.com", "Sara Higuera Molano", "3133344560", "villaLosCocos", "finDeAno2627", 4,
                    "recep", LocalDate.of(2027,1,3), LocalDate.of(2027,1,9), 6, "4400000.00")
        );

        int count = 0;
        for (R r : rows) {
            findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.CONFIRMED);
            count++;
        }
        return count;
    }

    // ── Scenario 6: Cancelled with enough notice — full refund, no penalty ────────
    // Guest cancelled respecting the property's notice-period policy, so the full
    // advance is returned and no penalty applies.

    private int seedCancelledOnTimeReservations(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto,
                 String anticipoMonto, LocalDate fechaAnticipo, String metodoAnticipo,
                 LocalDate fechaCancelacion, String metodoDevolucion, User devolucionUsuario) {}

        List<R> rows = List.of(
            new R("juan.delgado@gmail.com", "Juan Camilo Delgado", "3145566783", "loftPoblado", "baja25", 0,
                    "recep", LocalDate.of(2025,9,15), LocalDate.of(2025,9,18), 2, "690000.00",
                    "300000.00", LocalDate.of(2025,8,20), "Tarjeta de crédito",
                    LocalDate.of(2025,9,1), "Tarjeta de crédito", null),
            new R("laura.contreras@hotmail.com", "Laura Contreras Silva", "3256677894", "apartaSanFdo", "media2025", 1,
                    "recep", LocalDate.of(2025,3,25), LocalDate.of(2025,3,28), 2, "590000.00",
                    "250000.00", LocalDate.of(2025,3,5), "PSE",
                    LocalDate.of(2025,3,15), "PSE", null),
            new R("esteban.quiroga@outlook.com", "Esteban Quiroga Peña", "3067788995", "cabanaSierra", "puenteOct25", 2,
                    "auxiliar", LocalDate.of(2025,10,13), LocalDate.of(2025,10,16), 3, "980000.00",
                    "420000.00", LocalDate.of(2025,9,25), "Transferencia bancaria",
                    LocalDate.of(2025,10,3), "Transferencia bancaria", null),
            new R("monica.florez@gmail.com", "Mónica Flórez Serrano", "3178899006", "casaGetsemani", "finDeAno2526", 3,
                    "recep", LocalDate.of(2026,1,3), LocalDate.of(2026,1,6), 4, "1250000.00",
                    "550000.00", LocalDate.of(2025,12,10), "Efectivo",
                    LocalDate.of(2025,12,26), "Efectivo", null),
            new R("harold.novoa@yahoo.com", "Harold Novoa Sepúlveda", "3018899006", "refugioNevado", "media2026", 4,
                    "recep", LocalDate.of(2026,3,12), LocalDate.of(2026,3,15), 2, "870000.00",
                    "380000.00", LocalDate.of(2026,2,18), "Transferencia bancaria",
                    LocalDate.of(2026,3,1), "Transferencia bancaria", null)
        );

        int count = 0;
        for (R r : rows) {
            Reservation res = findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.CANCELLED);

            createAnticipoIfAbsent(res, c.user("contador"), new BigDecimal(r.anticipoMonto()),
                    r.fechaAnticipo(), r.metodoAnticipo(), AnticipoEstado.DEVUELTO);

            // Full refund: the guest respected the notice window, so the whole advance comes back.
            createDevolucionIfAbsent(res, c.user("contador"), new BigDecimal(r.anticipoMonto()),
                    r.metodoDevolucion(), DevolucionEstado.PROCESADA);
            count++;
        }
        return count;
    }

    // ── Scenario 7: Cancelled too late — penalty consumes the entire advance ──────
    // Notice fell inside the property's penalty window and the maximum penalty
    // allowed by the policy is at least as large as the advance itself, so the whole
    // deposit is forfeited and the refund request is rejected outright.

    private int seedCancelledLateFullyPenalizedReservations(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto,
                 String anticipoMonto, LocalDate fechaAnticipo, String metodoAnticipo,
                 LocalDate fechaCancelacion, String motivo) {}

        // r1: Casa Bahía Azul, Estricta policy (30% reembolso) → cap = monto*0.70.
        //     monto=1 800 000 → cap=1 260 000, well above the 650 000 advance, so the
        //     full advance is approved as penalty.
        // r2: Villa Marina, Muy Estricta policy (20% reembolso) → cap = monto*0.80.
        //     monto=2 400 000 → cap=1 920 000, again above the 900 000 advance.
        List<R> rows = List.of(
            new R("andres.penuela@gmail.com", "Andrés Peñuela Cano", "3129900116", "casaBahiaAzul", "altaMedio25", 3,
                    "recep", LocalDate.of(2025,7,10), LocalDate.of(2025,7,15), 5, "1800000.00",
                    "650000.00", LocalDate.of(2025,6,20), "Transferencia bancaria",
                    LocalDate.of(2025,7,6), "Cancelación 4 días antes del check-in, fuera del plazo de 10 días"),
            new R("carolina.matiz@hotmail.com", "Carolina Matiz Bohórquez", "3230011227", "villaMarina", "finDeAno2526", 4,
                    "auxiliar", LocalDate.of(2025,12,23), LocalDate.of(2025,12,30), 7, "2400000.00",
                    "900000.00", LocalDate.of(2025,12,3), "Tarjeta de crédito",
                    LocalDate.of(2025,12,17), "Cancelación 6 días antes del check-in, fuera del plazo de 15 días")
        );

        int count = 0;
        for (R r : rows) {
            Reservation res = findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.CANCELLED);

            createAnticipoIfAbsent(res, c.user("contador"), new BigDecimal(r.anticipoMonto()),
                    r.fechaAnticipo(), r.metodoAnticipo(), AnticipoEstado.DEVUELTO);

            BigDecimal montoTotal   = new BigDecimal(r.monto());
            BigDecimal reembolsoPct = c.policy(r.propKey()).getPorcentajeReembolso();
            BigDecimal maximoPenalidad = montoTotal
                    .multiply(BigDecimal.ONE.subtract(reembolsoPct.divide(BigDecimal.valueOf(100))))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal anticipoMonto = new BigDecimal(r.anticipoMonto());
            // The penalty can never take more than what was actually collected as advance.
            BigDecimal montoAprobado = maximoPenalidad.min(anticipoMonto);

            createPenalidadIfAbsent(res, c.user("contador"), maximoPenalidad, montoAprobado, BigDecimal.ZERO,
                    r.fechaCancelacion(), r.motivo());

            // The penalty consumed the entire advance — the refund request is denied.
            createDevolucionIfAbsent(res, c.user("contador"), montoAprobado,
                    "Transferencia bancaria", DevolucionEstado.RECHAZADA);
            count++;
        }
        return count;
    }

    // ── Scenario 8: Cancelled too late — partial penalty, remainder refunded ──────
    // Notice fell inside the penalty window, but the policy's maximum penalty is
    // smaller than the advance actually paid, so part of the deposit is withheld and
    // the rest is genuinely returned to the guest.

    private int seedCancelledLatePartialPenaltyReservations(ScenarioContext c) {
        record R(String email, String nombre, String tel, String propKey, String seasonKey, int canal,
                 String creador, LocalDate in, LocalDate out, int guests, String monto,
                 String anticipoMonto, LocalDate fechaAnticipo, String metodoAnticipo,
                 LocalDate fechaCancelacion, String motivo, String metodoDevolucion) {}

        // r3: Apartaestudio San Fernando, Estándar policy (50% reembolso) → cap = monto*0.50.
        //     monto=1 500 000 → cap=750 000, below the 800 000 advance paid, so 50 000 comes back.
        // r4: Casa Los Alisos, Moderada policy (60% reembolso) → cap = monto*0.40.
        //     monto=2 000 000 → cap=800 000, below the 1 000 000 advance, so 200 000 comes back.
        List<R> rows = List.of(
            new R("wilson.contreras@gmail.com", "Wilson Contreras Mahecha", "3045566783", "apartaSanFdo", "media2025", 0,
                    "recep", LocalDate.of(2025,2,20), LocalDate.of(2025,2,25), 2, "1500000.00",
                    "800000.00", LocalDate.of(2025,1,25), "Transferencia bancaria",
                    LocalDate.of(2025,2,17), "Cancelación 3 días antes del check-in, fuera del plazo de 5 días",
                    "Transferencia bancaria"),
            new R("erika.villanueva@hotmail.com", "Érika Villanueva Ocampo", "3256677895", "casaLosAlisos", "puenteOct25", 1,
                    "auxiliar", LocalDate.of(2025,10,15), LocalDate.of(2025,10,19), 4, "2000000.00",
                    "1000000.00", LocalDate.of(2025,9,26), "Tarjeta de crédito",
                    LocalDate.of(2025,10,10), "Cancelación 5 días antes del check-in, fuera del plazo de 7 días",
                    "Tarjeta de crédito")
        );

        int count = 0;
        for (R r : rows) {
            Reservation res = findOrCreateReservation(r.email(), c.canal(r.canal()), c.season(r.seasonKey()),
                    c.policy(r.propKey()), c.propertyId(r.propKey()), c.user(r.creador()),
                    r.nombre(), r.tel(), r.in(), r.out(), r.guests(),
                    new BigDecimal(r.monto()), ReservationStatus.CANCELLED);

            createAnticipoIfAbsent(res, c.user("contador"), new BigDecimal(r.anticipoMonto()),
                    r.fechaAnticipo(), r.metodoAnticipo(), AnticipoEstado.DEVUELTO);

            BigDecimal montoTotal   = new BigDecimal(r.monto());
            BigDecimal reembolsoPct = c.policy(r.propKey()).getPorcentajeReembolso();
            BigDecimal maximoPenalidad = montoTotal
                    .multiply(BigDecimal.ONE.subtract(reembolsoPct.divide(BigDecimal.valueOf(100))))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal anticipoMonto = new BigDecimal(r.anticipoMonto());
            BigDecimal montoAprobado = maximoPenalidad.min(anticipoMonto);
            BigDecimal remanente     = anticipoMonto.subtract(montoAprobado);

            createPenalidadIfAbsent(res, c.user("contador"), maximoPenalidad, montoAprobado, BigDecimal.ZERO,
                    r.fechaCancelacion(), r.motivo());

            // Only the portion of the advance beyond the approved penalty is returned.
            createDevolucionIfAbsent(res, c.user("contador"), remanente,
                    r.metodoDevolucion(), DevolucionEstado.PROCESADA);
            count++;
        }
        return count;
    }

    // ── Scenario 9: Cancelled before any advance was ever collected ───────────────
    // The guest cancelled before paying a deposit — nothing to penalize or refund,
    // so no financial records exist for this reservation at all.

    private int seedCancelledNeverPaidReservations(ScenarioContext c) {
        Reservation res = findOrCreateReservation("brayan.espinosa@gmail.com", c.canal(2), c.season("media2026"),
                c.policy("aptoCiudadJardin"), c.propertyId("aptoCiudadJardin"), c.user("recep"),
                "Brayan Espinosa Duarte", "3167788992", LocalDate.of(2026,5,5), LocalDate.of(2026,5,8), 3,
                new BigDecimal("910000.00"), ReservationStatus.CANCELLED);
        return res != null ? 1 : 0;
    }

    // ── Find-or-create helpers ─────────────────────────────────────────────────

    private User getOrCreateUser(String email, String password, String nombre, Rol rol, boolean activo) {
        return userRepository.findByUsername(email).orElseGet(() -> {
            User u = new User();
            u.setUsername(email);
            u.setPassword(passwordEncoder.encode(password));
            u.setNombre(nombre);
            u.setRol(rol);
            u.setActivo(activo);
            return userRepository.save(u);
        });
    }

    private Temporada findOrCreateTemporada(String nombre, LocalDate inicio, LocalDate fin) {
        return temporadaRepository.findByNombre(nombre).orElseGet(() -> {
            Temporada t = new Temporada();
            t.setNombre(nombre);
            t.setFechaInicio(inicio);
            t.setFechaFin(fin);
            return temporadaRepository.save(t);
        });
    }

    private Property findOrCreateProperty(String nombre, String direccion, String descripcion) {
        return propertyRepository.findByName(nombre).orElseGet(() -> {
            Property p = new Property();
            p.setName(nombre);
            p.setAddress(direccion);
            p.setDescripcion(descripcion);
            return propertyRepository.save(p);
        });
    }

    private PoliticaCancelacion findOrCreatePolitica(String nombre, Property propiedad,
            BigDecimal porcentajeReembolso, int diasAviso, String descripcion) {
        return politicaRepository.findByNombre(nombre).orElseGet(() -> {
            PoliticaCancelacion p = new PoliticaCancelacion();
            p.setPropiedad(propiedad);
            p.setNombre(nombre);
            p.setDescripcion(descripcion);
            p.setPorcentajeReembolso(porcentajeReembolso);
            p.setDiasAviso(diasAviso);
            return politicaRepository.save(p);
        });
    }

    private Reservation findOrCreateReservation(
            String clienteEmail, Canal canal, Temporada temporada,
            PoliticaCancelacion politica, Long propiedadId, User creador,
            String clienteNombre, String clienteTelefono,
            LocalDate checkIn, LocalDate checkOut, int guests,
            BigDecimal monto, ReservationStatus status) {
        return reservationRepository.findByClienteEmailAndCheckInDate(clienteEmail, checkIn).orElseGet(() ->
                reservationRepository.save(buildReservation(canal, temporada, politica, propiedadId, creador,
                        clienteNombre, clienteEmail, clienteTelefono, checkIn, checkOut, guests, monto, status)));
    }

    private void createAnticipoIfAbsent(Reservation reserva, User usuario,
            BigDecimal monto, LocalDate fechaPago, String metodo, AnticipoEstado estado) {
        if (!anticipoRepository.existsByReservaId(reserva.getId())) {
            anticipoRepository.save(buildAnticipo(reserva, usuario, monto, fechaPago, metodo, estado));
        }
    }

    private void createPenalidadIfAbsent(Reservation reserva, User usuario,
            BigDecimal montoSegun, BigDecimal montoAprobado, BigDecimal montoCondonado,
            LocalDate fechaCancelacion, String motivo) {
        if (!penalidadRepository.existsByReservaId(reserva.getId())) {
            penalidadRepository.save(buildPenalidad(reserva, usuario,
                    montoSegun, montoAprobado, montoCondonado, fechaCancelacion, motivo));
        }
    }

    private void createFacturaIfAbsent(Reservation reserva, User usuario, String numero,
            BigDecimal subtotal, BigDecimal descuento, BigDecimal recargo,
            BigDecimal impuestos, BigDecimal total, InvoiceStatus estado) {
        if (!facturaRepository.existsByReservaId(reserva.getId())) {
            facturaRepository.save(buildFactura(reserva, usuario, numero,
                    subtotal, descuento, recargo, impuestos, total, estado));
        }
    }

    private void createDevolucionIfAbsent(Reservation reserva, User usuario,
            BigDecimal monto, String metodo, DevolucionEstado estado) {
        if (!devolucionRepository.existsByReservaId(reserva.getId())) {
            List<Anticipo> anticipos = anticipoRepository.findByReservaId(reserva.getId());
            if (!anticipos.isEmpty()) {
                devolucionRepository.save(buildDevolucion(reserva, anticipos.get(0),
                        usuario, monto, metodo, estado));
            }
        }
    }

    // ── Entity builders ─────────────────────────────────────────────────────────

    private Reservation buildReservation(Canal canal, Temporada temporada,
            PoliticaCancelacion politica, Long propiedadId, User creador,
            String clienteNombre, String clienteEmail, String clienteTelefono,
            LocalDate checkIn, LocalDate checkOut, int guests,
            BigDecimal monto, ReservationStatus status) {
        Reservation r = new Reservation();
        r.setCanal(canal);
        r.setTemporada(temporada);
        r.setPoliticaCancelacion(politica);
        r.setPropertyId(propiedadId);
        r.setUsuarioCreador(creador);
        r.setClienteNombre(clienteNombre);
        r.setClienteEmail(clienteEmail);
        r.setClienteTelefono(clienteTelefono);
        r.setCheckIn(checkIn);
        r.setCheckOut(checkOut);
        r.setGuestCount(guests);
        r.setMontoTotal(monto);
        r.setStatus(status);
        return r;
    }

    private Anticipo buildAnticipo(Reservation reserva, User usuario,
            BigDecimal monto, LocalDate fechaPago, String metodo, AnticipoEstado estado) {
        Anticipo a = new Anticipo();
        a.setReserva(reserva);
        a.setUsuario(usuario);
        a.setMonto(monto);
        a.setFechaPago(fechaPago);
        a.setMetodoPago(metodo);
        a.setEstado(estado);
        return a;
    }

    private Penalidad buildPenalidad(Reservation reserva, User usuario,
            BigDecimal montoSegun, BigDecimal montoAprobado, BigDecimal montoCondonado,
            LocalDate fechaCancelacion, String motivo) {
        Penalidad p = new Penalidad();
        p.setReserva(reserva);
        p.setUsuario(usuario);
        p.setMontoSegunPolitica(montoSegun);
        p.setMontoAprobado(montoAprobado);
        p.setMontoCondonado(montoCondonado);
        p.setFechaCancelacion(fechaCancelacion);
        p.setMotivo(motivo);
        return p;
    }

    private Factura buildFactura(Reservation reserva, User usuario,
            String numero, BigDecimal subtotal, BigDecimal descuento,
            BigDecimal recargo, BigDecimal impuestos, BigDecimal total,
            InvoiceStatus estado) {
        Factura f = new Factura();
        f.setReserva(reserva);
        f.setUsuario(usuario);
        f.setNumeroFactura(numero);
        f.setSubtotal(subtotal);
        f.setDescuentoAnticipo(descuento);
        f.setRecargoPenalidad(recargo);
        f.setImpuestos(impuestos);
        f.setTotal(total);
        f.setEstado(estado);
        return f;
    }

    private Devolucion buildDevolucion(Reservation reserva, Anticipo anticipo,
            User usuario, BigDecimal monto, String metodo, DevolucionEstado estado) {
        Devolucion d = new Devolucion();
        d.setReserva(reserva);
        d.setAnticipo(anticipo);
        d.setUsuario(usuario);
        d.setMonto(monto);
        d.setMetodo(metodo);
        d.setEstado(estado);
        return d;
    }
}
