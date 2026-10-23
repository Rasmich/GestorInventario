package com.gestorinventario.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.FieldError;
import com.gestorinventario.error.CampoUnico;
import com.gestorinventario.error.ConflictoUnicoException;
import com.gestorinventario.error.IntegridadDatos;
import com.gestorinventario.error.ValidacionCamposException;
import static com.gestorinventario.error.IntegridadDatos.esDuplicado;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Map<String, String> CAMPOS = Map.ofEntries(
            Map.entry("codigo", "Código del producto"), Map.entry("nombre", "Nombre"),
            Map.entry("descripcion", "Descripción"), Map.entry("precioCompra", "Precio de compra"),
            Map.entry("precioVenta", "Precio de venta"), Map.entry("stockMinimo", "Stock mínimo"),
            Map.entry("categoriaId", "Categoría"), Map.entry("proveedorId", "Proveedor"),
            Map.entry("productoId", "Producto"), Map.entry("tipo", "Tipo de movimiento"),
            Map.entry("cantidad", "Cantidad"), Map.entry("observaciones", "Observaciones"),
            Map.entry("cif", "CIF"), Map.entry("telefono", "Teléfono"),
            Map.entry("email", "Correo electrónico"), Map.entry("direccion", "Dirección"),
            Map.entry("username", "Nombre de usuario"), Map.entry("password", "Contraseña"),
            Map.entry("rol", "Rol"), Map.entry("activo", "Estado"));
    private static final Pattern CLAVE_DUPLICADA = Pattern.compile("(?i)for key ['`]([^'`]+)['`]");

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> manejarBloqueo(PessimisticLockingFailureException exception) {
        return respuesta(HttpStatus.CONFLICT, "Conflicto de concurrencia. Reintenta la operacion.");
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> manejarNegocio(ResponseStatusException exception) {
        ProblemDetail error = ProblemDetail.forStatusAndDetail(exception.getStatusCode(),
                exception.getReason() == null ? "No se puede realizar la operacion" : exception.getReason());
        if (exception instanceof ConflictoUnicoException duplicado)
            error.setProperty("erroresCampos", Map.of(duplicado.campo().campo(), duplicado.campo().mensaje()));
        return ResponseEntity.status(exception.getStatusCode()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> manejarValidacion(MethodArgumentNotValidException exception) {
        ProblemDetail error = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los campos indicados.");
        List<String> errores = exception.getBindingResult().getFieldErrors().stream()
                .map(this::mensajeCampo).toList();
        error.setProperty("errores", errores);
        Map<String, String> campos = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(campo ->
                campos.merge(campo.getField(), mensajeCampo(campo), (a, b) -> a + " · " + b));
        error.setProperty("erroresCampos", campos);
        return ResponseEntity.badRequest().body(error);
    }

    private String mensajeCampo(FieldError campo) {
        String mensaje = switch (campo.getCode() == null ? "" : campo.getCode()) {
            case "NotBlank", "NotNull", "NotEmpty" -> "Este campo es obligatorio.";
            case "Email" -> "Introduce un correo electrónico válido.";
            case "Positive" -> "Introduce un número mayor que cero.";
            case "PositiveOrZero" -> "Introduce un número mayor o igual que cero.";
            case "DecimalMin" -> "El valor debe ser mayor o igual que " + atributo(campo, "value") + ".";
            case "Digits" -> "Introduce un número con un máximo de " + atributo(campo, "integer")
                    + " cifras enteras y " + atributo(campo, "fraction") + " decimales.";
            case "Size" -> Integer.valueOf(0).equals(atributo(campo, "min"))
                    ? "No puede superar los " + atributo(campo, "max") + " caracteres."
                    : "La longitud debe estar entre " + atributo(campo, "min")
                    + " y " + atributo(campo, "max") + " caracteres.";
            case "Pattern" -> campo.getField().equals("username")
                    ? "El nombre de usuario no puede contener espacios." : "Revisa el formato de este campo.";
            default -> "Revisa el valor de este campo.";
        };
        return CAMPOS.getOrDefault(campo.getField(), "Campo") + ": " + mensaje;
    }

    private Object atributo(FieldError campo, String nombre) {
        return campo.unwrap(jakarta.validation.ConstraintViolation.class)
                .getConstraintDescriptor().getAttributes().get(nombre);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ProblemDetail> manejarEntradaInvalida(Exception exception) {
        var respuesta = respuesta(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos: " + exception.getMessage());
        if (exception instanceof ValidacionCamposException validacion)
            respuesta.getBody().setProperty("erroresCampos", validacion.campos());
        return respuesta;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> manejarFormatoInvalido(Exception exception) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo JSON o los parametros tienen un formato invalido");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> manejarConcurrencia(Exception exception) {
        return respuesta(HttpStatus.CONFLICT,
                "El producto ha sido modificado por otra operacion. Recarga los datos y vuelve a intentarlo.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarIntegridad(DataIntegrityViolationException exception) {
        // No exponemos mensajes SQL ni nombres internos de tablas al cliente.
        for (Throwable causa : IntegridadDatos.causas(exception)) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException restriccion
                    && restriccion.getSQLException() != null
                    && esDuplicado(restriccion.getSQLException())) {
                CampoUnico campo = CampoUnico.porRestriccion(restriccion.getConstraintName());
                if (campo != null) return respuestaDuplicado(campo);
            }
        }
        for (Throwable causa : IntegridadDatos.causas(exception)) {
            if (causa instanceof SQLException sql) {
                if (esDuplicado(sql)) {
                    var clave = CLAVE_DUPLICADA.matcher(sql.getMessage() == null ? "" : sql.getMessage());
                    CampoUnico campo = clave.find() ? CampoUnico.porRestriccion(clave.group(1)) : null;
                    return campo != null ? respuestaDuplicado(campo) : respuesta(HttpStatus.CONFLICT,
                            "Ya existe un registro con esos datos. Revisa los campos que deben ser únicos.");
                }
                if (sql.getErrorCode() == 1451 || sql.getErrorCode() == 1452) {
                    return respuesta(HttpStatus.CONFLICT, "La operacion entra en conflicto con relaciones existentes");
                }
                if (sql.getErrorCode() == 1048 || sql.getErrorCode() == 1364
                        || sql.getErrorCode() == 1406 || sql.getErrorCode() == 1264
                        || sql.getErrorCode() == 3819) {
                    return respuesta(HttpStatus.BAD_REQUEST, "Los datos incumplen las restricciones de almacenamiento");
                }
            }
            if (causa instanceof org.hibernate.PropertyValueException) {
                return respuesta(HttpStatus.BAD_REQUEST, "Faltan datos obligatorios o una relacion valida");
            }
        }
        return respuesta(HttpStatus.CONFLICT, "La operacion incumple una restriccion de integridad");
    }

    private ResponseEntity<ProblemDetail> respuestaDuplicado(CampoUnico campo) {
        return manejarNegocio(new ConflictoUnicoException(campo, null));
    }

    private ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(ProblemDetail.forStatusAndDetail(estado, mensaje));
    }
}
