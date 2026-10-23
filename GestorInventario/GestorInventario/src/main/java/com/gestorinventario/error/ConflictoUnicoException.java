package com.gestorinventario.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ConflictoUnicoException extends ResponseStatusException {
    private final CampoUnico campo;

    public ConflictoUnicoException(CampoUnico campo, Throwable causa) {
        super(HttpStatus.CONFLICT, campo.mensaje(), causa);
        this.campo = campo;
    }

    public CampoUnico campo() { return campo; }
}
