package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MercadoLibreOpcionesEnvioServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void habilitaLasOpcionesPermitidasDelModoMe2() throws Exception {
        var respuesta = JSON.readTree("""
                {
                  "channels": [{
                    "channel_id": "marketplace",
                    "modes": [{
                      "mode": "me2",
                      "shipping_attributes": {
                        "free_shipping": "optional",
                        "local_pick_up": "optional"
                      }
                    }]
                  }]
                }
                """);

        var opciones = MercadoLibreOpcionesEnvioService
                .analizarRespuesta(respuesta);

        assertEquals("me2", opciones.modo());
        assertEquals(true, opciones.envioGratis());
        assertEquals(true, opciones.retiroPersonal());
    }

    @Test
    void noMarcaUnaOpcionQueMercadoLibreInformaComoNoPermitida()
            throws Exception {
        var respuesta = JSON.readTree("""
                {
                  "modes": [{
                    "mode": "me2",
                    "logistic_types": [{
                      "type": "drop_off",
                      "attributes": {
                        "free_shipping": "not_allowed",
                        "local_pick_up": "optional"
                      }
                    }]
                  }]
                }
                """);

        var opciones = MercadoLibreOpcionesEnvioService
                .analizarRespuesta(respuesta);

        assertEquals("me2", opciones.modo());
        assertEquals(false, opciones.envioGratis());
        assertEquals(true, opciones.retiroPersonal());
    }
}
