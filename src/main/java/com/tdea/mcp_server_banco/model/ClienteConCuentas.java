package com.tdea.mcp_server_banco.model;

import java.util.List;

public record ClienteConCuentas(Cliente cliente, List<Cuenta> cuentas) {
}
