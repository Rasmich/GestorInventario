# Auditoría de errores de GestorInventario

Fecha: 20 de octubre de 2026. Alcance: entidades, repositorios, migraciones V1–V3,
DTO, controladores, servicios, seguridad HTTP y todos los componentes del frontend.
La inspección de restricciones se basa en el código y las migraciones; no se ha
consultado ni modificado la base MySQL del usuario.

## Módulos y restricciones

| Módulo | UNIQUE editable definido en entidad/V1 | Hallazgo | Resultado |
| --- | --- | --- | --- |
| Productos | `codigo` / `uk_productos_codigo` | Traducción local de duplicados, repetida en el servicio | Usa la traducción común, en creación y edición; conserva el control de versión |
| Categorías | `nombre` / `uk_categorias_nombre` | Traducción local de duplicados, repetida en el servicio | Usa la traducción común, con flush antes de abandonar el contexto de categoría |
| Proveedores | `cif` / `uk_proveedores_cif` | No traducía el conflicto en el servicio; un índice desconocido terminaba en el mensaje genérico; UPDATE podía diferirse al commit | Traducción común y flush en creación/edición; mensaje específico de CIF |
| Usuarios | `username` / `uk_usuarios_username` | Comprobación previa correcta, pero un duplicado posterior a esa consulta dependía del nombre del índice | Comprobación previa conservada; traducción común también al guardar; edición de username permitida |
| Movimientos | Ninguno | El lector frontend descartaba `errores`; las validaciones del servicio no incluían campos | Lector común; producto/tipo ausentes y cantidad incorrecta identifican solo los campos afectados |
| Login | Ninguno | El formulario reemplazaba una respuesta de validación por un aviso de acceso | Lector común para errores 400 y campos; 401 conserva el aviso de credenciales |
| Inicio y listados/bajas | No crean claves UNIQUE adicionales | Lectores de respuesta distintos entre componentes | Reutilizan el lector común; errores generales sin atribución a campos |
| Recuperación de contraseña | Ninguno adicional | Herramienta de consola, sin formulario ni endpoint web; comprueba identidad antes de actualizar | Sin cambios; no entra en la traducción de campos UNIQUE |

Todas las tablas tienen además una clave primaria `id` autogenerada. No es un
campo editable del formulario y no se redacta un mensaje de duplicado para ella.
V2 conserva UNIQUE/FK y exige CIF y proveedor; V3 añade una FK de usuario y un
índice **no único** a movimientos. No hay otra clave UNIQUE declarada en el proyecto.
Una base adoptada por baseline puede tener nombres distintos: el nombre efectivo
de sus índices y el formato exacto de su excepción quedan pendientes de verificar.

## Causa y solución reutilizable

El handler identificaba un duplicado por MySQL 1062 o SQLState 23505, pero para
redactar el mensaje específico necesitaba reconocer el índice de V1. Eso no es
suficiente para bases con otros nombres o drivers que no entregan esa información.
Productos y Categorías ya habían evitado ese problema, duplicando lógica SQL.

`IntegridadDatos` centraliza el recorrido de causas y `SQLException.nextException`,
con protección frente a ciclos. No interpreta SQLState 23000 por sí solo como
duplicado: también se usa para FK y otros errores de integridad. Solo traduce
1062/23505 dentro de las cuatro escrituras cuyo único campo UNIQUE editable se
ha identificado. `CampoUnico` contiene los cuatro campos y sus mensajes públicos.
La excepción conserva su causa original y HTTP 409. Otros errores se propagan.

La escritura incluye flush para capturar también UPDATE antes del commit. La
restricción de la base de datos sigue siendo la protección definitiva y el fallo
revierte la transacción. No se reintenta ni se escribe después de la excepción.
No se aplica la traducción de username al cambio de estado o contraseña, ni la
traducción de código a movimientos o bajas: esas operaciones no editan esas claves.

El handler mantiene el reconocimiento de índices conocidos como compatibilidad
para errores sin contexto. Un duplicado desconocido fuera de las operaciones
identificadas conserva el aviso general: no se adivina el campo. Si se introduce
otra restricción UNIQUE, debe revisarse el contexto; no debe asignarse a un campo
existente sin comprobar qué clave se ha violado.

## Validación y códigos HTTP

| Código | Tratamiento auditado |
| --- | --- |
| 400 | Bean Validation mantiene textos en español y `errores`; añade `erroresCampos`. JSON/tipos inválidos siguen con aviso general. Validaciones existentes de movimientos y longitud UTF-8 de contraseña añaden campos sin cambiar reglas ni estados. |
| 401 | JWT y credenciales conservan su tratamiento. `fetchApi` notifica sesión caducada solo para una petición autenticada; no marca campos por credenciales incorrectas. |
| 403 | Conserva el detalle de permisos y no cierra la sesión. No se marca ningún campo. |
| 404 | Conserva la razón del recurso inexistente; si falta JSON hay un texto comprensible. Se trata como aviso general: no se confunde un id de ruta con un campo editable. |
| 409 | Duplicados identificados llevan campo. FK 1451/1452, concurrencia y protección del último administrador conservan su tratamiento. Los errores generales no se convierten en duplicados. |
| 422 | No hay endpoints que lo emitan actualmente. El lector frontend conserva detalle/campos si se recibe y tiene un texto alternativo comprensible. No se han cambiado errores 400 a 422. |
| 5xx/red | `fetchApi` conserva el aviso seguro ya existente; no consume ni expone HTML/SQL interno ni atribuye el fallo a campos. Un error inesperado del servicio no se captura como duplicado. |

