package com.gestorinventario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Clase ordinaria para no generar un toString que incluya la contrasena.
public class UsuarioPasswordRequest {
    @NotBlank
    @Size(min = 8, max = 72)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
