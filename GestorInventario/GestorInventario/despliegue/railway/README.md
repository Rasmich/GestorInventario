# Demo en Railway

Esta configuración procede de la copia local `demo-cloud`. Se integra en el repositorio para reutilizar el backend y frontend principales, sin duplicar la aplicación completa.

## Construcción

El contexto Docker debe ser la raíz de la aplicación (`GestorInventario/GestorInventario/`), no la raíz del repositorio. Desde esa carpeta:

```powershell
docker build -f despliegue/railway/Dockerfile -t gestor-inventario-railway .
```

El Dockerfile compila el frontend, copia el backend y aplica `overlay/` antes de generar el JAR. No modifica los archivos de desarrollo ni la demo Docker local.

La capa específica conserva los cambios de la copia de nube:
- Escucha en `0.0.0.0`.
- El guardián comprueba el identificador del proyecto Railway y su base MySQL privada.

Las variables del entorno demo deben configurarse en Railway. No incluir `.env.cloud` en Git ni en la imagen. Consultar las variables requeridas en `src/main/resources/application-demo.properties` y en las clases del paquete `demo`.

## Estado

La documentación de la copia original registraba una verificación HTTPS previa. Esta integración no ha desplegado una nueva versión ni ha comprobado el servicio en ejecución. La configuración del servicio Railway debe revisarse para establecer el contexto y Dockerfile indicados antes de utilizar esta estructura.

La demo Docker local sigue utilizando el Dockerfile y compose.demo.yml de la raíz de la aplicación. Su documentación está en `docs/demo.md`.

Si se cambian las validaciones de DemoDatabaseGuard en el código principal, revisar también la variante de overlay. Las bases y los datos de ambos entornos son independientes.
