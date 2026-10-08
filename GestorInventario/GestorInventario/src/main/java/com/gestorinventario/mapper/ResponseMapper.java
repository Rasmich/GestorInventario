package com.gestorinventario.mapper;

import com.gestorinventario.dto.*;
import com.gestorinventario.model.*;

// Lista explicita de campos publicos. Los DTO nunca conservan referencias a entidades JPA.
public final class ResponseMapper {
    private ResponseMapper() {
    }

    public static CategoriaResponse toCategoria(Categoria categoria) {
        if (categoria == null) {
            return null;
        }
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), categoria.getDescripcion());
    }

    public static ProveedorResponse toProveedor(Proveedor proveedor) {
        if (proveedor == null) {
            return null;
        }
        return new ProveedorResponse(proveedor.getId(), proveedor.getNombre(), proveedor.getCif(),
                proveedor.getTelefono(), proveedor.getEmail(), proveedor.getDireccion());
    }

    public static ProductoResponse toProducto(Producto producto) {
        if (producto == null) {
            return null;
        }
        return new ProductoResponse(producto.getId(), producto.getCodigo(), producto.getNombre(),
                producto.getDescripcion(), producto.getPrecioCompra(), producto.getPrecioVenta(),
                producto.getStock(), producto.getStockMinimo(), producto.getActivo(), producto.getVersion(),
                toCategoria(producto.getCategoria()), toProveedor(producto.getProveedor()),
                EstadoStock.calcular(producto.getStock(), producto.getStockMinimo()));
    }

    public static MovimientoResponse toMovimiento(Movimiento movimiento) {
        if (movimiento == null) {
            return null;
        }
        return new MovimientoResponse(movimiento.getId(), toProducto(movimiento.getProducto()),
                movimiento.getTipo(), movimiento.getCantidad(), movimiento.getFecha(), movimiento.getObservaciones(),
                movimiento.getUsuario() == null ? null : new UsuarioMovimientoResponse(
                        movimiento.getUsuario().getId(), movimiento.getUsuario().getUsername()));
    }
}
