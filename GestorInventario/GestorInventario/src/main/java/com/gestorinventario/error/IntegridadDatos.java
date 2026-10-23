package com.gestorinventario.error;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

public final class IntegridadDatos {
    private IntegridadDatos() {}

    public static boolean esDuplicado(SQLException sql) {
        // 23000 por sí solo incluye también FK y NOT NULL: no basta para identificar UNIQUE.
        return sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState());
    }

    public static List<Throwable> causas(Throwable error) {
        var resultado = new ArrayList<Throwable>();
        var vistos = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var pendientes = new ArrayDeque<Throwable>();
        pendientes.add(error);
        while (!pendientes.isEmpty()) {
            Throwable causa = pendientes.removeFirst();
            if (!vistos.add(causa)) continue;
            resultado.add(causa);
            if (causa.getCause() != null) pendientes.add(causa.getCause());
            if (causa instanceof SQLException sql && sql.getNextException() != null)
                pendientes.add(sql.getNextException());
        }
        return resultado;
    }

    /** Solo para escrituras cuyo único campo UNIQUE editable es el indicado.
     * El id debe ser generado o cargado por la ruta. La escritura debe incluir flush
     * para que el conflicto se produzca dentro de este contexto, antes del commit.
     */
    public static <T> T guardar(CampoUnico campo, Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (DataIntegrityViolationException error) {
            if (causas(error).stream().anyMatch(c -> c instanceof SQLException sql && esDuplicado(sql)))
                throw new ConflictoUnicoException(campo, error);
            throw error;
        }
    }
}
