-- Datos de demostracion. Cada INSERT solo se ejecuta si el registro no existe,
-- asi que reiniciar el servidor NO borra ni reinicia los saldos.

INSERT INTO clientes (documento, nombre, email, telefono)
SELECT '1001234567', 'Ana Maria Gomez', 'ana.gomez@example.com', '3001234567'
 WHERE NOT EXISTS (SELECT 1 FROM clientes WHERE documento = '1001234567');

INSERT INTO clientes (documento, nombre, email, telefono)
SELECT '1007654321', 'Carlos Andres Restrepo', 'carlos.restrepo@example.com', '3107654321'
 WHERE NOT EXISTS (SELECT 1 FROM clientes WHERE documento = '1007654321');

INSERT INTO clientes (documento, nombre, email, telefono)
SELECT '1012345678', 'Laura Sofia Martinez', 'laura.martinez@example.com', '3209876543'
 WHERE NOT EXISTS (SELECT 1 FROM clientes WHERE documento = '1012345678');

INSERT INTO cuentas (numero, cliente_id, tipo, saldo)
SELECT '1000000001', id, 'AHORROS', 2500000 FROM clientes
 WHERE documento = '1001234567' AND NOT EXISTS (SELECT 1 FROM cuentas WHERE numero = '1000000001');

INSERT INTO cuentas (numero, cliente_id, tipo, saldo)
SELECT '2000000001', id, 'CORRIENTE', 800000 FROM clientes
 WHERE documento = '1001234567' AND NOT EXISTS (SELECT 1 FROM cuentas WHERE numero = '2000000001');

INSERT INTO cuentas (numero, cliente_id, tipo, saldo)
SELECT '1000000002', id, 'AHORROS', 1200000 FROM clientes
 WHERE documento = '1007654321' AND NOT EXISTS (SELECT 1 FROM cuentas WHERE numero = '1000000002');

INSERT INTO cuentas (numero, cliente_id, tipo, saldo)
SELECT '1000000003', id, 'AHORROS', 350000 FROM clientes
 WHERE documento = '1012345678' AND NOT EXISTS (SELECT 1 FROM cuentas WHERE numero = '1000000003');

-- Movimiento de apertura para que el extracto cuadre con el saldo inicial
INSERT INTO movimientos (referencia, cuenta_id, tipo, monto, saldo_resultante, descripcion)
SELECT 'APERTURA-' || c.numero, c.id, 'CONSIGNACION', c.saldo, c.saldo, 'Deposito de apertura'
  FROM cuentas c
 WHERE c.numero IN ('1000000001', '2000000001', '1000000002', '1000000003')
   AND NOT EXISTS (SELECT 1 FROM movimientos m WHERE m.referencia = 'APERTURA-' || c.numero);
