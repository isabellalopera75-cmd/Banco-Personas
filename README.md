**BANCO DE PERSONAS**

Documento de Arquitectura de Sistema

Análisis y Desarrollo de Software (ADSO) - Ficha 3142784

Centro Tecnológico de la Amazonia - SENA

Instructor: Oscar Yanguas

julio de 2026

# 1. Contexto del Sistema

El Banco de Personas es un sistema de registro de ciudadanos diseñado
para operar en condiciones de conectividad intermitente. Su propósito
principal es permitir que un funcionario pueda registrar, consultar,
actualizar y eliminar información de personas desde un dispositivo
Android, sin depender de una conexión a internet estable para hacerlo.

La necesidad de este tipo de sistema surge en contextos rurales o de
difícil acceso, donde la señal de red es escasa o inexistente. Imagina
un funcionario de una alcaldía que debe levantar un censo en una vereda
apartada. No puede esperar a tener señal para guardar cada registro;
necesita trabajar en campo y que la información llegue al servidor
central cuando sea posible, de forma automática y garantizando que
ningún dato se pierda ni llegue corrupto.

Para cumplir con ese escenario, el sistema está diseñado bajo tres
condiciones no negociables:

**Primera,** la aplicación debe poder instalarse en un dispositivo
Android como cualquier otra app, es decir, a través de un archivo APK.
Esto implica que el sistema tiene una capa móvil construida para
ejecutarse de forma nativa en el sistema operativo Android, sin depender
de un navegador ni de una conexión permanente a internet.

**Segunda,** el sistema debe funcionar completamente offline. Esto
significa que todas las operaciones de crear, consultar, editar y
eliminar registros deben estar disponibles incluso sin red. El usuario
no debe ver mensajes de error por falta de conexión; simplemente trabaja
con normalidad y el sistema se encarga de sincronizar cuando la red
vuelva.

**Tercera,** cuando los datos se sincronizan con el servidor central,
debe garantizarse su integridad. Esto quiere decir que el sistema debe
detectar si un dato se corrompió durante el envío, si hay conflictos
entre versiones del mismo registro modificado desde distintos
dispositivos, y debe asegurarse de que los cambios se apliquen de forma
completa o no se apliquen en absoluto. No puede quedar la base de datos
central en un estado a medias.

# 2. Stack Tecnológico

La arquitectura del sistema está dividida en tres grandes capas: la
aplicación móvil que corre en el dispositivo Android, la capa de
comunicación entre el dispositivo y el servidor, y el backend que
gestiona la base de datos central.

## 2.1 La aplicación móvil

**React Native**, un framework que permite desarrollar aplicaciones
Android (y iOS) usando JavaScript. React Native compila el código en una
aplicación nativa real, por lo que el resultado final es un APK que se
instala y funciona como cualquier otra app del celular, con acceso a las
funciones del dispositivo como almacenamiento local, sensor de red y
notificaciones.

**SQLite** a través de la librería expo-sqlite. SQLite es una base de
datos relacional completa que corre directamente en el dispositivo, sin
necesidad de un servidor. Android incluye soporte para SQLite de forma
nativa, por lo que no requiere instalación adicional ni permisos
especiales de almacenamiento. Los datos que el usuario guarda quedan en
un archivo dentro del espacio privado de la app, protegido por el
sistema operativo.

## 2.2 La capa de comunicación

**Protocol Buffers** (también llamado protobuf), un estándar de Google
para serializar datos de forma binaria.

La diferencia frente a JSON es importante: JSON es texto libre donde
cualquier cosa puede ir en cualquier campo sin que nadie lo valide
automáticamente. Protocol Buffers exige definir de antemano un esquema
en un archivo con extensión .proto, que especifica exactamente qué
campos existen, en qué orden aparecen y de qué tipo es cada uno. Si un
dato llega con el tipo incorrecto, el protocolo lo rechaza antes de que
toque la base de datos. Además, los datos viajan en formato binario, más
compacto y menos expuesto que el texto plano de JSON.

**JWT** en el encabezado de la solicitud HTTP. Este token identifica el
dispositivo que está sincronizando, y el servidor lo verifica antes de
procesar cualquier dato.

