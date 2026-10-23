package com.gestorinventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Un ID enviado en JSON nunca se asigna a la entidad.
@JsonIgnoreProperties("id")
public class CategoriaRequest {
    @NotBlank
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres.")
    private String nombre;

    @Size(max = 255, message = "La descripción no puede superar los 255 caracteres.")
    private String descripcion;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
