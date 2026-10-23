package com.gestorinventario.error;

import java.util.Map;

/** Añade el campo a validaciones existentes del servicio, sin cambiar sus reglas. */
public class ValidacionCamposException extends IllegalArgumentException {
    private final Map<String, String> campos;

    public ValidacionCamposException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> campos() { return campos; }
}
