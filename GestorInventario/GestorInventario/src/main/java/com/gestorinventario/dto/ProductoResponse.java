package com.gestorinventario.dto;

import java.math.BigDecimal;
import com.gestorinventario.model.EstadoStock;

public record ProductoResponse(Long id, String codigo, String nombre, String descripcion,
                               BigDecimal precioCompra, BigDecimal precioVenta,
                               Integer stock, Integer stockMinimo, Boolean activo, Long version,
                               CategoriaResponse categoria, ProveedorResponse proveedor,
                               EstadoStock estadoStock) {
}
