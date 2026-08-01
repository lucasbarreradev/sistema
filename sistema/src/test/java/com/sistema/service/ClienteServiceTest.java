package com.sistema.service;

import com.sistema.model.Cliente;
import com.sistema.model.CondicionIva;
import com.sistema.repository.ClienteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClienteServiceTest {

    @Test
    void altaRapidaNormalizaDatosYAsignaConsumidorFinal() {
        ClienteRepository repository = mock(ClienteRepository.class);
        when(repository.save(any(Cliente.class))).thenAnswer(invocacion -> {
            Cliente cliente = invocacion.getArgument(0);
            cliente.setId(15L);
            return cliente;
        });
        ClienteService service = new ClienteService(repository);
        Cliente cliente = new Cliente();
        cliente.setNombre("  Lucas  ");
        cliente.setApellido("  Pérez  ");

        Cliente guardado = service.saveCliente(cliente);

        assertEquals(15L, guardado.getId());
        assertEquals("Lucas", guardado.getNombre());
        assertEquals("Pérez", guardado.getApellido());
        assertEquals(CondicionIva.CONSUMIDOR_FINAL, guardado.getCondicionIva());
    }

    @Test
    void noPermiteCrearClienteSinNombre() {
        ClienteRepository repository = mock(ClienteRepository.class);
        ClienteService service = new ClienteService(repository);
        Cliente cliente = new Cliente();
        cliente.setNombre("   ");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.saveCliente(cliente));

        assertEquals("Ingrese el nombre del cliente", error.getMessage());
        verify(repository, never()).save(any());
    }
}
