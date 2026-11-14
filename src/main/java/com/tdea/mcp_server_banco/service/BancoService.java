package com.tdea.mcp_server_banco.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tdea.mcp_server_banco.model.BancoException;
import com.tdea.mcp_server_banco.model.Cliente;
import com.tdea.mcp_server_banco.model.ClienteConCuentas;
import com.tdea.mcp_server_banco.model.Cuenta;
import com.tdea.mcp_server_banco.model.Movimiento;
import com.tdea.mcp_server_banco.model.ResultadoTransaccion;
import com.tdea.mcp_server_banco.repository.BancoRepository;

/**
 * Logica de negocio de clientes, cuentas y transacciones.
 * Cada metodo publico es una transaccion de base de datos: si algo falla
 * (por ejemplo saldo insuficiente a mitad de una transferencia) se hace rollback completo.
 */
@Service
@Transactional
public class BancoService {

    public static final BigDecimal MONTO_MAXIMO = new BigDecimal("100000000"); // 100 millones COP
    private static final DateTimeFormatter REF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BancoRepository repo;

    public BancoService(BancoRepository repo) {
        this.repo = repo;
    }

    // ================================================================== clientes

    public Cliente registrarCliente(String documento, String nombre, String email, String telefono) {
        String doc = requerido(documento, "documento").replaceAll("[\\s.]", "");
        String nom = requerido(nombre, "nombre");
        if (!doc.matches("\\d{5,15}")) {
            throw new BancoException("El documento debe tener entre 5 y 15 digitos numericos.");
        }
        if (repo.buscarCliente(doc).isPresent()) {
            throw new BancoException("Ya existe un cliente con documento " + doc + ".");
        }
        repo.insertarCliente(doc, nom, vacioANull(email), vacioANull(telefono));
        return repo.buscarCliente(doc).orElseThrow();
    }

    @Transactional(readOnly = true)
    public ClienteConCuentas consultarCliente(String documento) {
        Cliente cliente = cliente(documento);
        return new ClienteConCuentas(cliente, repo.cuentasDeCliente(cliente.documento()));
    }

    public Cliente cliente(String documento) {
        String doc = requerido(documento, "documento").replaceAll("[\\s.]", "");
        return repo.buscarCliente(doc)
                .orElseThrow(() -> new BancoException("No existe un cliente con documento " + doc + "."));
    }

    // ================================================================== cuentas

