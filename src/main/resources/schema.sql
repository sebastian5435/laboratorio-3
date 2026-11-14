CREATE TABLE IF NOT EXISTS clientes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    documento       VARCHAR(20)  NOT NULL UNIQUE,
    nombre          VARCHAR(120) NOT NULL,
    email           VARCHAR(120),
    telefono        VARCHAR(30),
    fecha_registro  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cuentas (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero          VARCHAR(20)    NOT NULL UNIQUE,
    cliente_id      BIGINT         NOT NULL REFERENCES clientes(id),
    tipo            VARCHAR(20)    NOT NULL,          -- AHORROS | CORRIENTE
    saldo           DECIMAL(19, 2) NOT NULL DEFAULT 0,
    estado          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVA', -- ACTIVA | BLOQUEADA
    fecha_apertura  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_saldo_no_negativo CHECK (saldo >= 0)
);

CREATE TABLE IF NOT EXISTS movimientos (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    referencia          VARCHAR(40)    NOT NULL,
    cuenta_id           BIGINT         NOT NULL REFERENCES cuentas(id),
    tipo                VARCHAR(30)    NOT NULL,
    monto               DECIMAL(19, 2) NOT NULL,
    saldo_resultante    DECIMAL(19, 2) NOT NULL,
    cuenta_contraparte  VARCHAR(20),
    descripcion         VARCHAR(255),
    fecha               TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_movimientos_cuenta ON movimientos(cuenta_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_referencia ON movimientos(referencia);

CREATE TABLE IF NOT EXISTS solicitudes_credito (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero              VARCHAR(20)    NOT NULL UNIQUE,
    cliente_id          BIGINT         NOT NULL REFERENCES clientes(id),
    monto               DECIMAL(19, 2) NOT NULL,
    plazo_meses         INT            NOT NULL,
    tasa_mensual        DECIMAL(8, 6)  NOT NULL,
    cuota_mensual       DECIMAL(19, 2) NOT NULL,
    ingresos_mensuales  DECIMAL(19, 2) NOT NULL,
    proposito           VARCHAR(255),
    estado              VARCHAR(20)    NOT NULL, -- PENDIENTE | RECHAZADA | DESEMBOLSADA
    observacion         VARCHAR(255),
    cuenta_desembolso   VARCHAR(20),
    fecha_solicitud     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion    TIMESTAMP
);
