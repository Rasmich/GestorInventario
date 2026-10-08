package com.gestorinventario.dto;

import com.gestorinventario.model.TipoMovimiento;
import java.time.LocalDateTime;

public record MovimientoResponse(Long id, ProductoResponse producto, TipoMovimiento tipo,
                                 Integer cantidad, LocalDateTime fecha, String observaciones,
                                 UsuarioMovimientoResponse usuario) {
}
