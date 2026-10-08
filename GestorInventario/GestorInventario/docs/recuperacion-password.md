# Recuperacion local de admin o empleado

La entrada independiente `com.gestorinventario.recovery.PasswordRecovery` exige
exactamente `--spring.profiles.active=recuperacion`. No es un runner del servidor
normal. El contexto carga solo la conexion JDBC y el BCrypt existente: no activa
Flyway, JPA, inicializacion SQL ni servidor web.

Se selecciona expresamente `admin` (ID 1) o `empleado` (ID 2). No existe un destino
por defecto ni se admiten otros usuarios. La herramienta muestra ID y username y
exige escribir `RECUPERAR admin` o `RECUPERAR empleado` antes de solicitar la nueva
contrasena. Una seleccion o confirmacion incorrecta cancela sin conectar a la base.

Se verifica el ID seleccionado dentro de una transaccion, bloqueando su fila y
exigiendo que coincida su username. La unica escritura es
`UPDATE usuarios SET password = ? WHERE id = ? AND username = ?`.
Si no se modifica exactamente una fila, la transaccion se revierte. No se crean
usuarios ni se cambian rol, estado, ID o relaciones.

## IntelliJ (Windows / PowerShell)

1. Usar el JDK 25 del proyecto y recargar Maven.
2. Revisar que la conexion habitual `spring.datasource.*` apunta a la base deseada.
   No anadir la nueva contrasena a ninguna propiedad.
3. En la ventana Maven, usar Execute Maven Goal y ejecutar
   `package`. Esto compila, ejecuta toda la bateria de pruebas y empaqueta;
   no inicia la aplicacion ni recupera contrasenas. Es imprescindible regenerar
   el JAR para evitar utilizar una version anterior limitada a empleado.
4. Abrir View > Tool Windows > Terminal en la carpeta que contiene `pom.xml`.
   Usar Terminal, no la consola Run, para disponer de entrada oculta.
5. Solo cuando se quiera efectuar la recuperacion real, ejecutar:

   ```powershell
   java '-Dloader.main=com.gestorinventario.recovery.PasswordRecovery' -cp target/gestor-inventario-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --spring.profiles.active=recuperacion
   ```

   Si `java` no esta disponible en la terminal, usar la ruta del JDK del proyecto:

   ```powershell
   & 'C:/Users/DAM2/.jdks/temurin-25.0.4.1/bin/java.exe' '-Dloader.main=com.gestorinventario.recovery.PasswordRecovery' -cp target/gestor-inventario-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --spring.profiles.active=recuperacion
   ```

6. Escribir `admin` o `empleado`. Revisar el ID y username mostrados y escribir
   exactamente `RECUPERAR admin` o `RECUPERAR empleado`, segun la seleccion.
   Despues escribir la nueva contrasena y repetirla en la confirmacion. No se muestran
   caracteres. Se exige al menos 8 caracteres, contenido no blanco y un maximo
   de 72 bytes UTF-8 (limite de BCrypt). La entrada no admite argumentos extra.
7. Esperar el mensaje de exito y el cierre del proceso. Si no hay consola real,
   se cancela; no existe alternativa de entrada visible ni por archivo.
8. Arrancar normalmente `GestorInventarioApplication` sin el perfil de recuperacion
   y probar `POST /api/auth/login` con el username seleccionado y la nueva contrasena.

La herramienta oculta los detalles de excepciones para evitar exponer parametros
SQL. Si falla, comprobar entrada, identidad y conexion sin registrar secretos.
Los arrays de entrada se limpian al terminar; el hash se usa solo en memoria y
en la escritura de la columna password. No se imprime ni se guarda en archivos.

## Pruebas

`mvn test` ejecuta toda la bateria. `PasswordRecoveryTest` usa JDBC y transacciones
simulados y BCrypt real. Verifica ambos destinos, seleccion y confirmacion,
la sentencia limitada a password, la identidad,
la ausencia de escrituras adicionales, commit/rollback, entradas invalidas y el
aislamiento respecto del arranque normal. `RecoveryServiceIntegrationTest` ejecuta
la recuperacion de ambos destinos exclusivamente contra H2 en memoria y comprueba
que no cambian otros usuarios, nombres, roles, estados, productos ni movimientos.
Ninguna prueba de recuperacion conecta a MySQL ni ejecuta Flyway.
