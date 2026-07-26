package com.sistema.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CifradoCredencialesServiceTest {
    @Test
    void cifraYDescifraSinGuardarElTokenEnTextoPlano() {
        String clave = Base64.getEncoder().encodeToString(new byte[32]);
        CifradoCredencialesService service = new CifradoCredencialesService(clave);

        String cifrado = service.cifrar("APP_USR-token-secreto");

        assertNotEquals("APP_USR-token-secreto", cifrado);
        assertFalse(cifrado.contains("token-secreto"));
        assertEquals("APP_USR-token-secreto", service.descifrar(cifrado));
    }

    @Test
    void rechazaUnaClaveQueNoTieneTreintaYDosBytes() {
        CifradoCredencialesService service = new CifradoCredencialesService(Base64.getEncoder().encodeToString(new byte[16]));
        assertFalse(service.configurado());
        assertThrows(IllegalStateException.class, () -> service.cifrar("token"));
    }
}
