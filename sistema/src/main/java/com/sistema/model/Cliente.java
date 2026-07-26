package com.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString(exclude = {"ventas", "presupuestos"})
public class Cliente extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Venta> ventas;
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Presupuesto> presupuestos;
    String nombre;
    String apellido;
    String telefono;
    String dni;
    String email;
    String direccion;
    @Enumerated(EnumType.STRING)
    private CondicionIva condicionIva;
}
