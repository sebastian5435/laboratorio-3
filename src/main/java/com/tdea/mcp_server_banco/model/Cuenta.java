package com.tdea.mcp_server_banco.model;

import java.math.BigDecimal;

public record Cuenta(
        Long id,
        String numero,
        String tipo,
        BigDecimal saldo,
        String estado,
        String documentoTitular,
        String titular,
        String fechaApertura) {

    public boolean activa() {
        return "ACTIVA".equals(estado);
    }
}
