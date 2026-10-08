package com.gestorinventario.dto;

import jakarta.validation.constraints.NotNull;

public record UsuarioEstadoRequest(@NotNull Boolean activo) {
}
