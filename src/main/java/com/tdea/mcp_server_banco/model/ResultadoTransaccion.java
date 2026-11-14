package com.tdea.mcp_server_banco.model;

import java.math.BigDecimal;

/**
 * Comprobante que se devuelve al modelo de IA despues de cada transaccion.
 */
public record ResultadoTransaccion(
        String referencia,
        String tipo,
        BigDecimal monto,
        String cuentaOrigen,
        BigDecimal saldoCuentaOrigen,
        String cuentaDestino,
        BigDecimal saldoCuentaDestino,
        String mensaje) {
}
