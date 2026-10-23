# Demo de GestorInventario

## Estado verificado

Demo local funcionando en http://localhost:18081. No se ha publicado un enlace externo ni se ha accedido a la base real. Docker Desktop debe estar activo.

La imagen compila React con Node 24 y empaqueta el resultado dentro del JAR de Spring Boot con Java 25. El proceso final usa un usuario sin privilegios. La compilacion Docker omite tests; estos se ejecutan por separado.

## Aislamiento

Compose fija la conexion a jdbc:mysql://mysql-demo:3306/gestor_inventario_demo, con el usuario demo_app. MySQL tiene un volumen exclusivo del proyecto gestorinventario-demo y solo pertenece a la red interna demo-interna, sin puertos publicados. La aplicacion pertenece tambien a demo-web y publica exclusivamente 127.0.0.1:18081. No proporciona HTTPS ni acceso externo.

El perfil demo comprueba las propiedades efectivas antes de instanciar DataSource o Flyway, rechaza otros destinos y no permite combinar demo con recuperacion. No usar overrides de Compose ni redes, volumenes o credenciales de la aplicacion real.

Flyway crea el esquema de la demo nueva mediante las migraciones existentes; Hibernate solo valida. Baseline automatico, Flyway clean e inicializacion SQL generica estan desactivados. No ejecutar contra bases existentes.

El contexto Docker excluye application.properties, archivos .env y configuracion local. Solo incluye el perfil demo y las migraciones como recursos del backend.

## Credenciales

Se ha generado .env.demo local con credenciales nuevas y aleatorias, excluido de Git y de la imagen. No se imprimen en las pruebas. Consultarlo localmente para acceder; no adjuntarlo ni compartirlo completo.

| Cuenta | Rol | Variable de contrasena |
| --- | --- | --- |
| demo_admin | ADMIN | DEMO_ADMIN_PASSWORD |
| demo_empleado1 | EMPLEADO | DEMO_EMPLOYEE1_PASSWORD |
| demo_empleado2 | EMPLEADO | DEMO_EMPLOYEE2_PASSWORD |
| demo_empleado3 | EMPLEADO | DEMO_EMPLOYEE3_PASSWORD |

DEMO_DB_PASSWORD es la contrasena nueva de MySQL. DEMO_JWT_SECRET contiene Base64 de 32 bytes aleatorios como minimo. Las cuatro contrasenas de cuentas deben ser distintas, tener al menos 8 caracteres y un maximo de 72 bytes UTF-8. No hay valores predeterminados. Se almacenan con BCrypt. Nunca usar variables VITE para secretos.

.env.demo.example sirve de plantilla sin valores para otro entorno. Cambiar .env.demo no cambia las contrasenas de usuarios o MySQL ya creados. No ejecutar docker compose config sin --quiet con credenciales reales, porque puede mostrarlas.

## Datos de prueba

El inicializador es exclusivo del perfil demo y transaccional. Solo carga datos cuando usuarios, categorias, proveedores, productos y movimientos estan vacios; si cualquiera contiene datos, no modifica nada. Usar una unica instancia durante la primera carga.

Crea cuatro cuentas, dos categorias, un proveedor ficticio con correo example.invalid y tres productos con stock 30, 3 y 0. Las existencias positivas tienen entradas asociadas al administrador. Los usuarios comparten el inventario ficticio. Reiniciar no repone datos ni restablece contrasenas.

## Operaciones locales

Desde la carpeta que contiene compose.demo.yml:

```powershell
docker compose --env-file .env.demo -f compose.demo.yml up --build -d
docker compose --env-file .env.demo -f compose.demo.yml ps
```

Para detener esta demo conservando sus datos:

```powershell
docker compose --env-file .env.demo -f compose.demo.yml stop
```

No borrar volumenes para reiniciar datos sin comprobar el destino exacto y autorizar expresamente el borrado.

## Validacion realizada

- Cinco pruebas aisladas: destino y perfiles permitidos, registro del componente en Spring, base existente intacta, rechazo de contrasenas ausentes o repetidas y carga con BCrypt.
- Compilacion local de React y backend; construccion y arranque de imagen completa con MySQL 8.4.
- Frontend servido por Spring con HTTP 200 y referencia a recursos compilados.
- Login de las cuatro cuentas; listado de cuatro usuarios mediante administrador y tres productos mediante empleados.
- Empleados reciben 403 al consultar gestion de usuarios; acceso anonimo al inventario recibe 401.
- Stocks iniciales 30, 3 y 0.
- Categoria ficticia temporal conservada tras reiniciar la aplicacion; categoria retirada despues. Cuentas sin duplicados y login conservado.
- Contenedores sin red rechazan configuracion ausente y destino distinto antes de conectar.

La comprobacion HTTP no sustituye una revision visual completa en navegador ni una prueba exhaustiva de todas las operaciones de negocio.

## Pendiente antes de compartir

Elegir alojamiento y revisar coste, configurar HTTPS y acceso externo sobre un entorno de demo independiente, y obtener aprobacion antes de publicar. La licencia propietaria academica se tratara despues.