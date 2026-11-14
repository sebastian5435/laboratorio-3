package com.tdea.mcp_server_banco.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tdea.mcp_server_banco.model.BancoException;
import com.tdea.mcp_server_banco.model.Cliente;
import com.tdea.mcp_server_banco.model.Cuenta;
import com.tdea.mcp_server_banco.model.SimulacionCredito;
import com.tdea.mcp_server_banco.model.SolicitudCredito;
import com.tdea.mcp_server_banco.repository.BancoRepository;

/**
 * Solicitudes de credito: simulacion, radicacion con pre-evaluacion automatica,
 * aprobacion con desembolso a una cuenta del cliente y rechazo.
 */
@Service
@Transactional
public class CreditoService {

    public static final BigDecimal MONTO_MINIMO = new BigDecimal("500000");
    public static final BigDecimal MONTO_MAXIMO = new BigDecimal("100000000");
    public static final int PLAZO_MINIMO = 6;
    public static final int PLAZO_MAXIMO = 84;

    private final BancoRepository repo;
    private final BancoService banco;
    private final BigDecimal tasaMensual;
    private final BigDecimal maxRelacionCuotaIngreso;

    public CreditoService(BancoRepository repo, BancoService banco,
            @Value("${banco.credito.tasa-mensual:0.0150}") BigDecimal tasaMensual,
            @Value("${banco.credito.max-relacion-cuota-ingreso:0.40}") BigDecimal maxRelacionCuotaIngreso) {
        this.repo = repo;
        this.banco = banco;
        this.tasaMensual = tasaMensual;
        this.maxRelacionCuotaIngreso = maxRelacionCuotaIngreso;
    }

    @Transactional(readOnly = true)
    public SimulacionCredito simular(BigDecimal monto, Integer plazoMeses) {
        BigDecimal valor = validarMontoCredito(monto);
        int plazo = validarPlazo(plazoMeses);
        BigDecimal cuota = cuotaMensual(valor, plazo);
        BigDecimal total = cuota.multiply(BigDecimal.valueOf(plazo));
        return new SimulacionCredito(valor, plazo, tasaMensual, cuota, total, total.subtract(valor));
    }

    public SolicitudCredito solicitar(String documento, BigDecimal monto, Integer plazoMeses,
            BigDecimal ingresosMensuales, String proposito) {
        Cliente cliente = banco.cliente(documento);
        BigDecimal valor = validarMontoCredito(monto);
        int plazo = validarPlazo(plazoMeses);
        if (ingresosMensuales == null || ingresosMensuales.signum() <= 0) {
            throw new BancoException("Los ingresos mensuales deben ser mayores que cero.");
        }
        BigDecimal ingresos = ingresosMensuales.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal cuota = cuotaMensual(valor, plazo);

        // Pre-evaluacion automatica: la cuota no puede superar el % configurado de los ingresos.
        BigDecimal relacion = cuota.divide(ingresos, 4, RoundingMode.HALF_UP);
        String porcentaje = relacion.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
        String limite = maxRelacionCuotaIngreso.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";

        String estado;
        String observacion;
        if (relacion.compareTo(maxRelacionCuotaIngreso) > 0) {
            estado = "RECHAZADA";
            observacion = "Rechazo automatico: la cuota (" + BancoService.pesos(cuota) + ") equivale al " + porcentaje
                    + " de los ingresos y el maximo permitido es " + limite + ".";
        } else {
            estado = "PENDIENTE";
            observacion = "Pre-aprobada: la cuota equivale al " + porcentaje + " de los ingresos (maximo " + limite
                    + "). Pendiente de aprobacion y desembolso.";
        }

        String numero = nuevoNumeroSolicitud();
        repo.insertarSolicitud(numero, cliente.id(), valor, plazo, tasaMensual, cuota, ingresos,
                proposito == null || proposito.isBlank() ? null : proposito.trim(), estado, observacion);
        return repo.buscarSolicitud(numero).orElseThrow();
    }

