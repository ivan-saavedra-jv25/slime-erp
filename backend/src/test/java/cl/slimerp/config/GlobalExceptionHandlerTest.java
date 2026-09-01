package cl.slimerp.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accessDeniedDevuelve403ConMensajeGenerico() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleAccessDenied(new AccessDeniedException("no importa"));

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
        assertEquals("No tiene permisos para realizar esta acción", respuesta.getBody().get("error"));
    }

    @Test
    void badCredentialsDevuelve401ConElMensajeOriginal() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleBadCredentials(new BadCredentialsException("Credenciales inválidas"));

        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        assertEquals("Credenciales inválidas", respuesta.getBody().get("error"));
    }
}
