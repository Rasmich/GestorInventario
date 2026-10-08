package com.gestorinventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestorinventario.model.Rol;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties({"id", "activo", "password"})
public record UsuarioActualizacionRequest(
        @NotBlank @Size(max = 255) String nombre,
        @NotBlank @Size(max = 255) @Pattern(regexp = "\\S+") String username,
        @NotNull Rol rol) {
}
