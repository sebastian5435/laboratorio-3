package com.tdea.mcp_server_banco.tools;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.tdea.mcp_server_banco.model.Cliente;
import com.tdea.mcp_server_banco.model.ClienteConCuentas;
import com.tdea.mcp_server_banco.model.Cuenta;
import com.tdea.mcp_server_banco.model.Movimiento;
import com.tdea.mcp_server_banco.model.ResultadoTransaccion;
import com.tdea.mcp_server_banco.service.BancoService;

/**
 * Herramientas MCP de clientes, cuentas y transacciones.
 * El nombre y la descripcion de cada @Tool es lo que el modelo de IA usa
 * para decidir cuando invocarla; @ToolParam describe cada argumento.
 */
@Component
public class BancoTools {

    private final BancoService banco;

    public BancoTools(BancoService banco) {
        this.banco = banco;
    }

    @Tool(name = "registrarCliente",
            description = "Registra un nuevo cliente del banco. Requiere numero de documento (cedula) y nombre completo.")
    public Cliente registrarCliente(
            @ToolParam(description = "Numero de documento de identidad (solo digitos)") String documento,
            @ToolParam(description = "Nombre completo del cliente") String nombre,
            @ToolParam(description = "Correo electronico", required = false) String email,
            @ToolParam(description = "Telefono de contacto", required = false) String telefono) {
        return banco.registrarCliente(documento, nombre, email, telefono);
    }

    @Tool(name = "consultarCliente",
            description = "Consulta los datos de un cliente por su documento junto con todas sus cuentas y saldos.")
    public ClienteConCuentas consultarCliente(
            @ToolParam(description = "Numero de documento del cliente") String documento) {
        return banco.consultarCliente(documento);
    }

    @Tool(name = "abrirCuenta",
            description = "Abre una cuenta bancaria (AHORROS o CORRIENTE) para un cliente existente, "
                    + "con un deposito inicial opcional. Devuelve el numero de cuenta asignado.")
    public Cuenta abrirCuenta(
            @ToolParam(description = "Documento del titular") String documento,
            @ToolParam(description = "Tipo de cuenta: AHORROS o CORRIENTE") String tipo,
            @ToolParam(description = "Deposito inicial en COP (0 si no hay)", required = false) BigDecimal depositoInicial) {
        return banco.abrirCuenta(documento, tipo, depositoInicial);
    }

    @Tool(name = "consultarCuenta",
            description = "Consulta el saldo, estado, tipo y titular de una cuenta por su numero.")
    public Cuenta consultarCuenta(
            @ToolParam(description = "Numero de la cuenta") String numeroCuenta) {
        return banco.consultarCuenta(numeroCuenta);
    }

    @Tool(name = "consignar",
            description = "Realiza una consignacion (deposito) de dinero en una cuenta. Devuelve el comprobante con la referencia y el nuevo saldo.")
    public ResultadoTransaccion consignar(
            @ToolParam(description = "Numero de la cuenta que recibe el dinero") String numeroCuenta,
            @ToolParam(description = "Monto en COP, mayor que cero") BigDecimal monto,
            @ToolParam(description = "Descripcion o concepto", required = false) String descripcion) {
        return banco.consignar(numeroCuenta, monto, descripcion);
    }

    @Tool(name = "retirar",
            description = "Retira dinero de una cuenta si tiene fondos suficientes. Devuelve el comprobante con la referencia y el nuevo saldo.")
    public ResultadoTransaccion retirar(
            @ToolParam(description = "Numero de la cuenta") String numeroCuenta,
            @ToolParam(description = "Monto en COP, mayor que cero") BigDecimal monto,
            @ToolParam(description = "Descripcion o concepto", required = false) String descripcion) {
        return banco.retirar(numeroCuenta, monto, descripcion);
    }

    @Tool(name = "transferir",
            description = "Transfiere dinero entre dos cuentas del banco en una sola transaccion atomica: "
                    + "si la cuenta origen no tiene fondos o alguna cuenta no existe o esta bloqueada, no se mueve nada.")
    public ResultadoTransaccion transferir(
            @ToolParam(description = "Numero de la cuenta origen (de donde sale el dinero)") String cuentaOrigen,
            @ToolParam(description = "Numero de la cuenta destino (a donde llega el dinero)") String cuentaDestino,
            @ToolParam(description = "Monto en COP, mayor que cero") BigDecimal monto,
            @ToolParam(description = "Descripcion o concepto", required = false) String descripcion) {
        return banco.transferir(cuentaOrigen, cuentaDestino, monto, descripcion);
    }

    @Tool(name = "consultarMovimientos",
            description = "Lista los ultimos movimientos (extracto) de una cuenta, del mas reciente al mas antiguo.")
    public List<Movimiento> consultarMovimientos(
            @ToolParam(description = "Numero de la cuenta") String numeroCuenta,
            @ToolParam(description = "Cantidad maxima de movimientos (por defecto 10, maximo 100)", required = false) Integer limite) {
        return banco.movimientos(numeroCuenta, limite);
    }

    @Tool(name = "cambiarEstadoCuenta",
            description = "Bloquea o desbloquea una cuenta. Una cuenta BLOQUEADA no puede recibir ni enviar dinero.")
    public Cuenta cambiarEstadoCuenta(
            @ToolParam(description = "Numero de la cuenta") String numeroCuenta,
            @ToolParam(description = "Nuevo estado: ACTIVA o BLOQUEADA") String estado) {
        return banco.cambiarEstadoCuenta(numeroCuenta, estado);
    }
}
