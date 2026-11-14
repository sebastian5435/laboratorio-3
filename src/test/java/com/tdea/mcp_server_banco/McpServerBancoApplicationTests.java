package com.tdea.mcp_server_banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.tool.ToolCallback;

import com.tdea.mcp_server_banco.model.BancoException;
import com.tdea.mcp_server_banco.model.Cuenta;
import com.tdea.mcp_server_banco.model.ResultadoTransaccion;
import com.tdea.mcp_server_banco.model.SolicitudCredito;
import com.tdea.mcp_server_banco.service.BancoService;
import com.tdea.mcp_server_banco.service.CreditoService;

@SpringBootTest(properties = {
        "spring.ai.mcp.server.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:bancotest;DB_CLOSE_DELAY=-1",
        "logging.file.name=target/test.log"
})
class McpServerBancoApplicationTests {

    @Autowired
    BancoService banco;

    @Autowired
    CreditoService creditos;

    @Autowired
    List<ToolCallback> tools;

    private static int secuencia = 0;

    /** Crea un cliente nuevo (documento unico por prueba) con una cuenta de ahorros. */
    private Cuenta clienteConCuenta(String saldo) {
        String doc = "9" + String.format("%08d", ++secuencia) + System.nanoTime() % 1000;
        banco.registrarCliente(doc, "Cliente " + doc, null, null);
        return banco.abrirCuenta(doc, "AHORROS", new BigDecimal(saldo));
    }

    @Test
    void registraLasCatorceHerramientas() {
        assertThat(tools).extracting(t -> t.getToolDefinition().name()).containsExactlyInAnyOrder(
                "registrarCliente", "consultarCliente", "abrirCuenta", "consultarCuenta", "consignar", "retirar",
                "transferir", "consultarMovimientos", "cambiarEstadoCuenta", "simularCredito", "solicitarCredito",
                "consultarCreditos", "aprobarCredito", "rechazarCredito");
    }

    @Test
    void transferenciaMueveElDineroYRegistraDosMovimientos() {
        Cuenta origen = clienteConCuenta("1000000");
        Cuenta destino = clienteConCuenta("50000");

        ResultadoTransaccion r = banco.transferir(origen.numero(), destino.numero(), new BigDecimal("250000"), "Arriendo");

        assertThat(r.saldoCuentaOrigen()).isEqualByComparingTo("750000");
        assertThat(r.saldoCuentaDestino()).isEqualByComparingTo("300000");
        assertThat(banco.movimientos(origen.numero(), 10)).first()
                .satisfies(m -> {
                    assertThat(m.tipo()).isEqualTo("TRANSFERENCIA_ENVIADA");
                    assertThat(m.referencia()).isEqualTo(r.referencia());
                });
        assertThat(banco.movimientos(destino.numero(), 10)).first()
                .satisfies(m -> assertThat(m.tipo()).isEqualTo("TRANSFERENCIA_RECIBIDA"));
    }

    @Test
    void transferenciaSinFondosNoModificaNingunSaldo() {
        Cuenta origen = clienteConCuenta("100000");
        Cuenta destino = clienteConCuenta("0");

        assertThatThrownBy(() -> banco.transferir(origen.numero(), destino.numero(), new BigDecimal("100000.01"), null))
                .isInstanceOf(BancoException.class)
                .hasMessageContaining("Fondos insuficientes");

        assertThat(banco.consultarCuenta(origen.numero()).saldo()).isEqualByComparingTo("100000");
        assertThat(banco.consultarCuenta(destino.numero()).saldo()).isEqualByComparingTo("0");
    }

    @Test
    void noSePuedeTransferirAUnaMismaCuentaEscritaDistinto() {
        Cuenta cuenta = clienteConCuenta("100000");
        String conGuion = cuenta.numero().substring(0, 4) + "-" + cuenta.numero().substring(4);

        assertThatThrownBy(() -> banco.transferir(cuenta.numero(), conGuion, new BigDecimal("50000"), null))
                .hasMessageContaining("no pueden ser la misma");
        assertThat(banco.consultarCuenta(cuenta.numero()).saldo()).isEqualByComparingTo("100000");
    }

