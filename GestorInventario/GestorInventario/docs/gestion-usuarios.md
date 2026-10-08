# Gestion de usuarios

Todos los endpoints requieren `Authorization: Bearer <token>` de un ADMIN activo.
EMPLEADO recibe 403; JWT ausente, invalido o de un usuario inactivo recibe 401.

| Metodo | Ruta | Cuerpo | Exito |
| --- | --- | --- | --- |
| GET | /api/usuarios | Sin cuerpo | 200, lista |
| GET | /api/usuarios/{id} | Sin cuerpo | 200, usuario |
| POST | /api/usuarios | nombre, username, password, rol | 201, usuario |
| PUT | /api/usuarios/{id} | nombre, username, rol | 200, usuario |
| PATCH | /api/usuarios/{id}/estado | activo (boolean) | 200, usuario |
| PATCH | /api/usuarios/{id}/password | password | 200, usuario |

Ejemplo de actualizacion:

```json
{"nombre":"Empleado de almacen","username":"empleado","rol":"EMPLEADO"}
```

Ejemplo de desactivacion:

```json
{"activo":false}
```

Respuesta de usuario (solo campos publicos):

```json
{"id":2,"nombre":"Empleado de almacen","username":"empleado","rol":"EMPLEADO","activo":false}
```

Los nombres son obligatorios y de hasta 255 caracteres. Username no admite
espacios. Rol debe ser ADMIN o EMPLEADO. Password requiere al menos 8 caracteres
y como maximo 72 bytes UTF-8; se codifica una vez mediante el BCrypt existente.
No se devuelve password ni hash, tampoco despues del cambio de password.

PUT no cambia ID, estado ni password. El estado y la password tienen endpoints
separados. No hay endpoint de borrado: se mantienen los usuarios y las claves
foraneas del historial. Cambiar username conserva su ID y los movimientos.

## Protecciones y respuestas

- 400: campos invalidos, nulos, enum incorrecto o password fuera de limites.
- 401: no autenticado, token invalido o usuario inactivo; tambien login incorrecto.
- 403: usuario sin rol ADMIN o actor que pierde ese rol antes de la transaccion.
- 404: usuario objetivo inexistente.
- 409: username duplicado, autodesactivacion, perdida del ultimo ADMIN activo
  o conflicto de bloqueo/concurrencia.

Se bloquean los administradores activos por ID antes de modificar usuarios y se
valida su estado dentro de la transaccion. Dos desactivaciones o cambios de rol
simultaneos no deben dejar cero administradores activos. Un ADMIN inactivo no
cuenta para esta proteccion. Un administrador puede dejar de ser ADMIN si queda
otro administrador activo, pero no puede desactivarse a si mismo.

## JWT anteriores

Los nuevos tokens incluyen `uid` (ID persistido) ademas de username como subject,
rol y fechas. El filtro verifica firma, expiracion, coincidencia del ID y username,
y lee de la base el rol y estado actuales. El claim rol no concede permisos.

Tras instalar esta version, tokens previos sin uid se rechazan: hay que iniciar
sesion de nuevo. Cambiar username invalida sus tokens anteriores; reutilizar el
nombre en otra cuenta nunca transfiere aquellos tokens a la nueva identidad.
Cambiar rol aplica los permisos actuales en la siguiente peticion, incluso con
un token anterior. Desactivar bloquea tanto el login como tokens emitidos antes.

Cambiar password no revoca tokens ya emitidos: continuan hasta expirar si la
cuenta sigue activa y mantiene username. Al reactivar una cuenta, sus tokens
anteriores aun vigentes pueden volver a autorizarse. No se ha anadido un sistema
de revocacion ni un campo persistido de version del token.

## Verificacion

`mvn test` ejecuta las pruebas existentes y las de gestion de usuarios. Las
nuevas pruebas usan HTTP simulado, JWT y BCrypt reales, servicios y repositorios
JPA reales y H2 en memoria. Incluyen una comprobacion concurrente del ultimo
administrador. No arrancan la aplicacion real, no acceden a MySQL ni ejecutan
Flyway. La configuracion y las migraciones existentes quedan intactas.
