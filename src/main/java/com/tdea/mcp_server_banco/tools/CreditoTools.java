package com.tdea.mcp_server_banco.tools;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.tdea.mcp_server_banco.model.SimulacionCredito;
import com.tdea.mcp_server_banco.model.SolicitudCredito;
import com.tdea.mcp_server_banco.service.CreditoService;

/**
 * Herramientas MCP para el ciclo de vida de una solicitud de credito.
 */
@Component
public class CreditoTools {

    private final CreditoService creditos;

    public CreditoTools(CreditoService creditos) {
        this.creditos = creditos;
    }

    @Tool(name = "simularCredito",
            description = "Simula un credito de libre inversion: calcula la cuota mensual fija, el total a pagar y los intereses. No guarda nada.")
    public SimulacionCredito simularCredito(
            @ToolParam(description = "Monto del credito en COP (entre 500.000 y 100.000.000)") BigDecimal monto,
            @ToolParam(description = "Plazo en meses (entre 6 y 84)") Integer plazoMeses) {
        return creditos.simular(monto, plazoMeses);
    }

    @Tool(name = "solicitarCredito",
            description = "Radica una solicitud de credito para un cliente. Se pre-evalua automaticamente: si la cuota "
                    + "supera el 40% de los ingresos mensuales queda RECHAZADA; si no, queda PENDIENTE de aprobacion.")
    public SolicitudCredito solicitarCredito(
            @ToolParam(description = "Documento del cliente solicitante") String documento,
            @ToolParam(description = "Monto solicitado en COP (entre 500.000 y 100.000.000)") BigDecimal monto,
            @ToolParam(description = "Plazo en meses (entre 6 y 84)") Integer plazoMeses,
            @ToolParam(description = "Ingresos mensuales del cliente en COP") BigDecimal ingresosMensuales,
            @ToolParam(description = "Proposito del credito (vehiculo, estudio, vivienda, etc.)", required = false) String proposito) {
        return creditos.solicitar(documento, monto, plazoMeses, ingresosMensuales, proposito);
    }

    @Tool(name = "consultarCreditos",
            description = "Lista solicitudes de credito. Si se indica documento, devuelve las del cliente (opcionalmente filtradas por estado). "
                    + "Sin documento, devuelve las solicitudes en el estado indicado (por defecto PENDIENTE). "
                    + "Estados: PENDIENTE, RECHAZADA, DESEMBOLSADA.")
    public List<SolicitudCredito> consultarCreditos(
            @ToolParam(description = "Documento del cliente", required = false) String documento,
            @ToolParam(description = "Estado a filtrar: PENDIENTE, RECHAZADA o DESEMBOLSADA", required = false) String estado) {
        return creditos.consultar(documento, estado);
    }

    @Tool(name = "aprobarCredito",
            description = "Aprueba una solicitud de credito PENDIENTE y desembolsa el monto en una cuenta activa del mismo cliente.")
    public SolicitudCredito aprobarCredito(
            @ToolParam(description = "Numero de la solicitud, por ejemplo CR-123456") String numeroSolicitud,
            @ToolParam(description = "Numero de la cuenta del cliente donde se desembolsa") String numeroCuenta) {
        return creditos.aprobarYDesembolsar(numeroSolicitud, numeroCuenta);
    }

    @Tool(name = "rechazarCredito",
            description = "Rechaza una solicitud de credito PENDIENTE indicando el motivo.")
    public SolicitudCredito rechazarCredito(
            @ToolParam(description = "Numero de la solicitud, por ejemplo CR-123456") String numeroSolicitud,
            @ToolParam(description = "Motivo del rechazo") String motivo) {
        return creditos.rechazar(numeroSolicitud, motivo);
    }
}
