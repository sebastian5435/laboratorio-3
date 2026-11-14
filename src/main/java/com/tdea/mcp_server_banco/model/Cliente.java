package com.tdea.mcp_server_banco.model;

public record Cliente(
        Long id,
        String documento,
        String nombre,
        String email,
        String telefono,
        String fechaRegistro) {
}
