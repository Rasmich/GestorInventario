package com.gestorinventario.model;

/** Estado calculado al consultar; no se persiste en MySQL. */
public enum EstadoStock {
    STOCK_CORRECTO, STOCK_BAJO, SIN_STOCK;

    public static EstadoStock calcular(int stock, int stockMinimo) {
        if (stock < 0 || stockMinimo < 0) {
            throw new IllegalArgumentException("El stock y el minimo no pueden ser negativos");
        }
        if (stock == 0) {
            return SIN_STOCK;
        }
        return stock <= stockMinimo ? STOCK_BAJO : STOCK_CORRECTO;
    }
}
