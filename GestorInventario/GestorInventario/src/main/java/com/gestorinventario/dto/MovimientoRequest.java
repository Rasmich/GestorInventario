package com.gestorinventario.dto;

import com.gestorinventario.model.TipoMovimiento;
import jakarta.validation.constraints.Size;

public class MovimientoRequest {

    private Long productoId;
    private TipoMovimiento tipo;
    private Integer cantidad;
    @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres.")
    private String observaciones;

    public MovimientoRequest() {
    }

    public Long getProductoId() {
        return productoId;
    }

    public void setProductoId(Long productoId) {
        this.productoId = productoId;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public void setTipo(TipoMovimiento tipo) {
        this.tipo = tipo;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