## 2.3 El backend y la base de datos central

**Node.js** y el framework **Express**. Expone un único endpoint de
sincronización, POST /sync, que recibe el lote de cambios del
dispositivo, los valida y los aplica en la base de datos central.

**PostgreSQL**, un sistema de gestión de bases de datos relacional
robusto y apto para producción. Aquí viven todos los registros de todas
las personas, de todos los dispositivos que hayan sincronizado. Es la
fuente única de verdad del sistema.

# 3. Estructura de Datos

El sistema maneja dos bases de datos completamente independientes: una
en el dispositivo (SQLite) y otra en el servidor (PostgreSQL). No son
una réplica la una de la otra; tienen estructuras similares en los
campos de negocio, pero difieren en los campos de control que cada una
necesita para su propio funcionamiento.

## 3.1 La base de datos local en SQLite

La base de datos local tiene dos tablas. La primera es la tabla
principal de personas, donde se almacenan los registros con los datos de
negocio: cédula, nombre, apellido, teléfono, dirección, fecha de
nacimiento y profesión. A estos campos se les suman cuatro columnas de
control que el servidor no necesita ver de la misma forma.

**id**, que no es un número autoincremental sino un UUID, es decir, un
identificador único universal generado directamente en el dispositivo en
el momento de crear el registro. Esto es fundamental porque si dos
dispositivos distintos están offline al mismo tiempo y ambos crean
registros nuevos, no pueden coordinarse para asignar IDs consecutivos
sin colisionar. Con UUID, la probabilidad de que dos dispositivos
generen el mismo ID es matemáticamente despreciable.

**version**, un número entero que empieza en 1 y se incrementa cada vez
que el registro es editado. Sirve para que el servidor pueda detectar si
el registro que le están enviando es más nuevo o más viejo que el que ya
tiene almacenado.

**checksum**, que es el resultado de aplicar el algoritmo SHA-256 sobre
el contenido del registro. Antes de enviarlo al servidor, la app calcula
este hash. El servidor lo recibe, recalcula el hash sobre los datos que
le llegaron, y compara. Si coinciden, el dato llegó íntegro. Si no
coinciden, significa que algo se corrompió en tránsito y el registro se
rechaza.

**sync_status**, que indica el estado de sincronización del registro.
Puede tener cuatro valores: pending_create cuando el registro es nuevo y
aún no ha llegado al servidor, pending_update cuando fue editado
localmente pero el cambio no se ha sincronizado, pending_delete cuando
fue marcado para borrar pero la eliminación aún no llegó al servidor, y
synced cuando el servidor ya confirmó el cambio.

La segunda tabla de la base de datos local es la outbox. Esta tabla
funciona como una cola de operaciones pendientes: cada vez que el
usuario crea, edita o elimina un registro, se genera una entrada en la
outbox con el tipo de operación, el ID del registro afectado, y el
payload ya serializado en formato protobuf. Esta separación entre la
tabla de datos y la cola de operaciones es importante porque si el
dispositivo se apaga justo a mitad de una sincronización, las
operaciones pendientes no se pierden; siguen en la outbox esperando el
próximo intento.

## 3.2 La base de datos central en PostgreSQL

La base de datos central tiene los mismos campos de negocio que SQLite,
más algunos campos propios del servidor. La cédula tiene una restricción
UNIQUE, que es la regla de negocio fundamental: no puede haber dos
personas con la misma cédula en el sistema. El ID sigue siendo UUID, el
mismo que generó el dispositivo, para que el servidor pueda identificar
de qué registro habla el cliente sin ambigüedad.

El servidor también guarda su propia versión y checksum de cada
registro, que son los que compara contra los que llegan del dispositivo
para detectar conflictos. Adicionalmente tiene campos de auditoría como
la fecha de creación y la fecha de última modificación en el servidor.

Junto a la tabla de personas, el servidor tiene una tabla de auditoría
llamada sync_log. Cada vez que un dispositivo sincroniza, se registra
aquí qué dispositivo fue, qué operación intentó hacer, y si se aplicó
correctamente o quedó en conflicto. Esto no afecta el funcionamiento de
la app, pero es muy útil para depurar problemas y tener trazabilidad de
todos los cambios del sistema.

