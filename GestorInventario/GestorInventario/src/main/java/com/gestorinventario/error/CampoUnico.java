package com.gestorinventario.error;

import java.util.Locale;

/** Campos UNIQUE editables comprobados en las entidades y en la migración V1. */
public enum CampoUnico {
    PRODUCTO("codigo", "uk_productos_codigo",
            "Ya existe un producto con ese código. Introduce un código diferente."),
    CATEGORIA("nombre", "uk_categorias_nombre",
            "Ya existe una categoría con ese nombre. Introduce un nombre diferente."),
    PROVEEDOR("cif", "uk_proveedores_cif",
            "Ya existe un proveedor con ese CIF. Revisa el CIF introducido."),
    USUARIO("username", "uk_usuarios_username",
            "El nombre de usuario ya existe. Introduce uno diferente.");

    private final String campo;
    private final String restriccion;
    private final String mensaje;

    CampoUnico(String campo, String restriccion, String mensaje) {
        this.campo = campo;
        this.restriccion = restriccion;
        this.mensaje = mensaje;
    }

    public String campo() { return campo; }
    public String mensaje() { return mensaje; }

    // Compatibilidad para excepciones sin contexto de servicio; nunca se busca
    // en el valor duplicado ni se adivinan índices desconocidos.
    public static CampoUnico porRestriccion(String nombre) {
        if (nombre == null) return null;
        String clave = nombre.toLowerCase(Locale.ROOT).replace("\"", "").replace("`", "");
        clave = clave.substring(clave.lastIndexOf('.') + 1);
        for (CampoUnico campo : values()) {
            if (clave.equals(campo.restriccion) || clave.startsWith(campo.restriccion + "_index_")) return campo;
        }
        return null;
    }
}
