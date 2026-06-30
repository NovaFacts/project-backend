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
 * Populates the database with development/demo sample data when the "dev" Spring profile
 * is active and app.seed-demo-data=true (the default in application-dev.properties).
 *
 * Design rules:
 *  - Each entity group is seeded independently via a find-or-create pattern.
 *  - Restarting the application never creates duplicates — all seed operations are idempotent.
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

        log.info("DevelopmentDataSeeder: verificando y sembrando datos de desarrollo...");

        Map<String, User>                users        = seedUsers();
        Map<String, Temporada>           temporadas   = seedSeasons();
        Map<String, Property>            properties   = seedProperties();
        Map<String, PoliticaCancelacion> policies     = seedPolicies(properties);
        Map<String, Reservation>         reservations = seedReservations(canales, temporadas, policies, users, properties);
        seedAnticipes(reservations, users);
        seedPenalties(reservations, users);
        seedInvoices(reservations, users);
        seedRefunds(reservations, users);

        log.info("DevelopmentDataSeeder: siembra completada — {} usuarios, {} propiedades, {} reservas.",
                userRepository.count(), propertyRepository.count(), reservationRepository.count());
    }

    // ── Users ──────────────────────────────────────────────────────────────────

    private Map<String, User> seedUsers() {
        Rol rolAdmin    = rolRepository.findById(1).orElseThrow();
        Rol rolContador = rolRepository.findById(2).orElseThrow();
        Rol rolAuxiliar = rolRepository.findById(3).orElseThrow();
        Rol rolRecep    = rolRepository.findById(4).orElseThrow();

        // admin@novafacts.com is guaranteed to exist already (AdminUserInitializer or Flyway V8).
        // getOrCreateUser() returns the existing row without re-encoding the password.
        return Map.of(
            "admin",    getOrCreateUser("admin@novafacts.com",     "Admin2024!",  "Administrador Sistema", rolAdmin,    true),
            "contador", getOrCreateUser("contador@novafacts.com",  "Conta2024!",  "María García",          rolContador, true),
            "auxiliar", getOrCreateUser("auxiliar@novafacts.com",  "Aux2024!",    "Carlos López",          rolAuxiliar, true),
            "recep",    getOrCreateUser("recepcion@novafacts.com", "Recep2024!",  "Ana Martínez",          rolRecep,    true)
        );
    }

    // ── Seasons ────────────────────────────────────────────────────────────────

    private Map<String, Temporada> seedSeasons() {
        Temporada alta = findOrCreateTemporada("Temporada Alta 2025",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 8, 31));
        Temporada baja = findOrCreateTemporada("Temporada Baja 2025",
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 11, 30));
        return Map.of("alta", alta, "baja", baja);
    }

    // ── Properties ─────────────────────────────────────────────────────────────

    private Map<String, Property> seedProperties() {
        Property p1 = findOrCreateProperty("Villa El Retiro",
                "Cra 9 #15-30, Bogotá", "Villa con piscina y jardín privado");
        Property p2 = findOrCreateProperty("Casa Bosque",
                "Calle 82 #12-15, Medellín", "Casa campestre rodeada de bosque nativo");
        Property p3 = findOrCreateProperty("Apartamento Centro",
                "Av. El Dorado #85-80, Bogotá", "Apartamento ejecutivo en el centro financiero");
        return Map.of("p1", p1, "p2", p2, "p3", p3);
    }

    // ── Cancellation Policies ──────────────────────────────────────────────────

    private Map<String, PoliticaCancelacion> seedPolicies(Map<String, Property> props) {
        PoliticaCancelacion pol1 = findOrCreatePolitica("Política Estándar",
                props.get("p1"), new BigDecimal("50.00"), 7,  "50% de reembolso con 7 días de aviso");
        PoliticaCancelacion pol2 = findOrCreatePolitica("Política Flexible",
                props.get("p2"), new BigDecimal("80.00"), 3,  "80% de reembolso con 3 días de aviso");
        PoliticaCancelacion pol3 = findOrCreatePolitica("Política Estricta",
                props.get("p3"), new BigDecimal("20.00"), 14, "20% de reembolso con 14 días de aviso");
        return Map.of("pol1", pol1, "pol2", pol2, "pol3", pol3);
    }

    // ── Reservations ───────────────────────────────────────────────────────────

    private Map<String, Reservation> seedReservations(
            List<Canal> canales,
            Map<String, Temporada> temps,
            Map<String, PoliticaCancelacion> pols,
            Map<String, User> users,
            Map<String, Property> props) {

        // Canal order from Flyway V3: 0=Airbnb, 1=Booking, 2=Web propia, 3=Teléfono, 4=WhatsApp
        Reservation r1 = findOrCreateReservation("laura@example.com",
                canales.get(0), temps.get("alta"), pols.get("pol1"), props.get("p1").getId(), users.get("recep"),
                "Laura Rodríguez",  "3001112233",
                LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 17), 2,
                new BigDecimal("1400000.00"), ReservationStatus.COMPLETED);

        Reservation r2 = findOrCreateReservation("pedro@example.com",
                canales.get(1), temps.get("alta"), pols.get("pol2"), props.get("p2").getId(), users.get("recep"),
                "Pedro Álvarez",    "3102223344",
                LocalDate.of(2025, 7, 5), LocalDate.of(2025, 7, 12), 4,
                new BigDecimal("2100000.00"), ReservationStatus.COMPLETED);

        Reservation r3 = findOrCreateReservation("sofia@example.com",
                canales.get(2), temps.get("baja"), pols.get("pol3"), props.get("p3").getId(), users.get("auxiliar"),
                "Sofía Herrera",    "3203334455",
                LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 23), 1,
                new BigDecimal("600000.00"), ReservationStatus.CONFIRMED);

        Reservation r4 = findOrCreateReservation("andres@example.com",
                canales.get(0), temps.get("alta"), pols.get("pol1"), props.get("p1").getId(), users.get("recep"),
                "Andrés Castro",    "3154445566",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 8), 3,
                new BigDecimal("1750000.00"), ReservationStatus.CANCELLED);

        Reservation r5 = findOrCreateReservation("vale@example.com",
                canales.get(3), temps.get("baja"), pols.get("pol2"), props.get("p2").getId(), users.get("auxiliar"),
                "Valentina Ruiz",   "3005556677",
                LocalDate.of(2025, 10, 10), LocalDate.of(2025, 10, 15), 2,
                new BigDecimal("1250000.00"), ReservationStatus.CONFIRMED);

        Reservation r6 = findOrCreateReservation("miguel@example.com",
                canales.get(4), temps.get("alta"), pols.get("pol3"), props.get("p3").getId(), users.get("recep"),
                "Miguel Torres",    "3136667788",
                LocalDate.of(2025, 7, 20), LocalDate.of(2025, 7, 25), 2,
                new BigDecimal("1000000.00"), ReservationStatus.COMPLETED);

        Reservation r7 = findOrCreateReservation("camila@example.com",
                canales.get(1), temps.get("baja"), pols.get("pol1"), props.get("p1").getId(), users.get("recep"),
                "Camila Vargas",    "3177778899",
                LocalDate.of(2025, 11, 3), LocalDate.of(2025, 11, 7), 3,
                new BigDecimal("900000.00"), ReservationStatus.CANCELLED);

        Reservation r8 = findOrCreateReservation("santi@example.com",
                canales.get(2), temps.get("baja"), pols.get("pol2"), props.get("p2").getId(), users.get("auxiliar"),
                "Santiago Mora",    "3008889900",
                LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 10), 1,
                new BigDecimal("750000.00"), ReservationStatus.CONFIRMED);

        return Map.of(
                "r1", r1, "r2", r2, "r3", r3, "r4", r4,
                "r5", r5, "r6", r6, "r7", r7, "r8", r8);
    }

    // ── Advances (Anticipos) ───────────────────────────────────────────────────

    private void seedAnticipes(Map<String, Reservation> reservations, Map<String, User> users) {
        createAnticipoIfAbsent(reservations.get("r1"), users.get("contador"),
                new BigDecimal("700000.00"),  LocalDate.of(2025, 6, 1),   "Transferencia bancaria", AnticipoEstado.APLICADO);
        createAnticipoIfAbsent(reservations.get("r2"), users.get("contador"),
                new BigDecimal("1050000.00"), LocalDate.of(2025, 6, 25),  "Tarjeta crédito",        AnticipoEstado.APLICADO);
        createAnticipoIfAbsent(reservations.get("r4"), users.get("contador"),
                new BigDecimal("875000.00"),  LocalDate.of(2025, 7, 20),  "PSE",                    AnticipoEstado.APLICADO);
        createAnticipoIfAbsent(reservations.get("r6"), users.get("auxiliar"),
                new BigDecimal("500000.00"),  LocalDate.of(2025, 7, 10),  "Efectivo",               AnticipoEstado.APLICADO);
        createAnticipoIfAbsent(reservations.get("r7"), users.get("contador"),
                new BigDecimal("450000.00"),  LocalDate.of(2025, 10, 20), "Transferencia bancaria", AnticipoEstado.APLICADO);
    }

    // ── Penalties (Penalidades) ────────────────────────────────────────────────

    private void seedPenalties(Map<String, Reservation> reservations, Map<String, User> users) {
        createPenalidadIfAbsent(reservations.get("r4"), users.get("contador"),
                new BigDecimal("875000.00"), new BigDecimal("875000.00"), BigDecimal.ZERO,
                LocalDate.of(2025, 7, 28),  "Cancelación tardía sin justificación");
        createPenalidadIfAbsent(reservations.get("r7"), users.get("auxiliar"),
                new BigDecimal("180000.00"), new BigDecimal("180000.00"), BigDecimal.ZERO,
                LocalDate.of(2025, 10, 25), "Cancelación sin aviso previo suficiente");
    }

    // ── Invoices (Facturas) ────────────────────────────────────────────────────

    private void seedInvoices(Map<String, Reservation> reservations, Map<String, User> users) {
        createFacturaIfAbsent(reservations.get("r1"), users.get("contador"), "FAC-SEED-0001",
                new BigDecimal("1400000.00"), new BigDecimal("700000.00"), BigDecimal.ZERO,
                new BigDecimal("266000.00"),  new BigDecimal("966000.00"),  InvoiceStatus.PAID);
        createFacturaIfAbsent(reservations.get("r2"), users.get("contador"), "FAC-SEED-0002",
                new BigDecimal("2100000.00"), new BigDecimal("1050000.00"), BigDecimal.ZERO,
                new BigDecimal("399000.00"),  new BigDecimal("1449000.00"), InvoiceStatus.PAID);
        createFacturaIfAbsent(reservations.get("r6"), users.get("auxiliar"), "FAC-SEED-0003",
                new BigDecimal("1000000.00"), new BigDecimal("500000.00"), BigDecimal.ZERO,
                new BigDecimal("190000.00"),  new BigDecimal("690000.00"),  InvoiceStatus.PENDING);
    }

    // ── Refunds (Devoluciones) ─────────────────────────────────────────────────

    private void seedRefunds(Map<String, Reservation> reservations, Map<String, User> users) {
        // dev1: refund for r4's anticipo — was rejected (monto 0, refund denied)
        createDevolucionIfAbsent(reservations.get("r4"), users.get("contador"),
                new BigDecimal("0.00"), "Transferencia bancaria", DevolucionEstado.RECHAZADA);
        // dev2: refund for r7's anticipo — was processed (partial refund via PSE)
        createDevolucionIfAbsent(reservations.get("r7"), users.get("contador"),
                new BigDecimal("360000.00"), "PSE", DevolucionEstado.PROCESADA);
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

    // ── Entity builders (unchanged from original) ──────────────────────────────

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
