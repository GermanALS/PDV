# Preguntas frecuentes del punto de venta PDV

## Venta de mostrador
P: ¿Cómo busco un artículo para vender?
R: Escribí parte del nombre en el buscador o tocá el botón de escanear
código de barras (usa la cámara). El artículo encontrado se agrega al
carrito con cantidad 1; podés ajustar la cantidad antes de confirmar.

P: ¿Cómo cobro en efectivo y calculo el cambio?
R: Al confirmar la venta con método de pago "Efectivo", la app pide el
monto recibido y calcula el cambio automáticamente. El cambio queda
visible en una tarjeta destacada hasta que cierres la venta.

P: ¿Cómo imprimo o reimprimo el ticket?
R: Después de confirmar la venta se genera un ticket en PDF. Usá el botón
"Imprimir ticket" (Print Framework de Android, funciona con cualquier
impresora que tenga su servicio de impresión instalado). Tras imprimir se
ofrece reimprimir para el cliente.

## Entrada de mercancía
P: ¿Cuál es la diferencia entre artículo nuevo y artículo existente?
R: "Artículo nuevo" da de alta el producto en el catálogo maestro y en el
inventario a la vez. "Artículo existente" solo suma cantidad al inventario
de un producto que ya está en el catálogo (buscalo o escaneá su código).

P: ¿Dónde indico en qué estante quedó guardado?
R: El campo "Estante" está disponible tanto para artículo nuevo como
existente, junto a la cantidad de la entrada.

## Inventario
P: ¿Cómo ajusto la cantidad en existencia de un artículo?
R: Buscá el artículo en Inventario y usá "Ajustar" — podés corregir tanto
atributos del catálogo (nombre, precio, categoría) como la cantidad en
existencia, en la misma pantalla.

## Caja
P: ¿Qué diferencia hay entre un corte parcial y un retiro de efectivo?
R: Un corte de caja resume las ventas/efectivo/tarjeta del período y
calcula el monto esperado en caja. Un retiro de efectivo registra que se
sacó dinero de la caja (ej. para depósito bancario), con su propio motivo.

## Devoluciones
P: ¿Cómo registro una devolución?
R: Buscá la venta o el artículo, indicá cantidad y motivo. La devolución
queda con folio propio y estado "registrada" para seguimiento.

## Usuarios y roles
P: ¿Quién puede administrar usuarios y roles?
R: Solo un usuario con el módulo "usuarios" habilitado en su rol ve la
pantalla de Administración de usuarios, donde se crean cuentas y se
asignan roles con sus módulos permitidos.

## Configuración
P: ¿Qué significan los tres modos (Local, Remoto, Local con
sincronización)?
R: Local funciona sin conexión al backend, guardando todo en el
dispositivo. Remoto usa exclusivamente el backend compartido. Local con
sincronización trabaja localmente y sincroniza en diferido cuando hay
conexión.

P: ¿Cómo activo el Asistente de IA?
R: En Configuración, sección "Asistente de IA": activá el switch, elegí
proveedor (DeepSeek, OpenAI u OpenRouter), modelo y token. Usá "Probar
conexión" para confirmar que el token funciona antes de guardar.

## Asistente de IA
P: ¿Qué puede hacer el asistente de IA además de responder preguntas?
R: Puede proponer tres tipos de acciones sobre el punto de venta: alta de
artículo al inventario (con costo opcional), corte de caja parcial (con
retiro opcional), y registro de una devolución. Ninguna acción se ejecuta
sin que la confirmes explícitamente, y solo si tu usuario tiene permiso
del módulo correspondiente.

P: ¿Puedo usar la voz en vez de escribir?
R: Sí, el botón de micrófono transcribe lo que decís y lo escribe en el
mismo campo de texto del chat — es solo otra forma de escribir el mensaje,
no cambia lo que la IA puede hacer.

P: ¿Por qué no puedo usar el chat con IA ahora mismo?
R: El chat necesita conexión a internet para hablar con el proveedor de
IA (DeepSeek/OpenAI/OpenRouter), sin importar el modo del backend. Si no
hay conexión, se muestra esta ayuda en su lugar.
