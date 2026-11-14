package com.tdea.mcp_server_banco.model;

import java.math.BigDecimal;

public record SimulacionCredito(
        BigDecimal monto,
        int plazoMeses,
        BigDecimal tasaMensual,
        BigDecimal cuotaMensual,
        BigDecimal totalAPagar,
        BigDecimal totalIntereses) {
}
