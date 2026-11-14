package com.tdea.mcp_server_banco.model;

import java.math.BigDecimal;

public record SolicitudCredito(
        String numero,
        String documentoCliente,
        String nombreCliente,
        BigDecimal monto,
        int plazoMeses,
        BigDecimal tasaMensual,
        BigDecimal cuotaMensual,
        BigDecimal ingresosMensuales,
        String proposito,
        String estado,
        String observacion,
        String cuentaDesembolso,
        String fechaSolicitud,
        String fechaResolucion) {
}
