package com.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.stream.Stream;

@Entity
@Table(name = "configuracion_documento",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_configuracion_documento_tenant", columnNames = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionDocumento extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String nombreEmpresa;
    @Column(length = 150)
    private String nombreContacto;
    @Column(length = 40)
    private String cuit;
    @Column(length = 250)
    private String direccion;
    @Column(length = 20)
    private String codigoPostal;
    @Column(length = 120)
    private String localidad;
    @Column(length = 120)
    private String provincia;
    @Column(length = 120)
    private String pais;
    @Column(length = 100)
    private String telefono;
    @Column(length = 180)
    private String email;

    private String logoNombre;
    private String logoTipoContenido;
    @Lob
    @Column(columnDefinition = "MEDIUMBLOB")
    @JsonIgnore
    private byte[] logoContenido;

    public boolean tieneLogo() {
        return logoContenido != null && logoContenido.length > 0;
    }

    public String ubicacionCompleta() {
        return unir(codigoPostal, localidad, provincia, pais);
    }

    public String direccionCompleta() {
        return unir(direccion, ubicacionCompleta());
    }

    private String unir(String... valores) {
        return String.join(", ", Stream.of(valores)
                .filter(valor -> valor != null && !valor.isBlank())
                .map(String::trim)
                .toList());
    }
}