**4. El Esquema de Comunicación: El Archivo .proto**

El archivo .proto es el contrato formal entre la aplicación móvil y el
servidor. Define exactamente qué estructura tienen los datos que viajan
entre los dos, y tanto el cliente como el servidor deben respetar ese
contrato para poder comunicarse.

persona.proto y contiene tres definiciones. La primera es el mensaje
Persona, que describe todos los campos de un registro: los siete campos
de negocio (cédula, nombre, apellido, teléfono, dirección, fecha de
nacimiento y profesión), más los campos de control (id, version,
checksum, tipo de operación y timestamp de modificación). Cada campo
tiene un tipo de dato estricto, por ejemplo, la versión es un entero de
32 bits y no puede llegar como texto.

SyncRequest, que es lo que la app envía al servidor. Contiene el
identificador del dispositivo que está sincronizando y una lista de
mensajes Persona, cada uno con su tipo de operación (create, update o
delete). Así el servidor recibe en una sola petición todo el lote de
cambios pendientes.

SyncResponse, que es lo que el servidor devuelve a la app. Contiene tres
listas: los IDs de los registros que se aplicaron correctamente, los IDs
de los registros que generaron un conflicto de versión, y los IDs de los
registros que fueron rechazados por tener un checksum inválido. Con esta
respuesta, la app sabe exactamente qué hacer con cada elemento de su
cola.

El hecho de que ambos lados usen el mismo archivo .proto garantiza que
nunca haya ambigüedad sobre el formato. Si alguien modifica el esquema
de un lado sin actualizar el otro, la comunicación falla de forma clara
e inmediata, en lugar de silenciosamente corrupta.

**5. Flujo de Datos en Modo Offline**

Cuando el dispositivo no tiene conexión a internet, el usuario
interactúa con la app exactamente igual que si la tuviera. No hay
pantallas de error, no hay tiempos de espera, no hay funciones
bloqueadas. Todo lo que el usuario hace se guarda localmente y la app
responde de inmediato.

## 5.1 Crear un nuevo registro

El usuario abre el formulario e ingresa la cédula, nombre, apellido,
teléfono, dirección, fecha de nacimiento y profesión. Antes de guardar,
la app consulta en SQLite si ya existe un registro con esa cédula. Si no
existe, continúa con el proceso de creación. Si ya existe, en lugar de
crear un duplicado, abre ese registro en el formulario pre-llenado para
que el usuario lo edite.

Asumiendo que es un registro nuevo, la app genera un UUID para ese
registro, calcula el checksum SHA-256 del contenido, establece la
versión en 1, y guarda el registro en la tabla de personas con
sync_status igual a pending_create. Simultáneamente, inserta una entrada
en la tabla outbox indicando que hay una operación de tipo create
pendiente para ese ID.

El usuario ve el registro guardado en su pantalla de inmediato,
posiblemente con un pequeño indicador visual de que está pendiente de
sincronización. Desde su perspectiva, el proceso terminó exitosamente.

## 5.2 Editar un registro

El usuario selecciona un registro existente y modifica uno o más campos.
Al guardar, la app actualiza el registro en SQLite con los nuevos
valores, incrementa el campo version en uno, y recalcula el checksum con
el contenido actualizado. El sync_status cambia a pending_update. En la
outbox se inserta una nueva entrada de tipo update.

Si el registro ya tenía una entrada pendiente en la outbox de una
edición anterior que aún no se había sincronizado, esa entrada se
reemplaza por la nueva, porque no tiene sentido enviar al servidor dos
versiones intermedias del mismo registro cuando solo importa el estado
final más reciente.

## 5.3 Eliminar un registro

Eliminar un registro no borra inmediatamente el dato de SQLite, porque
el servidor todavía no sabe que debe eliminarlo. En cambio, el registro
se marca con sync_status igual a pending_delete y se oculta de la
interfaz del usuario, de modo que visualmente ya no aparece. En la
outbox se registra una operación de tipo delete. Cuando se sincronice,
el servidor recibirá la instrucción de borrar ese registro y confirmará
la eliminación; solo entonces el registro se borra definitivamente de
SQLite también.