    @Test
    void cuentaBloqueadaNoRecibeConsignaciones() {
        Cuenta cuenta = clienteConCuenta("0");
        banco.cambiarEstadoCuenta(cuenta.numero(), "BLOQUEADA");

        assertThatThrownBy(() -> banco.consignar(cuenta.numero(), new BigDecimal("1000"), null))
                .isInstanceOf(BancoException.class)
                .hasMessageContaining("BLOQUEADA");
    }

    @Test
    void consignarYRetirarValidanMontos() {
        Cuenta cuenta = clienteConCuenta("0");
        assertThat(banco.consignar(cuenta.numero(), new BigDecimal("1500.50"), null).saldoCuentaDestino())
                .isEqualByComparingTo("1500.50");
        assertThat(banco.retirar(cuenta.numero(), new BigDecimal("500.50"), null).saldoCuentaOrigen())
                .isEqualByComparingTo("1000");
        assertThatThrownBy(() -> banco.consignar(cuenta.numero(), new BigDecimal("-5"), null))
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> banco.consignar(cuenta.numero(), new BigDecimal("1.001"), null))
                .hasMessageContaining("dos decimales");
    }

    @Test
    void creditoPreAprobadoSeDesembolsaEnCuentaDelCliente() {
        Cuenta cuenta = clienteConCuenta("0");
        SolicitudCredito s = creditos.solicitar(cuenta.documentoTitular(), new BigDecimal("10000000"), 36,
                new BigDecimal("5000000"), "Vehiculo");
        assertThat(s.estado()).isEqualTo("PENDIENTE");
        // 10M a 36 meses al 1.5% mensual -> cuota ~ 361.524
        assertThat(s.cuotaMensual()).isBetween(new BigDecimal("361000"), new BigDecimal("362000"));

        SolicitudCredito aprobada = creditos.aprobarYDesembolsar(s.numero(), cuenta.numero());

        assertThat(aprobada.estado()).isEqualTo("DESEMBOLSADA");
        assertThat(banco.consultarCuenta(cuenta.numero()).saldo()).isEqualByComparingTo("10000000");
        assertThatThrownBy(() -> creditos.aprobarYDesembolsar(s.numero(), cuenta.numero()))
                .hasMessageContaining("ya fue resuelta");
    }

    @Test
    void creditoConCuotaAltaSeRechazaAutomaticamente() {
        Cuenta cuenta = clienteConCuenta("0");
        SolicitudCredito s = creditos.solicitar(cuenta.documentoTitular(), new BigDecimal("50000000"), 12,
                new BigDecimal("2000000"), null);
        assertThat(s.estado()).isEqualTo("RECHAZADA");
        assertThat(s.observacion()).contains("Rechazo automatico");
    }

    @Test
    void noSePuedeDesembolsarEnCuentaDeOtroCliente() {
        Cuenta propia = clienteConCuenta("0");
        Cuenta ajena = clienteConCuenta("0");
        SolicitudCredito s = creditos.solicitar(propia.documentoTitular(), new BigDecimal("1000000"), 12,
                new BigDecimal("3000000"), null);

        assertThatThrownBy(() -> creditos.aprobarYDesembolsar(s.numero(), ajena.numero()))
                .hasMessageContaining("no pertenece");
        assertThat(banco.consultarCuenta(ajena.numero()).saldo()).isEqualByComparingTo("0");
    }

    @Test
    void herramientaDevuelveJsonAlModelo() {
        Cuenta cuenta = clienteConCuenta("12345");
        ToolCallback consultar = tools.stream()
                .filter(t -> t.getToolDefinition().name().equals("consultarCuenta")).findFirst().orElseThrow();

        String json = consultar.call("{\"numeroCuenta\":\"" + cuenta.numero() + "\"}");

        assertThat(json).contains("\"numero\":\"" + cuenta.numero() + "\"").contains("12345");
    }
}
