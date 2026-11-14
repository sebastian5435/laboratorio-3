package com.tdea.mcp_server_banco.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.tdea.mcp_server_banco.model.Cliente;
import com.tdea.mcp_server_banco.model.Cuenta;
import com.tdea.mcp_server_banco.model.Movimiento;
import com.tdea.mcp_server_banco.model.SolicitudCredito;

/**
 * Acceso a la base de datos con JdbcTemplate (SQL explicito).
 */
@Repository
public class BancoRepository {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SELECT_CUENTA = """
            SELECT c.id, c.numero, c.tipo, c.saldo, c.estado, c.fecha_apertura,
                   cl.documento, cl.nombre
              FROM cuentas c
              JOIN clientes cl ON cl.id = c.cliente_id
            """;

    private static final String SELECT_SOLICITUD = """
            SELECT s.*, cl.documento, cl.nombre
              FROM solicitudes_credito s
              JOIN clientes cl ON cl.id = s.cliente_id
            """;

    private final JdbcTemplate jdbc;

    public BancoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ clientes

    public long contarClientes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Long.class);
    }

    public void insertarCliente(String documento, String nombre, String email, String telefono) {
        jdbc.update("INSERT INTO clientes (documento, nombre, email, telefono) VALUES (?, ?, ?, ?)",
                documento, nombre, email, telefono);
    }

    public Optional<Cliente> buscarCliente(String documento) {
        return jdbc.query("SELECT * FROM clientes WHERE documento = ?", CLIENTE, documento)
                .stream().findFirst();
    }

    // ------------------------------------------------------------------ cuentas

    public boolean existeCuenta(String numero) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cuentas WHERE numero = ?", Long.class, numero) > 0;
    }

    public void insertarCuenta(String numero, long clienteId, String tipo) {
        jdbc.update("INSERT INTO cuentas (numero, cliente_id, tipo, saldo) VALUES (?, ?, ?, 0)",
                numero, clienteId, tipo);
    }

    public Optional<Cuenta> buscarCuenta(String numero) {
        return jdbc.query(SELECT_CUENTA + " WHERE c.numero = ?", CUENTA, numero).stream().findFirst();
    }

    /**
     * Bloquea la fila de la cuenta (SELECT ... FOR UPDATE) hasta el final de la
     * transaccion actual, para que dos operaciones concurrentes no lean el mismo saldo.
     */
    public boolean bloquearFilaCuenta(String numero) {
        return !jdbc.queryForList("SELECT id FROM cuentas WHERE numero = ? FOR UPDATE", Long.class, numero)
                .isEmpty();
    }

    public List<Cuenta> cuentasDeCliente(String documento) {
        return jdbc.query(SELECT_CUENTA + " WHERE cl.documento = ? ORDER BY c.fecha_apertura, c.numero",
                CUENTA, documento);
    }

    public void actualizarSaldo(long cuentaId, BigDecimal saldo) {
        jdbc.update("UPDATE cuentas SET saldo = ? WHERE id = ?", saldo, cuentaId);
    }

    public void actualizarEstado(long cuentaId, String estado) {
        jdbc.update("UPDATE cuentas SET estado = ? WHERE id = ?", estado, cuentaId);
    }

    // ------------------------------------------------------------------ movimientos

    public void insertarMovimiento(String referencia, long cuentaId, String tipo, BigDecimal monto,
            BigDecimal saldoResultante, String contraparte, String descripcion) {
        jdbc.update("""
                INSERT INTO movimientos
                    (referencia, cuenta_id, tipo, monto, saldo_resultante, cuenta_contraparte, descripcion)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, referencia, cuentaId, tipo, monto, saldoResultante, contraparte, descripcion);
    }

    public List<Movimiento> movimientosDeCuenta(String numero, int limite) {
        return jdbc.query("""
                SELECT m.*, c.numero
                  FROM movimientos m
                  JOIN cuentas c ON c.id = m.cuenta_id
                 WHERE c.numero = ?
                 ORDER BY m.fecha DESC, m.id DESC
                 LIMIT ?
                """, MOVIMIENTO, numero, limite);
    }

    // ------------------------------------------------------------------ creditos

    public boolean existeSolicitud(String numero) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM solicitudes_credito WHERE numero = ?", Long.class,
                numero) > 0;
    }

    public void insertarSolicitud(String numero, long clienteId, BigDecimal monto, int plazoMeses,
            BigDecimal tasaMensual, BigDecimal cuotaMensual, BigDecimal ingresos, String proposito,
            String estado, String observacion) {
        jdbc.update("""
                INSERT INTO solicitudes_credito
                    (numero, cliente_id, monto, plazo_meses, tasa_mensual, cuota_mensual,
                     ingresos_mensuales, proposito, estado, observacion, fecha_resolucion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'PENDIENTE' THEN NULL ELSE CURRENT_TIMESTAMP END)
                """, numero, clienteId, monto, plazoMeses, tasaMensual, cuotaMensual, ingresos, proposito,
                estado, observacion, estado);
    }

    public Optional<SolicitudCredito> buscarSolicitud(String numero) {
        return jdbc.query(SELECT_SOLICITUD + " WHERE s.numero = ?", SOLICITUD, numero).stream().findFirst();
    }

    public boolean bloquearFilaSolicitud(String numero) {
        return !jdbc.queryForList("SELECT id FROM solicitudes_credito WHERE numero = ? FOR UPDATE", Long.class,
                numero).isEmpty();
    }

    public List<SolicitudCredito> solicitudesDeCliente(String documento) {
        return jdbc.query(SELECT_SOLICITUD + " WHERE cl.documento = ? ORDER BY s.fecha_solicitud DESC, s.id DESC",
                SOLICITUD, documento);
    }

    public List<SolicitudCredito> solicitudesPorEstado(String estado) {
        return jdbc.query(SELECT_SOLICITUD + " WHERE s.estado = ? ORDER BY s.fecha_solicitud, s.id",
                SOLICITUD, estado);
    }

    public void resolverSolicitud(String numero, String estado, String observacion, String cuentaDesembolso) {
        jdbc.update("""
                UPDATE solicitudes_credito
                   SET estado = ?, observacion = ?, cuenta_desembolso = ?, fecha_resolucion = CURRENT_TIMESTAMP
                 WHERE numero = ?
                """, estado, observacion, cuentaDesembolso, numero);
    }

    // ------------------------------------------------------------------ mapeos

    private static String fecha(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().format(FECHA);
    }

    private static final RowMapper<Cliente> CLIENTE = (ResultSet rs, int n) -> new Cliente(
            rs.getLong("id"),
            rs.getString("documento"),
            rs.getString("nombre"),
            rs.getString("email"),
            rs.getString("telefono"),
            fecha(rs.getTimestamp("fecha_registro")));

    private static final RowMapper<Cuenta> CUENTA = (ResultSet rs, int n) -> new Cuenta(
            rs.getLong("id"),
            rs.getString("numero"),
            rs.getString("tipo"),
            rs.getBigDecimal("saldo"),
            rs.getString("estado"),
            rs.getString("documento"),
            rs.getString("nombre"),
            fecha(rs.getTimestamp("fecha_apertura")));

    private static final RowMapper<Movimiento> MOVIMIENTO = (ResultSet rs, int n) -> new Movimiento(
            rs.getString("referencia"),
            rs.getString("numero"),
            rs.getString("tipo"),
            rs.getBigDecimal("monto"),
            rs.getBigDecimal("saldo_resultante"),
            rs.getString("cuenta_contraparte"),
            rs.getString("descripcion"),
            fecha(rs.getTimestamp("fecha")));

    private static final RowMapper<SolicitudCredito> SOLICITUD = BancoRepository::mapSolicitud;

    private static SolicitudCredito mapSolicitud(ResultSet rs, int n) throws SQLException {
        return new SolicitudCredito(
                rs.getString("numero"),
                rs.getString("documento"),
                rs.getString("nombre"),
                rs.getBigDecimal("monto"),
                rs.getInt("plazo_meses"),
                rs.getBigDecimal("tasa_mensual"),
                rs.getBigDecimal("cuota_mensual"),
                rs.getBigDecimal("ingresos_mensuales"),
                rs.getString("proposito"),
                rs.getString("estado"),
                rs.getString("observacion"),
                rs.getString("cuenta_desembolso"),
                fecha(rs.getTimestamp("fecha_solicitud")),
                fecha(rs.getTimestamp("fecha_resolucion")));
    }
}
