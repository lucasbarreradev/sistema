package com.sistema.service;

import com.sistema.model.Cliente;
import com.sistema.model.Producto;
import com.sistema.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepo;

    public ClienteService(ClienteRepository clienteRepo) {
        this.clienteRepo = clienteRepo;
    }

    public List<Cliente> getClientes() {
        return clienteRepo.findAllByOrderByNombreAsc();
    }

    public Optional<Cliente> getClienteById(Long id) {
        return clienteRepo.findById(id);
    }

    public Cliente saveCliente(Cliente cliente) {
        if (cliente.getNombre() == null || cliente.getNombre().isBlank()) {
            throw new IllegalArgumentException("Ingrese el nombre del cliente");
        }
        cliente.setNombre(cliente.getNombre().trim());
        cliente.setApellido(limpiar(cliente.getApellido()));
        cliente.setTelefono(limpiar(cliente.getTelefono()));
        cliente.setDni(limpiar(cliente.getDni()));
        cliente.setEmail(limpiar(cliente.getEmail()));
        cliente.setDireccion(limpiar(cliente.getDireccion()));
        if (cliente.getCondicionIva() == null) {
            cliente.setCondicionIva(com.sistema.model.CondicionIva.CONSUMIDOR_FINAL);
        }
        return clienteRepo.save(cliente);
    }

    public Cliente updateCliente(Long id, Cliente cliente) {
        Cliente existente = clienteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Cliente no encontrado con id: " + id));

        existente.setNombre(cliente.getNombre());
        existente.setApellido(cliente.getApellido());
        existente.setTelefono(cliente.getTelefono());
        existente.setDni(cliente.getDni());
        existente.setEmail(cliente.getEmail());
        existente.setDireccion(cliente.getDireccion());
        existente.setCondicionIva(cliente.getCondicionIva());
        // setear SOLO lo que se permite modificar

        return clienteRepo.save(existente);
    }

    public void deleteCliente(Long id) {
        if (!clienteRepo.existsById(id)) {
            throw new RuntimeException("Cliente no existe");
        }
        clienteRepo.deleteById(id);
    }

    public List<Cliente> buscar(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        return clienteRepo.buscar(query.trim())
                .stream()
                .limit(10)
                .toList();
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

}

