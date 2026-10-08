package com.gestorinventario.controller;

import java.sql.SQLException;
import java.util.List;
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

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> manejarBloqueo(PessimisticLockingFailureException exception) {
        return respuesta(HttpStatus.CONFLICT, "Conflicto de concurrencia. Reintenta la operacion.");
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> manejarNegocio(ResponseStatusException exception) {
        ProblemDetail error = ProblemDetail.forStatusAndDetail(exception.getStatusCode(),
                exception.getReason() == null ? "No se puede realizar la operacion" : exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> manejarValidacion(MethodArgumentNotValidException exception) {
        ProblemDetail error = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos");
        List<String> errores = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage()).toList();
        error.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ProblemDetail> manejarEntradaInvalida(Exception exception) {
        return respuesta(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos: " + exception.getMessage());
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
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                if (sql.getErrorCode() == 1062) {
                    return respuesta(HttpStatus.CONFLICT, "Ya existe un registro con esos datos unicos");
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

    private ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(ProblemDetail.forStatusAndDetail(estado, mensaje));
    }
}