**6. Flujo de Datos en Modo Online — Sincronización**

Cuando el dispositivo recupera conexión a internet, la librería NetInfo
de React Native detecta el cambio de estado de red y notifica a la app.
La app revisa si hay entradas pendientes en la outbox; si las hay,
inicia el proceso de sincronización de forma automática, sin que el
usuario tenga que hacer nada.

## 6.1 Preparación y envío

La app consulta todas las entradas de la outbox y construye un objeto
SyncRequest con la lista de operaciones pendientes. Ese objeto se
serializa usando el esquema .proto, produciendo un buffer de bytes
binarios, y se envía al servidor mediante una petición POST al endpoint
/sync. La petición viaja sobre HTTPS, por lo que el canal está cifrado
de extremo a extremo. El encabezado de autenticación lleva el token JWT
que identifica el dispositivo.

## 6.2 Validación en el servidor

El servidor recibe el buffer binario y lo deserializa usando el mismo
archivo .proto. Para cada operación del lote, ejecuta dos validaciones
en secuencia.

**validación del checksum**. El servidor toma los datos del registro
recibido, calcula su propio hash SHA-256 y lo compara con el checksum
que envió el cliente. Si los hashes no coinciden, significa que el dato
se alteró o corrompió durante el tránsito, y ese registro se rechaza. No
se guarda nada, y el ID va a la lista de errores en la respuesta.

**validación de versión**, que aplica solo para operaciones de tipo
update y delete. El servidor busca en PostgreSQL si ya tiene un registro
con ese ID y compara la versión que envía el cliente con la versión que
tiene almacenada. Si la versión del servidor es igual o menor a la del
cliente, no hay problema: el cliente tiene la versión más reciente o al
menos la misma. Pero si la versión del servidor es mayor, significa que
otro dispositivo ya modificó ese registro después de la última
sincronización del cliente. En ese caso se declara un conflicto: el
servidor no sobrescribe nada y reporta el ID en la lista de conflictos
de la respuesta.

## 6.3 Aplicación atómica

Todas las operaciones que superaron las dos validaciones se aplican
dentro de una sola transacción SQL en PostgreSQL. Esto significa que si
el lote tiene veinte cambios y diecinueve se aplican bien pero el último
falla por un error inesperado del servidor, ninguno de los diecinueve
queda guardado. La transacción se revierte completa. Esto protege la
integridad de la base de datos central: nunca queda en un estado a
medias.

Si la transacción se completa con éxito, el servidor actualiza la
versión de cada registro aplicado y registra el evento en la tabla
sync_log.

## 6.4 Respuesta y actualización local

El servidor construye un SyncResponse con tres listas y lo serializa en
protobuf para devolverlo al cliente. La app recibe la respuesta y actúa
sobre cada categoría de forma distinta.

Para los registros aplicados correctamente, la app actualiza su
sync_status a synced, elimina sus entradas de la outbox, y quita el
indicador visual de pendiente. El ciclo para esos registros está
completo.

Para los registros en conflicto, la app los mantiene visibles pero los
marca de forma especial en la interfaz. El usuario puede ver qué campos
difieren entre su versión local y la versión del servidor, y decidir
cuál conservar. Una vez que el usuario resuelve el conflicto, el
registro vuelve a la outbox como pending_update con la versión ganadora
y se sincroniza en el próximo ciclo.

Para los registros rechazados por checksum inválido, la app los vuelve a
encolar automáticamente para reintentarlos en la próxima sincronización.
Si un registro falla repetidamente, el campo de intentos en la outbox se
incrementa y eventualmente se puede alertar al usuario de que hay un
problema persistente.

**7. Base de Datos de Ejemplo**

Para ilustrar cómo funciona el sistema en un escenario real, a
continuación se describe el estado de las bases de datos con cinco
registros de ejemplo.

## 7.1 Registros en PostgreSQL

La base de datos central contiene los siguientes registros
sincronizados:

