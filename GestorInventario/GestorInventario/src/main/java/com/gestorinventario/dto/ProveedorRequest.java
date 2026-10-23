package com.gestorinventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// Un ID enviado en JSON nunca se asigna a la entidad.
@JsonIgnoreProperties("id")
public class ProveedorRequest {
    @NotBlank
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres.")
    private String nombre;

    @NotBlank
    @Size(max = 255, message = "El CIF no puede superar los 255 caracteres.")
    private String cif;

    @Size(max = 255, message = "El teléfono no puede superar los 255 caracteres.")
    private String telefono;

    @Email
    @Size(max = 255, message = "El correo electrónico no puede superar los 255 caracteres.")
    private String email;

    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres.")
    private String direccion;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCif() {
        return cif;
    }

    public void setCif(String cif) {
        this.cif = cif;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}
