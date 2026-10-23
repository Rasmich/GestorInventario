# GestorInventario

Gestor de inventario con backend Java/Spring Boot y frontend React.

## Estructura

La raíz de la aplicación es `GestorInventario/GestorInventario/`:

- `src/main/`: backend y migraciones de base de datos.
- `src/test/`: pruebas del backend.
- `frontend/`: aplicación React y sus pruebas.
- `docs/`: documentación funcional y de la demo local.
- `compose.demo.yml` y `Dockerfile`: demo Docker local.
- `despliegue/railway/`: Dockerfile y ajustes exclusivos de la demo en Railway.

Las carpetas `GestorInventario/web/` y `GestorInventario/css/` se conservan como material adicional; el frontend usado por la demo Docker es el de React.

## Entornos

Consultar `GestorInventario/GestorInventario/docs/demo.md` para la demo Docker local y `GestorInventario/GestorInventario/despliegue/railway/README.md` para Railway.

El código principal se mantiene una sola vez. La construcción de Railway sustituye únicamente el guardián de la base de datos y las propiedades del perfil demo mediante una capa específica de configuración.

## Configuración privada y datos

Las credenciales se configuran localmente o en el alojamiento. No publicar archivos `.env`, contraseñas ni claves. `.env.demo.example` es una plantilla sin valores privados.

Git no incluye los datos de MySQL, los volúmenes Docker ni la base de Railway. Las migraciones SQL definen la estructura de la base.

La licencia está pendiente de definir.
