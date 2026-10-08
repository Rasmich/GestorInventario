package com.gestorinventario.dto;

public record ProveedorResponse(Long id, String nombre, String cif,
                                String telefono, String email, String direccion) {
}