Se conservan las validaciones de obligatoriedad, longitudes, email, precios,
decimales, cantidades y stock, así como las restricciones y los permisos existentes.
Movimientos valida producto/tipo/cantidad en el servicio; no se han añadido
anotaciones obligatorias al DTO. Los avisos de stock insuficiente o producto
inactivo siguen siendo generales, sin marcar arbitrariamente un campo.

## Frontend y accesibilidad

`leerRespuesta` sustituye los lectores locales: conserva `detail`, todos los
mensajes de `errores`, HTTP y `erroresCampos`; admite 204 y controla JSON inesperado.
`useErroresFormulario` acepta tanto strings anteriores como errores con campos.
`erroresPorCampo` limita los campos explícitos a los conocidos para cada formulario
y mantiene el reconocimiento anterior cuando no hay campos explícitos.

El hook filtra además por los controles presentes: una contraseña que no aparece
en edición no queda marcada por un campo recibido. Mantiene `aria-invalid`,
`aria-describedby`, los ids de mensajes y el foco al primer control inválido en
orden del formulario, después de levantar el estado ocupado. Los avisos generales
se muestran sin invalidar controles. Los indicadores de obligatoriedad se conservan.

Revisión de conservación de datos: Productos solo llama `alGuardar` tras éxito;
Categorías, Proveedores y Usuarios solo cierran el formulario tras éxito;
Movimientos solo limpia cantidad tras éxito; Login solo limpia contraseña tras
éxito o cierre de sesión. Ningún catch reinicia los valores del formulario. La
invalidación real de una sesión sigue cerrándola como antes.

## Pruebas y límites de comprobación

Se mantienen las suites previas de productos/categorías, validación, permisos,
login, concurrencia, precios, stock y recuperación. Las pruebas nuevas cubren:

- Detección común para las cuatro claves, sin texto/nombre de índice, errores
  encadenados, ciclos, y propagación de errores de integridad o inesperados.
- Proveedores en POST/PUT, tanto al guardar como al ejecutar flush.
- Usuarios en POST/PUT, tanto en la comprobación previa como si la base rechaza
  después de ella (simulación determinista de carrera, sin modificar permisos).
- Proveedores y usuarios con servicios/JPA y H2 temporal: rechazo y conservación
  de datos. Las suites existentes comprueban rollback de productos y categorías.
- Campos de validación de login, movimientos y contraseña UTF-8.
- Lector frontend común, cuatro duplicados en POST/PUT, validaciones 400/422,
  campos explícitos, propiedades ARIA, foco, errores generales, respuestas
  malformadas y compatibilidad con mensajes anteriores. Se mantienen las pruebas
  de red, 401, 403 y 5xx.

No se usan datos reales. Las pruebas frontend ejecutan API y utilidades con Node;
no sustituyen una prueba de interacción del DOM completo. Sigue siendo necesario
verificar manualmente en navegador el foco real y la conservación de todos los
valores en cada modo del formulario y en MySQL el driver/índices efectivos y una
carrera real entre sesiones. No se ha realizado ninguna de esas operaciones sobre
la base de datos del usuario.

## Archivos de esta corrección

- Backend: `error/CampoUnico.java`, `error/ConflictoUnicoException.java`,
  `error/IntegridadDatos.java`, `error/ValidacionCamposException.java`,
  `controller/GlobalExceptionHandler.java` y los servicios `ProductoService`,
  `CategoriaService`, `ProveedorService`, `UsuarioService`, `MovimientoService`.
- Frontend (`frontend/src`): `api.js`, `erroresFormulario.js`,
  `useErroresFormulario.js`, `App.jsx`, `FormularioProducto.jsx`, `Categorias.jsx`,
  `Proveedores.jsx`, `Usuarios.jsx`, `Movimientos.jsx`, `Productos.jsx`, `Inicio.jsx`.
- Pruebas: `src/test/java/com/gestorinventario/ConflictosDatosTest.java` y
  `frontend/tests/respuestasFormulario.test.mjs`.
- Este informe: `docs/auditoria-errores.md`.

Sin cambios en DTO, entidades, repositorios, migraciones, configuración de
autenticación, permisos, dependencias o base de datos. Sin commit ni push.

## Resultado de la validación

- Maven `test`: 349 pruebas, 0 fallos, 0 errores, 0 omitidas. Incluye las 24
  pruebas de la nueva suite global y las regresiones previas de todos los módulos.
- `node --test tests/*.test.mjs`: 54 pruebas, todas correctas.
- `npm run build`: correcto.
- `npm run lint`: correcto.
- `git diff --check`: sin errores de espacios; Git avisa de conversión LF/CRLF
  según la configuración existente de Windows.

La ejecución de Maven necesitó acceso fuera del aislamiento para leer el JDK y
la caché de dependencias. No se instalaron dependencias nuevas. Los avisos de
Mockito sobre carga dinámica del agente no impidieron ejecutar las pruebas.
