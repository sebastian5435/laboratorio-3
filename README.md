# Servidor MCP bancario con Spring AI (TdeA – Laboratorio 3)

Servidor **MCP (Model Context Protocol)** hecho con **Spring Boot 3.5.3 + Spring AI 1.0.0** que se conecta a una
base de datos **H2** y permite que Claude (desde **Claude Desktop**) haga operaciones bancarias en lenguaje natural:
transferencias entre cuentas, consignaciones, retiros, consulta de extractos y solicitudes de crédito.

Es la solución al *Ejercicio a desarrollar* de la guía, construida sobre la misma estructura que el servidor de la
lista de compras (anotación `@Tool`, transporte **stdio**, JAR ejecutable registrado en `claude_desktop_config.json`).

## Cumplimiento del enunciado

> *Desarrolle un servidor MCP con Spring Boot que se conecte a una BD de su elección y permita generar
> transacciones para servicios bancarios (transferencia de dinero entre cuentas, crear solicitudes de créditos,
> realizar consignaciones en cuentas, etc). Debe conectarlo a Claude Desktop.*

| Requisito | Dónde está |
|---|---|
| Servidor MCP con Spring Boot | Spring Boot 3.5.3 + `spring-ai-starter-mcp-server` 1.0.0, transporte stdio (`application.properties`) |
| Conexión a una BD | H2 en archivo, tablas en `schema.sql`, acceso con `JdbcTemplate` (`BancoRepository`) |
| Transferencia entre cuentas | Herramienta `transferir` → `BancoService.transferir` (transacción atómica) |
| Solicitudes de crédito | `simularCredito`, `solicitarCredito`, `consultarCreditos`, `aprobarCredito`, `rechazarCredito` |
| Consignaciones | Herramienta `consignar` (además `retirar` y `consultarMovimientos`) |
| Herramientas con `@Tool` | `tools/BancoTools.java`, `tools/CreditoTools.java`, registradas en `McpServerBancoApplication` |
| Conexión a Claude Desktop | `claude_desktop_config.json` (ver *Conectar con Claude Desktop*) |

## Arquitectura

```
Claude Desktop (Host MCP)
   └─ Cliente MCP ──stdio (JSON-RPC)──▶ Servidor MCP (Spring AI)
                                          ├─ tools/BancoTools, CreditoTools   (@Tool → lo que ve la IA)
                                          ├─ service/BancoService, CreditoService (@Transactional, reglas de negocio)
                                          ├─ repository/BancoRepository        (JdbcTemplate, SQL)
                                          └─ H2 en archivo  ~/mcp-banco/bancodb.mv.db
```

**Tablas** (`src/main/resources/schema.sql`): `clientes`, `cuentas`, `movimientos`, `solicitudes_credito`.

### Garantías de las transacciones

- Cada operación es una **transacción de BD** (`@Transactional`): una transferencia debita y acredita en una sola
  unidad; si falla algo (fondos insuficientes, cuenta bloqueada…) se hace *rollback* y no se mueve nada.
- Las filas de las cuentas se bloquean con `SELECT … FOR UPDATE`, siempre en el mismo orden, para que dos
  operaciones simultáneas no lean el mismo saldo ni se interbloqueen.
- La BD tiene `CHECK (saldo >= 0)` como última defensa.
- Cada movimiento queda registrado con una **referencia** (`TRX-…`), el saldo resultante y la cuenta contraparte.

## Herramientas expuestas (14)

| Herramienta | Qué hace |
|---|---|
| `registrarCliente` | Crea un cliente (documento, nombre, email, teléfono) |
| `consultarCliente` | Datos del cliente y todas sus cuentas con saldo |
| `abrirCuenta` | Abre cuenta `AHORROS` o `CORRIENTE` con depósito inicial opcional |
| `consultarCuenta` | Saldo, estado, tipo y titular de una cuenta |
| `consignar` | Deposita dinero en una cuenta |
| `retirar` | Retira dinero si hay fondos |
| `transferir` | Transferencia atómica entre dos cuentas |
| `consultarMovimientos` | Extracto: últimos movimientos de una cuenta |
| `cambiarEstadoCuenta` | Bloquea / desbloquea una cuenta |
| `simularCredito` | Cuota fija (sistema francés), total a pagar e intereses |
| `solicitarCredito` | Radica una solicitud con pre-evaluación automática (cuota ≤ 40 % de ingresos) |
| `consultarCreditos` | Solicitudes de un cliente o todas las `PENDIENTE` |
| `aprobarCredito` | Aprueba y **desembolsa** en una cuenta del mismo cliente |
| `rechazarCredito` | Rechaza una solicitud pendiente con motivo |

Tasa mensual (1.5 %) y relación cuota/ingresos (40 %) se configuran en `application.properties`.

