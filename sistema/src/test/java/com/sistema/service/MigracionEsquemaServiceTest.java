package com.sistema.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MigracionEsquemaServiceTest {

    @Test
    void convierteElEnumAntiguoAUnCampoDeTexto() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("enum('PUBLICADO','ERROR')"));

        new MigracionEsquemaService(jdbc).run(mock(ApplicationArguments.class));

        verify(jdbc).execute("ALTER TABLE publicacion_canal MODIFY COLUMN estado VARCHAR(30) NOT NULL");
    }

    @Test
    void noVuelveAEjecutarLaMigracionSiYaEsVarchar() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("varchar(30)"));
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any()))
                .thenReturn(List.of("varchar(30)"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);

        new MigracionEsquemaService(jdbc).run(mock(ApplicationArguments.class));

        verify(jdbc, never()).execute("ALTER TABLE publicacion_canal MODIFY COLUMN estado VARCHAR(30) NOT NULL");
    }

    @Test
    void convierteElTipoDeTrabajoEnumParaAceptarNuevosFlujos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("varchar(30)"));
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any()))
                .thenAnswer(invocacion -> {
                    String tabla = invocacion.getArgument(2);
                    String columna = invocacion.getArgument(3);
                    if ("trabajo_sincronizacion".equals(tabla)
                            && "tipo_trabajo".equals(columna)) {
                        return List.of("enum('SINCRONIZACION_CANALES','PUBLICACION_SELECCIONADA')");
                    }
                    return List.of("varchar(30)");
                });
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);

        new MigracionEsquemaService(jdbc).run(mock(ApplicationArguments.class));

        verify(jdbc).execute("""
                ALTER TABLE trabajo_sincronizacion
                MODIFY COLUMN tipo_trabajo VARCHAR(40) NULL
                """);
    }
}
