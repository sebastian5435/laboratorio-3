package com.tdea.mcp_server_banco.model;

import java.math.BigDecimal;

public record Movimiento(
        String referencia,
        String numeroCuenta,
        String tipo,
        BigDecimal monto,
        BigDecimal saldoResultante,
        String cuentaContraparte,
        String descripcion,
        String fecha) {
}