## Datos de ejemplo

Se cargan la primera vez (`data.sql`, no se sobrescriben al reiniciar):

| Cliente | Documento | Cuentas |
|---|---|---|
| Ana Maria Gomez | 1001234567 | 1000000001 (ahorros, $2.500.000) · 2000000001 (corriente, $800.000) |
| Carlos Andres Restrepo | 1007654321 | 1000000002 (ahorros, $1.200.000) |
| Laura Sofia Martinez | 1012345678 | 1000000003 (ahorros, $350.000) |

## Requisitos

- Java 17 o superior (probado con Java 26)
- **No hace falta instalar Maven**: el proyecto trae el Maven Wrapper (`mvnw` / `mvnw.cmd`).
- Claude Desktop

## Compilar y probar

```powershell
.\mvnw clean package
```

Esto corre las 10 pruebas automáticas (`McpServerBancoApplicationTests`) y genera
`target\mcp-server-banco-0.0.1-SNAPSHOT.jar`.

## Conectar con Claude Desktop

1. En Claude Desktop abre **Settings → Developer → Edit Config** (abre `claude_desktop_config.json`
   en la ubicación correcta; en la versión de Microsoft Store **no** es la carpeta `%APPDATA%\Claude`).
2. Agrega el servidor (también está en `claude_desktop_config.example.json`):

   ```json
   {
     "mcpServers": {
       "banco-tdea": {
         "command": "java",
         "args": [
           "-jar",
           "C:\\RUTA\\A\\mcp-server-banco\\target\\mcp-server-banco-0.0.1-SNAPSHOT.jar"
         ]
       }
     }
   }
   ```

   Cambia `C:\\RUTA\\A\\` por la carpeta donde clonaste el proyecto (en JSON cada `\` se escribe doble: `\\`).
   Si el archivo ya tiene otros servidores, agrega solo la entrada `"banco-tdea"` dentro de `"mcpServers"`.
3. **Cierra Claude Desktop por completo** (también desde la bandeja del sistema) y ábrelo de nuevo.
4. En el chat, el menú de herramientas debe mostrar `banco-tdea` con sus 14 herramientas.

### Prompts de prueba

- «Muéstrame los datos y cuentas del cliente con cédula 1001234567»
- «Transfiere 150.000 pesos de la cuenta 1000000001 a la 1000000002 por pago de arriendo»
- «Consigna 50.000 en la cuenta 1000000003»
- «Intenta transferir 5 millones desde la cuenta 1000000003» (debe fallar por fondos insuficientes)
- «Muéstrame el extracto de la cuenta 1000000002»
- «Simula un crédito de 10 millones a 36 meses»
- «Carlos (1007654321) gana 3.500.000 al mes y quiere un crédito de 5 millones a 24 meses para estudio»
- «Aprueba esa solicitud y desembólsala en su cuenta de ahorros»
- «Registra a Juan Pérez con cédula 1098765432 y ábrele una cuenta de ahorros con 100.000»

## Probar con MCP Inspector

```powershell
npx @modelcontextprotocol/inspector java -jar "C:\RUTA\A\mcp-server-banco\target\mcp-server-banco-0.0.1-SNAPSHOT.jar"
```

## Base de datos y logs

- BD: `%USERPROFILE%\mcp-banco\bancodb.mv.db` (ruta absoluta porque Claude Desktop lanza el JAR desde un
  directorio que no controlamos). Se puede cambiar con la variable de entorno `BANCO_DATA_DIR`.
- La URL usa `AUTO_SERVER=TRUE`, así Claude Desktop y MCP Inspector pueden abrir la BD al mismo tiempo.
- Log: `%USERPROFILE%\mcp-banco\mcp-server-banco.log`.
- Para empezar de cero: cierra Claude Desktop y borra la carpeta `%USERPROFILE%\mcp-banco`.

## Diferencias respecto a la guía (y por qué)

| Guía | Este proyecto | Motivo |
|---|---|---|
| `import org.springframework.ai.tool.ToolCallbacks` | `org.springframework.ai.support.ToolCallbacks` | En Spring AI 1.0.0 la clase está en `support`; el import de la guía no compila. |
| Sin `spring.ai.mcp.server.stdio` | `spring.ai.mcp.server.stdio=true` | En 1.0.0 el valor por defecto es `false`; se deja explícito. |
| `logging.pattern.console=` | `logging.threshold.console=OFF` | El patrón vacío hace que logback imprima un error en **stdout**, que es el canal del protocolo MCP. |
| `ConcurrentHashMap` en memoria | H2 en archivo + JdbcTemplate | El ejercicio pide una BD; H2 no requiere instalar nada y los datos sobreviven a reinicios. |
