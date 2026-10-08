package com.gestorinventario.dto;

import com.gestorinventario.model.Rol;

public record LoginResponse(Long id, String nombre, String username, Rol rol, String token) {
}