    public Cuenta abrirCuenta(String documento, String tipo, BigDecimal depositoInicial) {
        Cliente cliente = cliente(documento);
        String tipoCuenta = requerido(tipo, "tipo").toUpperCase(Locale.ROOT);
        if (!tipoCuenta.equals("AHORROS") && !tipoCuenta.equals("CORRIENTE")) {
            throw new BancoException("Tipo de cuenta invalido: use AHORROS o CORRIENTE.");
        }
        if (depositoInicial != null && depositoInicial.signum() < 0) {
            throw new BancoException("El deposito inicial no puede ser negativo.");
        }
        String numero = nuevoNumeroCuenta(tipoCuenta);
        repo.insertarCuenta(numero, cliente.id(), tipoCuenta);

        if (depositoInicial != null && depositoInicial.signum() > 0) {
            consignar(numero, depositoInicial, "Deposito de apertura");
        }
        return repo.buscarCuenta(numero).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Cuenta consultarCuenta(String numero) {
        return cuenta(numero);
    }

    public Cuenta cambiarEstadoCuenta(String numero, String estado) {
        String nuevo = requerido(estado, "estado").toUpperCase(Locale.ROOT);
        if (!nuevo.equals("ACTIVA") && !nuevo.equals("BLOQUEADA")) {
            throw new BancoException("Estado invalido: use ACTIVA o BLOQUEADA.");
        }
        Cuenta cuenta = cuentaBloqueada(numero);
        repo.actualizarEstado(cuenta.id(), nuevo);
        return repo.buscarCuenta(cuenta.numero()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Movimiento> movimientos(String numero, Integer limite) {
        Cuenta cuenta = cuenta(numero);
        int n = (limite == null || limite <= 0) ? 10 : Math.min(limite, 100);
        return repo.movimientosDeCuenta(cuenta.numero(), n);
    }

    // ================================================================== transacciones

    public ResultadoTransaccion consignar(String numero, BigDecimal monto, String descripcion) {
        BigDecimal valor = validarMonto(monto);
        Cuenta cuenta = cuentaActivaBloqueada(numero);
        String ref = nuevaReferencia();
        BigDecimal saldo = acreditar(cuenta, valor, "CONSIGNACION", ref, null,
                textoODefecto(descripcion, "Consignacion"));
        return new ResultadoTransaccion(ref, "CONSIGNACION", valor, null, null, cuenta.numero(), saldo,
                "Consignacion exitosa de " + pesos(valor) + " en la cuenta " + cuenta.numero() + ".");
    }

    public ResultadoTransaccion retirar(String numero, BigDecimal monto, String descripcion) {
        BigDecimal valor = validarMonto(monto);
        Cuenta cuenta = cuentaActivaBloqueada(numero);
        String ref = nuevaReferencia();
        BigDecimal saldo = debitar(cuenta, valor, "RETIRO", ref, null, textoODefecto(descripcion, "Retiro"));
        return new ResultadoTransaccion(ref, "RETIRO", valor, cuenta.numero(), saldo, null, null,
                "Retiro exitoso de " + pesos(valor) + " de la cuenta " + cuenta.numero() + ".");
    }

    public ResultadoTransaccion transferir(String origen, String destino, BigDecimal monto, String descripcion) {
        BigDecimal valor = validarMonto(monto);
        String numOrigen = normalizarCuenta(origen, "cuenta origen");
        String numDestino = normalizarCuenta(destino, "cuenta destino");
        if (numOrigen.equals(numDestino)) {
            throw new BancoException("La cuenta origen y la cuenta destino no pueden ser la misma.");
        }

        // Se bloquean siempre en el mismo orden (por numero) para evitar interbloqueos
        // cuando dos transferencias cruzadas ocurren al mismo tiempo.
        boolean origenPrimero = numOrigen.compareTo(numDestino) < 0;
        Cuenta primera = cuentaActivaBloqueada(origenPrimero ? numOrigen : numDestino);
        Cuenta segunda = cuentaActivaBloqueada(origenPrimero ? numDestino : numOrigen);
        Cuenta cuentaOrigen = origenPrimero ? primera : segunda;
        Cuenta cuentaDestino = origenPrimero ? segunda : primera;

        String ref = nuevaReferencia();
        String desc = textoODefecto(descripcion, "Transferencia");
        BigDecimal saldoOrigen = debitar(cuentaOrigen, valor, "TRANSFERENCIA_ENVIADA", ref, cuentaDestino.numero(), desc);
        BigDecimal saldoDestino = acreditar(cuentaDestino, valor, "TRANSFERENCIA_RECIBIDA", ref, cuentaOrigen.numero(), desc);

        return new ResultadoTransaccion(ref, "TRANSFERENCIA", valor, cuentaOrigen.numero(), saldoOrigen,
                cuentaDestino.numero(), saldoDestino,
                "Transferencia exitosa de " + pesos(valor) + " desde la cuenta " + cuentaOrigen.numero()
                        + " (" + cuentaOrigen.titular() + ") hacia la cuenta " + cuentaDestino.numero()
                        + " (" + cuentaDestino.titular() + ").");
    }

    // ================================================================== auxiliares (usados tambien por CreditoService)

    /** Suma dinero a una cuenta ya bloqueada y registra el movimiento. Devuelve el nuevo saldo. */
    public BigDecimal acreditar(Cuenta cuenta, BigDecimal monto, String tipo, String ref, String contraparte, String desc) {
        BigDecimal nuevoSaldo = cuenta.saldo().add(monto);
        repo.actualizarSaldo(cuenta.id(), nuevoSaldo);
        repo.insertarMovimiento(ref, cuenta.id(), tipo, monto, nuevoSaldo, contraparte, desc);
        return nuevoSaldo;
    }

    /** Resta dinero de una cuenta ya bloqueada validando fondos. Devuelve el nuevo saldo. */
    public BigDecimal debitar(Cuenta cuenta, BigDecimal monto, String tipo, String ref, String contraparte, String desc) {
        if (cuenta.saldo().compareTo(monto) < 0) {
            throw new BancoException("Fondos insuficientes en la cuenta " + cuenta.numero() + ": saldo disponible "
                    + pesos(cuenta.saldo()) + ", monto solicitado " + pesos(monto) + ".");
        }
        BigDecimal nuevoSaldo = cuenta.saldo().subtract(monto);
        repo.actualizarSaldo(cuenta.id(), nuevoSaldo);
        repo.insertarMovimiento(ref, cuenta.id(), tipo, monto, nuevoSaldo, contraparte, desc);
        return nuevoSaldo;
    }

    public Cuenta cuentaActivaBloqueada(String numero) {
        Cuenta cuenta = cuentaBloqueada(numero);
        if (!cuenta.activa()) {
            throw new BancoException("La cuenta " + cuenta.numero() + " esta " + cuenta.estado()
                    + " y no puede realizar transacciones.");
        }
        return cuenta;
    }

    private Cuenta cuentaBloqueada(String numero) {
        String num = normalizarCuenta(numero, "numero de cuenta");
        if (!repo.bloquearFilaCuenta(num)) {
            throw new BancoException("No existe la cuenta " + num + ".");
        }
        return repo.buscarCuenta(num).orElseThrow();
    }

    private Cuenta cuenta(String numero) {
        String num = normalizarCuenta(numero, "numero de cuenta");
        return repo.buscarCuenta(num).orElseThrow(() -> new BancoException("No existe la cuenta " + num + "."));
    }

    static BigDecimal validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new BancoException("El monto debe ser mayor que cero.");
        }
        if (monto.compareTo(MONTO_MAXIMO) > 0) {
            throw new BancoException("El monto supera el maximo permitido por transaccion (" + pesos(MONTO_MAXIMO) + ").");
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new BancoException("El monto no puede tener mas de dos decimales.");
        }
        return monto.setScale(2, RoundingMode.UNNECESSARY);
    }

    static String pesos(BigDecimal valor) {
        return String.format(Locale.forLanguageTag("es-CO"), "$%,.2f COP", valor);
    }

    /** Quita espacios y guiones: "1000-000001" y "1000000001" son la misma cuenta. */
    static String normalizarCuenta(String numero, String campo) {
        return requerido(numero, campo).replaceAll("[\\s-]", "");
    }

    static String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new BancoException("El campo '" + campo + "' es obligatorio.");
        }
        return valor.trim();
    }

    private static String vacioANull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private static String textoODefecto(String valor, String defecto) {
        return (valor == null || valor.isBlank()) ? defecto : valor.trim();
    }

    private String nuevoNumeroCuenta(String tipo) {
        String prefijo = tipo.equals("AHORROS") ? "10" : "20";
        String numero;
        do {
            numero = prefijo + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (repo.existeCuenta(numero));
        return numero;
    }

    static String nuevaReferencia() {
        return "TRX-" + LocalDateTime.now().format(REF) + "-"
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
    }
}
