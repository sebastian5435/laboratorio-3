package com.tdea.mcp_server_banco.model;

/**
 * Error de negocio (saldo insuficiente, cuenta inexistente, etc.).
 * Su mensaje llega tal cual al modelo de IA como resultado de la herramienta.
 */
public class BancoException extends RuntimeException {

    public BancoException(String message) {
        super(message);
    }
}
