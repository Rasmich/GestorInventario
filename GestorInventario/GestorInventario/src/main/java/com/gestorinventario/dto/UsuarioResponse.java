package com.gestorinventario.dto;

import com.gestorinventario.model.Rol;

// La respuesta contiene solo datos publicos: no hay campo password ni hash.
public record UsuarioResponse(Long id, String nombre, String username, Rol rol, Boolean activo) {
}
