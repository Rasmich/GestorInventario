package com.gestorinventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestorinventario.model.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

// Nunca se trasladan id ni activo recibidos del cliente a la entidad.
@JsonIgnoreProperties({"id", "activo"})
public class UsuarioRequest {
    @NotBlank
    @Size(max = 255)
    private String nombre;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "\\S+")
    private String username;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotNull
    private Rol rol;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
}