    /**
     * Aprueba una solicitud PENDIENTE y desembolsa el dinero en una cuenta activa del mismo cliente.
     * Actualizar la solicitud, el saldo y el movimiento ocurre en una sola transaccion.
     */
    public SolicitudCredito aprobarYDesembolsar(String numeroSolicitud, String numeroCuenta) {
        SolicitudCredito solicitud = solicitudPendienteBloqueada(numeroSolicitud);
        Cuenta cuenta = banco.cuentaActivaBloqueada(numeroCuenta);
        if (!cuenta.documentoTitular().equals(solicitud.documentoCliente())) {
            throw new BancoException("La cuenta " + cuenta.numero() + " no pertenece al cliente de la solicitud ("
                    + solicitud.nombreCliente() + "). El desembolso solo puede hacerse a una cuenta propia.");
        }
        String ref = BancoService.nuevaReferencia();
        banco.acreditar(cuenta, solicitud.monto(), "DESEMBOLSO_CREDITO", ref, null,
                "Desembolso credito " + solicitud.numero());
        repo.resolverSolicitud(solicitud.numero(), "DESEMBOLSADA",
                "Aprobada y desembolsada en la cuenta " + cuenta.numero() + " (referencia " + ref + ").",
                cuenta.numero());
        return repo.buscarSolicitud(solicitud.numero()).orElseThrow();
    }

    public SolicitudCredito rechazar(String numeroSolicitud, String motivo) {
        SolicitudCredito solicitud = solicitudPendienteBloqueada(numeroSolicitud);
        String obs = "Rechazada: " + BancoService.requerido(motivo, "motivo");
        repo.resolverSolicitud(solicitud.numero(), "RECHAZADA", obs, null);
        return repo.buscarSolicitud(solicitud.numero()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<SolicitudCredito> consultar(String documento, String estado) {
        if (documento != null && !documento.isBlank()) {
            Cliente cliente = banco.cliente(documento);
            List<SolicitudCredito> lista = repo.solicitudesDeCliente(cliente.documento());
            if (estado == null || estado.isBlank()) {
                return lista;
            }
            String e = estado.trim().toUpperCase(Locale.ROOT);
            return lista.stream().filter(s -> s.estado().equals(e)).toList();
        }
        String e = (estado == null || estado.isBlank()) ? "PENDIENTE" : estado.trim().toUpperCase(Locale.ROOT);
        return repo.solicitudesPorEstado(e);
    }

    // ------------------------------------------------------------------ auxiliares

    /** Cuota fija (sistema frances): C = P * i / (1 - (1 + i)^-n). */
    BigDecimal cuotaMensual(BigDecimal monto, int plazo) {
        MathContext mc = MathContext.DECIMAL64;
        if (tasaMensual.signum() == 0) {
            return monto.divide(BigDecimal.valueOf(plazo), 2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(tasaMensual).pow(plazo, mc);
        BigDecimal cuota = monto.multiply(tasaMensual, mc).multiply(factor, mc)
                .divide(factor.subtract(BigDecimal.ONE), mc);
        return cuota.setScale(2, RoundingMode.HALF_UP);
    }

    private SolicitudCredito solicitudPendienteBloqueada(String numeroSolicitud) {
        String num = BancoService.requerido(numeroSolicitud, "numero de solicitud").toUpperCase(Locale.ROOT);
        if (!repo.bloquearFilaSolicitud(num)) {
            throw new BancoException("No existe la solicitud de credito " + num + ".");
        }
        SolicitudCredito solicitud = repo.buscarSolicitud(num).orElseThrow();
        if (!"PENDIENTE".equals(solicitud.estado())) {
            throw new BancoException("La solicitud " + num + " ya fue resuelta (estado " + solicitud.estado() + ").");
        }
        return solicitud;
    }

    private static BigDecimal validarMontoCredito(BigDecimal monto) {
        if (monto == null || monto.compareTo(MONTO_MINIMO) < 0 || monto.compareTo(MONTO_MAXIMO) > 0) {
            throw new BancoException("El monto del credito debe estar entre " + BancoService.pesos(MONTO_MINIMO)
                    + " y " + BancoService.pesos(MONTO_MAXIMO) + ".");
        }
        return monto.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static int validarPlazo(Integer plazo) {
        if (plazo == null || plazo < PLAZO_MINIMO || plazo > PLAZO_MAXIMO) {
            throw new BancoException("El plazo debe estar entre " + PLAZO_MINIMO + " y " + PLAZO_MAXIMO + " meses.");
        }
        return plazo;
    }

    private String nuevoNumeroSolicitud() {
        String numero;
        do {
            numero = "CR-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        } while (repo.existeSolicitud(numero));
        return numero;
    }
}