**Registro 1.** Cédula 1082654321. María Fernanda Rodríguez Pérez,
nacida el 15 de marzo de 1990, docente de profesión, reside en la
Carrera 5 \#12-34 de Neiva, contacto 3215678901. Este registro va en
versión 3, lo que indica que ha sido editado dos veces desde su
creación. Está sincronizado y no tiene conflictos.

**Registro 2.** Cédula 91354872. Carlos Andrés Patiño, nacido el 22 de
noviembre de 1985, agricultor, reside en la Calle 8 \#7-20 de Florencia,
contacto 3104523678. Versión 1, creado una sola vez y nunca editado.

**Registro 3.** Cédula 55123456. Lucía Mendoza, nacida el 4 de julio de
2001, estudiante, reside en la Vereda El Triunfo kilómetro 4, contacto
3008891234. Versión 2, editada una vez. Este registro fue creado offline
desde un dispositivo en campo y sincronizado cuando el funcionario
volvió al casco urbano.

**Registro 4.** Cédula 71890234. Andrés Gómez, nacido el 30 de
septiembre de 1978, comerciante, reside en la Carrera 12 \#3-45 de
Neiva, contacto 3177654321. Versión 1.

**Registro 5.** Cédula 1193401234. Sara Patricia Torres, nacida el 19 de
enero de 2003, enfermera, reside en la Calle 15 \#8-10 de Florencia,
contacto 3226671122. Versión 4, el registro con más ediciones del
sistema. El sync_log muestra que sufrió un conflicto de versión en su
tercera edición, que fue resuelto manualmente por el usuario eligiendo
conservar la versión del servidor.

## 7.2 Estado de la outbox en un dispositivo en campo

Supongamos que un funcionario acaba de regresar al municipio después de
dos días sin señal. Su dispositivo tiene tres operaciones pendientes en
la outbox:

**Operación 1 — create:** Una persona nueva registrada ayer en la
vereda, con cédula 55987654, nombre Pedro Quintero, agricultor. Este
registro nunca ha llegado al servidor. La app lo creó con UUID, lo
hasheó y lo puso en la cola con cero intentos de sincronización.

**Operación 2 — update:** El registro de Sara Patricia Torres (cédula
1193401234) fue editado para corregir un número de teléfono. El
dispositivo tiene la versión 4 del registro y está enviando la versión
5. En el servidor también está la versión 4, así que no habrá conflicto.

**Operación 3 — delete:** El registro de un ciudadano fue marcado para
eliminar porque estaba duplicado con una cédula diferente. El registro
sigue en SQLite pero oculto, esperando que el servidor confirme la
eliminación.

Cuando el dispositivo detecte la señal y ejecute el sync, estas tres
operaciones viajarán juntas en un solo SyncRequest. Si todo va bien, el
servidor responderá con los tres IDs en la lista de aplicados, y la
outbox quedará vacía.

**8. Síntesis de la Arquitectura**

El diseño del Banco de Personas descansa sobre un principio central: el
dispositivo nunca depende del servidor para responder al usuario. Toda
interacción del usuario se completa primero en la base de datos local, y
el servidor es tratado como un destino eventual, no como un requisito
inmediato.

La base de datos local SQLite garantiza que la app funcione offline. La
outbox garantiza que ninguna operación se pierda aunque el dispositivo
se apague o la red falle a mitad de una sincronización. El esquema
.proto de Protocol Buffers garantiza que los datos tengan el tipo y la
estructura correctos antes de viajar. El checksum SHA-256 garantiza que
los datos que llegaron al servidor son exactamente los mismos que
salieron del dispositivo. El campo version garantiza que no se
sobrescriban registros sin detectar conflictos previos. Las
transacciones atómicas en PostgreSQL garantizan que la base de datos
central nunca quede en un estado a medias.

Cada uno de estos mecanismos responde a una falla posible del sistema.
En conjunto, forman una arquitectura que cumple las tres condiciones del
proyecto: instalación nativa en Android, operación completamente
offline, e integridad verificada de los datos al sincronizar.

*persona*), pero su estructura es lo suficientemente modular para
extenderse a múltiples entidades con diferentes relaciones sin cambiar
la arquitectura base de sincronización.
