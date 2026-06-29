package com.novafacts.backend.config;

import com.novafacts.backend.anticipo.entity.Anticipo;
import com.novafacts.backend.anticipo.repository.AnticipoRepository;
import com.novafacts.backend.auth.entity.User;
import com.novafacts.backend.auth.repository.UserRepository;
import com.novafacts.backend.canal.entity.Canal;
import com.novafacts.backend.canal.repository.CanalRepository;
import com.novafacts.backend.devolucion.entity.Devolucion;
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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

@Component
@Profile("dev")
public class DevelopmentDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentDataSeeder.class);
    private static final Random RNG = new Random(42);

    private final UserRepository         userRepository;
    private final RolRepository          rolRepository;
    private final CanalRepository        canalRepository;
    private final TemporadaRepository    temporadaRepository;
    private final PropertyRepository     propertyRepository;
    private final PoliticaCancelacionRepository politicaRepository;
    private final ReservationRepository  reservationRepository;
    private final AnticipoRepository     anticipoRepository;
    private final PenalidadRepository    penalidadRepository;
    private final FacturaRepository      facturaRepository;
    private final DevolucionRepository   devolucionRepository;
    private final PasswordEncoder        passwordEncoder;

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
        this.userRepository      = userRepository;
        this.rolRepository       = rolRepository;
        this.canalRepository     = canalRepository;
        this.temporadaRepository = temporadaRepository;
        this.propertyRepository  = propertyRepository;
        this.politicaRepository  = politicaRepository;
        this.reservationRepository = reservationRepository;
        this.anticipoRepository  = anticipoRepository;
        this.penalidadRepository = penalidadRepository;
        this.facturaRepository   = facturaRepository;
        this.devolucionRepository = devolucionRepository;
        this.passwordEncoder     = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (reservationRepository.count() > 0) {
            log.info("DevelopmentDataSeeder: datos ya presentes, omitiendo siembra.");
            return;
        }

        log.info("DevelopmentDataSeeder: iniciando siembra de datos de desarrollo...");

        // Roles — ya sembrados por Flyway V2 (1=Administrador, 2=Contador,
        //         3=Auxiliar contable, 4=Recepcionista)
        Rol rolAdmin    = rolRepository.findById(1).orElseThrow();
        Rol rolContador = rolRepository.findById(2).orElseThrow();
        Rol rolAuxiliar = rolRepository.findById(3).orElseThrow();
        Rol rolRecep    = rolRepository.findById(4).orElseThrow();

        // Usuarios
        User admin    = getOrCreateUser("admin@novafacts.com",     "Admin2024!",    "Administrador Sistema", rolAdmin,    true);
        User contador = getOrCreateUser("contador@novafacts.com",  "Conta2024!",    "María García",          rolContador, true);
        User auxiliar = getOrCreateUser("auxiliar@novafacts.com",  "Aux2024!",      "Carlos López",          rolAuxiliar, true);
        User recep    = getOrCreateUser("recepcion@novafacts.com", "Recep2024!",    "Ana Martínez",          rolRecep,    true);

        // Canales — ya sembrados por Flyway V3
        List<Canal> canales = canalRepository.findAll();

        // Temporadas
        Temporada alta = new Temporada();
        alta.setNombre("Temporada Alta 2025");
        alta.setFechaInicio(LocalDate.of(2025, 6, 1));
        alta.setFechaFin(LocalDate.of(2025, 8, 31));
        alta = temporadaRepository.save(alta);

        Temporada baja = new Temporada();
        baja.setNombre("Temporada Baja 2025");
        baja.setFechaInicio(LocalDate.of(2025, 9, 1));
        baja.setFechaFin(LocalDate.of(2025, 11, 30));
        baja = temporadaRepository.save(baja);

        // Propiedades
        Property p1 = new Property();
        p1.setName("Villa El Retiro");
        p1.setAddress("Cra 9 #15-30, Bogotá");
        p1.setDescripcion("Villa con piscina y jardín privado");
        p1 = propertyRepository.save(p1);

        Property p2 = new Property();
        p2.setName("Casa Bosque");
        p2.setAddress("Calle 82 #12-15, Medellín");
        p2.setDescripcion("Casa campestre rodeada de bosque nativo");
        p2 = propertyRepository.save(p2);

        Property p3 = new Property();
        p3.setName("Apartamento Centro");
        p3.setAddress("Av. El Dorado #85-80, Bogotá");
        p3.setDescripcion("Apartamento ejecutivo en el centro financiero");
        p3 = propertyRepository.save(p3);

        // Políticas de cancelación
        PoliticaCancelacion pol1 = new PoliticaCancelacion();
        pol1.setPropiedad(p1);
        pol1.setNombre("Política Estándar");
        pol1.setDescripcion("50% de reembolso con 7 días de aviso");
        pol1.setPorcentajeReembolso(new BigDecimal("50.00"));
        pol1.setDiasAviso(7);
        pol1 = politicaRepository.save(pol1);

        PoliticaCancelacion pol2 = new PoliticaCancelacion();
        pol2.setPropiedad(p2);
        pol2.setNombre("Política Flexible");
        pol2.setDescripcion("80% de reembolso con 3 días de aviso");
        pol2.setPorcentajeReembolso(new BigDecimal("80.00"));
        pol2.setDiasAviso(3);
        pol2 = politicaRepository.save(pol2);

        PoliticaCancelacion pol3 = new PoliticaCancelacion();
        pol3.setPropiedad(p3);
        pol3.setNombre("Política Estricta");
        pol3.setDescripcion("20% de reembolso con 14 días de aviso");
        pol3.setPorcentajeReembolso(new BigDecimal("20.00"));
        pol3.setDiasAviso(14);
        pol3 = politicaRepository.save(pol3);

        // Reservas — 8 reservas distribuidas en las tres propiedades
        Reservation r1 = buildReservation(canales.get(0), alta, pol1, p1.getId(), recep,
                "Laura Rodríguez", "laura@example.com", "3001112233",
                LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 17), 2,
                new BigDecimal("1400000.00"), ReservationStatus.COMPLETED);
        r1 = reservationRepository.save(r1);

        Reservation r2 = buildReservation(canales.get(1), alta, pol2, p2.getId(), recep,
                "Pedro Álvarez", "pedro@example.com", "3102223344",
                LocalDate.of(2025, 7, 5), LocalDate.of(2025, 7, 12), 4,
                new BigDecimal("2100000.00"), ReservationStatus.COMPLETED);
        r2 = reservationRepository.save(r2);

        Reservation r3 = buildReservation(canales.get(2), baja, pol3, p3.getId(), auxiliar,
                "Sofía Herrera", "sofia@example.com", "3203334455",
                LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 23), 1,
                new BigDecimal("600000.00"), ReservationStatus.CONFIRMED);
        r3 = reservationRepository.save(r3);

        Reservation r4 = buildReservation(canales.get(0), alta, pol1, p1.getId(), recep,
                "Andrés Castro", "andres@example.com", "3154445566",
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 8), 3,
                new BigDecimal("1750000.00"), ReservationStatus.CANCELLED);
        r4 = reservationRepository.save(r4);

        Reservation r5 = buildReservation(canales.get(3), baja, pol2, p2.getId(), auxiliar,
                "Valentina Ruiz", "vale@example.com", "3005556677",
                LocalDate.of(2025, 10, 10), LocalDate.of(2025, 10, 15), 2,
                new BigDecimal("1250000.00"), ReservationStatus.CONFIRMED);
        r5 = reservationRepository.save(r5);

        Reservation r6 = buildReservation(canales.get(4), alta, pol3, p3.getId(), recep,
                "Miguel Torres", "miguel@example.com", "3136667788",
                LocalDate.of(2025, 7, 20), LocalDate.of(2025, 7, 25), 2,
                new BigDecimal("1000000.00"), ReservationStatus.COMPLETED);
        r6 = reservationRepository.save(r6);

        Reservation r7 = buildReservation(canales.get(1), baja, pol1, p1.getId(), recep,
                "Camila Vargas", "camila@example.com", "3177778899",
                LocalDate.of(2025, 11, 3), LocalDate.of(2025, 11, 7), 3,
                new BigDecimal("900000.00"), ReservationStatus.CANCELLED);
        r7 = reservationRepository.save(r7);

        Reservation r8 = buildReservation(canales.get(2), baja, pol2, p2.getId(), auxiliar,
                "Santiago Mora", "santi@example.com", "3008889900",
                LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 10), 1,
                new BigDecimal("750000.00"), ReservationStatus.CONFIRMED);
        r8 = reservationRepository.save(r8);

        // Anticipos — para reservas CONFIRMED y COMPLETED
        Anticipo a1 = buildAnticipo(r1, contador, new BigDecimal("700000.00"),
                LocalDate.of(2025, 6, 1), "Transferencia bancaria", "aplicado");
        a1 = anticipoRepository.save(a1);

        Anticipo a2 = buildAnticipo(r2, contador, new BigDecimal("1050000.00"),
                LocalDate.of(2025, 6, 25), "Tarjeta crédito", "aplicado");
        a2 = anticipoRepository.save(a2);

        Anticipo a3 = buildAnticipo(r4, contador, new BigDecimal("875000.00"),
                LocalDate.of(2025, 7, 20), "PSE", "aplicado");
        a3 = anticipoRepository.save(a3);

        Anticipo a4 = buildAnticipo(r6, auxiliar, new BigDecimal("500000.00"),
                LocalDate.of(2025, 7, 10), "Efectivo", "aplicado");
        a4 = anticipoRepository.save(a4);

        Anticipo a5 = buildAnticipo(r7, contador, new BigDecimal("450000.00"),
                LocalDate.of(2025, 10, 20), "Transferencia bancaria", "aplicado");
        a5 = anticipoRepository.save(a5);

        // Penalidades — para reservas CANCELLED
        Penalidad pen1 = buildPenalidad(r4, contador,
                new BigDecimal("875000.00"), new BigDecimal("875000.00"), BigDecimal.ZERO,
                LocalDate.of(2025, 7, 28), "Cancelación tardía sin justificación");
        penalidadRepository.save(pen1);

        Penalidad pen2 = buildPenalidad(r7, auxiliar,
                new BigDecimal("180000.00"), new BigDecimal("180000.00"), BigDecimal.ZERO,
                LocalDate.of(2025, 10, 25), "Cancelación sin aviso previo suficiente");
        penalidadRepository.save(pen2);

        // Facturas — para reservas COMPLETED
        Factura fac1 = buildFactura(r1, contador, "FAC-SEED-0001",
                new BigDecimal("1400000.00"), new BigDecimal("700000.00"),
                BigDecimal.ZERO, new BigDecimal("266000.00"),
                new BigDecimal("966000.00"), InvoiceStatus.PAID);
        facturaRepository.save(fac1);

        Factura fac2 = buildFactura(r2, contador, "FAC-SEED-0002",
                new BigDecimal("2100000.00"), new BigDecimal("1050000.00"),
                BigDecimal.ZERO, new BigDecimal("399000.00"),
                new BigDecimal("1449000.00"), InvoiceStatus.PAID);
        facturaRepository.save(fac2);

        Factura fac3 = buildFactura(r6, auxiliar, "FAC-SEED-0003",
                new BigDecimal("1000000.00"), new BigDecimal("500000.00"),
                BigDecimal.ZERO, new BigDecimal("190000.00"),
                new BigDecimal("690000.00"), InvoiceStatus.PENDING);
        facturaRepository.save(fac3);

        // Devoluciones — para anticipos de reservas canceladas
        Devolucion dev1 = buildDevolucion(r4, a3, contador,
                new BigDecimal("0.00"), "Transferencia bancaria", "rechazada");
        devolucionRepository.save(dev1);

        Devolucion dev2 = buildDevolucion(r7, a5, contador,
                new BigDecimal("360000.00"), "PSE", "procesada");
        devolucionRepository.save(dev2);

        log.info("DevelopmentDataSeeder: siembra completada — {} usuarios, {} propiedades, {} reservas.",
                userRepository.count(), propertyRepository.count(), reservationRepository.count());
    }

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
            BigDecimal monto, LocalDate fechaPago, String metodo, String estado) {
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
            User usuario, BigDecimal monto, String metodo, String estado) {
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
