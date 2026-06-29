-- V1__baseline.sql
-- Snapshot of the database schema as created by Hibernate ddl-auto=update
-- from the entity files at Sprint 0 (2026-06-28).
-- On existing databases Flyway baselines this version without executing it.
-- On fresh installations this migration creates the initial schema.

CREATE TABLE IF NOT EXISTS usuario (
    id            BIGSERIAL     PRIMARY KEY,
    email         VARCHAR(150)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    nombre        VARCHAR(100)  NOT NULL,
    rol_id        INTEGER       NOT NULL,
    activo        BOOLEAN       NOT NULL DEFAULT true,
    creado_en     TIMESTAMP     NOT NULL
);

CREATE TABLE IF NOT EXISTS huesped (
    id               BIGSERIAL    PRIMARY KEY,
    primer_nombre    VARCHAR(100) NOT NULL,
    apellido         VARCHAR(100) NOT NULL,
    tipo_documento   VARCHAR(50)  NOT NULL,
    numero_documento VARCHAR(50)  NOT NULL UNIQUE,
    email            VARCHAR(150),
    telefono         VARCHAR(30),
    creado_en        TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS propiedad (
    id               BIGSERIAL     PRIMARY KEY,
    nombre           VARCHAR(150)  NOT NULL UNIQUE,
    direccion        VARCHAR(250)  NOT NULL,
    ciudad           VARCHAR(100)  NOT NULL,
    capacidad        INTEGER       NOT NULL,
    precio_por_noche DECIMAL(15,2) NOT NULL,
    creado_en        TIMESTAMP     NOT NULL
);

CREATE TABLE IF NOT EXISTS reserva (
    id                 BIGSERIAL   PRIMARY KEY,
    huesped_id         BIGINT      NOT NULL,
    propiedad_id       BIGINT      NOT NULL,
    fecha_inicio       DATE        NOT NULL,
    fecha_fin          DATE        NOT NULL,
    cantidad_huespedes INTEGER     NOT NULL,
    estado             VARCHAR(20) NOT NULL,
    creado_en          TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reserva_propiedad_id ON reserva(propiedad_id);

CREATE TABLE IF NOT EXISTS factura (
    id         BIGSERIAL     PRIMARY KEY,
    reserva_id BIGINT        NOT NULL UNIQUE,
    subtotal   DECIMAL(15,2) NOT NULL,
    iva        DECIMAL(15,2) NOT NULL,
    total      DECIMAL(15,2) NOT NULL,
    estado     VARCHAR(20)   NOT NULL,
    version    BIGINT,
    creado_en  TIMESTAMP     NOT NULL
);

CREATE TABLE IF NOT EXISTS pago (
    id          BIGSERIAL     PRIMARY KEY,
    factura_id  BIGINT        NOT NULL UNIQUE,
    monto       DECIMAL(15,2) NOT NULL,
    metodo_pago VARCHAR(20)   NOT NULL,
    referencia  VARCHAR(100),
    pagado_en   TIMESTAMP     NOT NULL,
    version     BIGINT,
    creado_en   TIMESTAMP     NOT NULL
);
